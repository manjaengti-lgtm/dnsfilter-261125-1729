package com.dnsfilter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.dnsfilter.MainActivity
import com.dnsfilter.R
import com.dnsfilter.data.BlockList
import com.dnsfilter.data.FilterState
import com.dnsfilter.dns.DnsMessage
import com.dnsfilter.dns.DnsQuery
import com.dnsfilter.dns.Ipv4
import com.dnsfilter.dns.u16
import com.dnsfilter.model.TARGET_PACKAGE
import com.dnsfilter.model.TunnelStatus
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The tunnel. Only 10.0.0.2/32 is routed, and only the target app is allowed
 * in, so Castle's DNS queries are the entire traffic surface. Blocked names get
 * NXDOMAIN (or NOERROR with no answers for AAAA), everything else is forwarded
 * to a real resolver through a protected socket.
 */
class DnsFilterService : VpnService() {

    @Volatile
    private var running = false

    @Volatile
    private var foreground = false

    @Volatile
    private var lastBlockedShown = -1

    private var tunnel: ParcelFileDescriptor? = null
    private var readThread: Thread? = null
    private var setupThread: Thread? = null
    private var forwardPool: ExecutorService? = null

    private lateinit var blockList: BlockList

    private val writeLock = Any()
    private val packetId = AtomicInteger(1)
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            updateNotification()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (running) {
                stopFiltering()
            } else {
                // Nothing running: a plain stopSelf() is enough, no
                // startForeground() call is needed afterwards.
                handler.removeCallbacks(ticker)
                teardown()
                FilterState.setStatus(TunnelStatus.STOPPED)
                stopForegroundCompat()
                stopSelf()
            }
        } else {
            startFiltering()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // Another VPN was started, or the user cleared it in system settings.
        running = false
        FilterState.setError("The VPN was revoked by Android. Tap Start to reconnect.")
        FilterState.setStatus(TunnelStatus.ERROR)
        teardown()
        stopForegroundCompat()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        teardown()
        if (FilterState.status.value != TunnelStatus.ERROR) {
            FilterState.setStatus(TunnelStatus.STOPPED)
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- start

    private fun startFiltering() {
        if (running) return
        running = true
        FilterState.begin()
        startForegroundCompat()
        forwardPool = Executors.newFixedThreadPool(4)
        setupThread = Thread { setUpTunnel() }.apply {
            name = "dnsfilter-setup"
            start()
        }
        handler.postDelayed(ticker, 1_000L)
    }

    private fun setUpTunnel() {
        try {
            if (!isTargetInstalled()) {
                fail("Castle ($TARGET_PACKAGE) is not installed, so there is nothing to filter.")
                return
            }

            blockList = BlockList.load(this)

            val builder = Builder()
                .setSession("DNS Filter")
                .setMtu(1500)
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(TUN_ADDRESS)
                .addRoute(TUN_ADDRESS, 32)
                .setBlocking(true)

            try {
                builder.addAllowedApplication(TARGET_PACKAGE)
            } catch (e: PackageManager.NameNotFoundException) {
                fail("Castle ($TARGET_PACKAGE) is not installed, so there is nothing to filter.")
                return
            }

            val descriptor = builder.establish()
            if (descriptor == null) {
                fail("Android refused to create the tunnel. Close any other VPN and try again.")
                return
            }
            if (!running) {
                try {
                    descriptor.close()
                } catch (ignored: IOException) {
                }
                return
            }

            tunnel = descriptor
            FilterState.setStatus(TunnelStatus.ACTIVE)
            updateNotification()

            readThread = Thread { readLoop(descriptor) }.apply {
                name = "dnsfilter-read"
                start()
            }
        } catch (t: Throwable) {
            fail(t.message ?: "Could not start the filter.")
        }
    }

    private fun isTargetInstalled(): Boolean = try {
        packageManager.getPackageInfo(TARGET_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun fail(message: String) {
        running = false
        handler.removeCallbacks(ticker)
        FilterState.setError(message)
        FilterState.setStatus(TunnelStatus.ERROR)
        teardown()
        stopForegroundCompat()
        stopSelf()
    }

    private fun teardown() {
        val descriptor = tunnel
        tunnel = null
        try {
            descriptor?.close()
        } catch (ignored: IOException) {
        }
        val reader = readThread
        readThread = null
        if (reader != null && reader != Thread.currentThread()) {
            try {
                reader.join(400L)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        forwardPool?.shutdownNow()
        forwardPool = null
    }

    private fun stopFiltering() {
        running = false
        handler.removeCallbacks(ticker)
        teardown()
        FilterState.setStatus(TunnelStatus.STOPPED)
        stopForegroundCompat()
        stopSelf()
    }

    // ------------------------------------------------------------ read loop

    private fun readLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32_767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                break
            }
            if (length <= 0) continue
            try {
                handlePacket(buffer, length, output)
            } catch (t: Throwable) {
                // one bad packet must never kill the tunnel
            }
        }

        try {
            input.close()
        } catch (ignored: IOException) {
        }
        try {
            output.close()
        } catch (ignored: IOException) {
        }
    }

    private fun handlePacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 28) return
        if ((buffer[0].toInt() and 0xF0) != 0x40) return // IPv6 is not routed

        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength + 8) return
        if ((buffer[9].toInt() and 0xFF) != 17) return // UDP only

        val udpLength = u16(buffer, headerLength + 4)
        if (udpLength < 8) return
        val dnsLength = minOf(udpLength - 8, length - headerLength - 8)
        if (dnsLength < 12) return

        val query = DnsMessage.parse(buffer, headerLength + 8, dnsLength) ?: return
        val sourceIp = buffer.copyOfRange(12, 16)
        val destinationIp = buffer.copyOfRange(16, 20)
        val sourcePort = u16(buffer, headerLength)
        val destinationPort = u16(buffer, headerLength + 2)

        val host = query.host
        if (host.isEmpty()) return

        val blocked = blockList.isBlocked(host)
        FilterState.record(host, blocked)

        if (blocked) {
            val rcode = if (query.type == DnsMessage.TYPE_AAAA) {
                DnsMessage.RCODE_NOERROR
            } else {
                DnsMessage.RCODE_NXDOMAIN
            }
            writeToTunnel(
                output,
                destinationIp,
                sourceIp,
                destinationPort,
                sourcePort,
                DnsMessage.buildResponse(query, rcode),
            )
        } else {
            val payload = buffer.copyOfRange(headerLength + 8, headerLength + 8 + dnsLength)
            val pool = forwardPool
            if (pool == null || pool.isShutdown) return
            pool.execute {
                forward(output, destinationIp, sourceIp, destinationPort, sourcePort, payload, query)
            }
        }
    }

    private fun forward(
        output: FileOutputStream,
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
        query: DnsQuery,
    ) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = TIMEOUT_MS
            val upstream = InetAddress.getByAddress(UPSTREAM_ADDRESS)
            socket.send(DatagramPacket(payload, payload.size, upstream, DNS_PORT))

            val responseBuffer = ByteArray(4096)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(response)

            val data = responseBuffer.copyOfRange(0, response.length)
            writeToTunnel(output, sourceIp, destinationIp, sourcePort, destinationPort, data)
        } catch (t: Throwable) {
            try {
                writeToTunnel(
                    output,
                    sourceIp,
                    destinationIp,
                    sourcePort,
                    destinationPort,
                    DnsMessage.buildResponse(query, DnsMessage.RCODE_SERVFAIL),
                )
            } catch (ignored: Throwable) {
            }
        } finally {
            try {
                socket?.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun writeToTunnel(
        output: FileOutputStream,
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
    ) {
        val packet = Ipv4.buildUdp(
            sourceIp,
            destinationIp,
            sourcePort,
            destinationPort,
            payload,
            packetId.incrementAndGet(),
        )
        synchronized(writeLock) {
            try {
                output.write(packet)
                output.flush()
            } catch (ignored: IOException) {
            }
        }
    }

    // ---------------------------------------------------------- notification

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Filter status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows that DNS filtering is running and how many queries were blocked."
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DnsFilterService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPending = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = when (FilterState.status.value) {
            TunnelStatus.STARTING -> "Starting…"
            TunnelStatus.ERROR -> FilterState.error.value ?: "Filtering stopped"
            else -> "${FilterState.blocked.value} queries blocked"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DNS Filter active")
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_notification, "Stop", stopPending)
            .build()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                // Older releases do not know the special-use type; the plain
                // two-argument call matches the manifest entry for them.
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
            lastBlockedShown = FilterState.blocked.value
        } catch (t: Throwable) {
            // Foreground start can be refused if notifications are blocked, but
            // the tunnel still runs, so do not fail hard.
            foreground = false
        }
    }

    private fun stopForegroundCompat() {
        if (!foreground) return
        foreground = false
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (ignored: Throwable) {
        }
    }

    private fun updateNotification() {
        if (!foreground) return
        val blocked = FilterState.blocked.value
        if (blocked == lastBlockedShown && FilterState.status.value != TunnelStatus.ERROR) return
        lastBlockedShown = blocked
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        const val ACTION_START = "com.dnsfilter.action.START"
        const val ACTION_STOP = "com.dnsfilter.action.STOP"

        private const val CHANNEL_ID = "dns_filter_status"
        private const val NOTIFICATION_ID = 42
        private const val TUN_ADDRESS = "10.0.0.2"
        private const val DNS_PORT = 53
        private const val TIMEOUT_MS = 3_000
        private val UPSTREAM_ADDRESS = byteArrayOf(8, 8, 8, 8)
    }
}
