package com.dnsfilter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dnsfilter.ui.HomeScreen
import com.dnsfilter.ui.theme.DnsFilterTheme
import com.dnsfilter.viewmodel.MainViewModel

/**
 * Single screen host. Start goes through VpnService.prepare() first, so the
 * system consent dialog is shown before the service ever runs, and a refusal is
 * reported instead of starting a dead tunnel.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startNow()
        } else {
            viewModel.consentDenied()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            viewModel.showError(
                "Notifications are blocked, so the ongoing status notification stays hidden. " +
                    "Filtering still works.",
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForNotificationPermission()

        setContent {
            DnsFilterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(
                        viewModel = viewModel,
                        onStartRequested = ::startWithConsent,
                    )
                }
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Shows the system VPN consent dialog, or starts straight away if already granted. */
    private fun startWithConsent() {
        val prepareIntent = try {
            VpnService.prepare(this)
        } catch (t: Throwable) {
            viewModel.showError(t.message ?: "Android could not prepare the VPN.")
            return
        }
        if (prepareIntent == null) {
            viewModel.startNow()
            return
        }
        try {
            vpnConsentLauncher.launch(prepareIntent)
        } catch (t: Throwable) {
            viewModel.showError("The VPN permission dialog could not be opened.")
        }
    }
}
