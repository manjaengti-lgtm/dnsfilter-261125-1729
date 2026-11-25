package com.dnsfilter.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dnsfilter.data.FilterState
import com.dnsfilter.model.LogEntry
import com.dnsfilter.model.TARGET_PACKAGE
import com.dnsfilter.model.TunnelStatus
import com.dnsfilter.service.DnsFilterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val status: TunnelStatus = TunnelStatus.STOPPED,
    val blocked: Int = 0,
    val allowed: Int = 0,
    val log: List<LogEntry> = emptyList(),
    val error: String? = null,
    val privateDnsMode: String = "off",
    val privateDnsHostname: String = "",
    val vpnNetworkPresent: Boolean = false,
    val targetInstalled: Boolean = true,
)

private data class LocalChecks(
    val targetInstalled: Boolean = true,
    val privateDnsMode: String = "off",
    val privateDnsHostname: String = "",
    val vpnNetworkPresent: Boolean = false,
)

private data class TunnelSnapshot(
    val status: TunnelStatus,
    val blocked: Int,
    val allowed: Int,
    val log: List<LogEntry>,
    val error: String?,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val localChecks = MutableStateFlow(LocalChecks())

    private val tunnel = combine(
        FilterState.status,
        FilterState.blocked,
        FilterState.allowed,
        FilterState.log,
        FilterState.error,
    ) { status, blocked, allowed, log, error ->
        TunnelSnapshot(status, blocked, allowed, log, error)
    }

    val uiState: StateFlow<HomeUiState> = combine(tunnel, localChecks) { snapshot, checks ->
        HomeUiState(
            status = snapshot.status,
            blocked = snapshot.blocked,
            allowed = snapshot.allowed,
            log = snapshot.log,
            error = snapshot.error,
            privateDnsMode = checks.privateDnsMode,
            privateDnsHostname = checks.privateDnsHostname,
            vpnNetworkPresent = checks.vpnNetworkPresent,
            targetInstalled = checks.targetInstalled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HomeUiState())

    fun refreshChecks() {
        val context = getApplication<Application>()
        val installed = isInstalled(context)
        val privateDnsMode = try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        } catch (t: Throwable) {
            null
        } ?: "off"
        val hostname = try {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
        } catch (t: Throwable) {
            null
        } ?: ""
        val alreadyUp = FilterState.status.value == TunnelStatus.ACTIVE ||
            FilterState.status.value == TunnelStatus.STARTING
        localChecks.value = LocalChecks(
            targetInstalled = installed,
            privateDnsMode = privateDnsMode,
            privateDnsHostname = hostname,
            vpnNetworkPresent = hasVpnNetwork(context) && !alreadyUp,
        )
    }

    fun startNow() {
        val context = getApplication<Application>()
        val status = FilterState.status.value
        if (status == TunnelStatus.ACTIVE || status == TunnelStatus.STARTING) return
        if (!isInstalled(context)) {
            showError("Castle ($TARGET_PACKAGE) is not installed, so there is nothing to filter.")
            refreshChecks()
            return
        }
        FilterState.setError(null)
        val intent = Intent(context, DnsFilterService::class.java)
            .setAction(DnsFilterService.ACTION_START)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (t: Throwable) {
            showError(t.message ?: "Android refused to start the filter service.")
            FilterState.setStatus(TunnelStatus.ERROR)
        }
    }

    fun stopNow() {
        val context = getApplication<Application>()
        val intent = Intent(context, DnsFilterService::class.java)
            .setAction(DnsFilterService.ACTION_STOP)
        try {
            // Plain startService: a stop-only run never needs to become foreground,
            // and it always ends in stopSelf().
            context.startService(intent)
        } catch (t: Throwable) {
            FilterState.setStatus(TunnelStatus.STOPPED)
        }
    }

    fun consentDenied() {
        showError("VPN permission was declined. Tap Start filtering and choose OK to use the filter.")
        FilterState.setStatus(TunnelStatus.ERROR)
    }

    fun showError(message: String) {
        FilterState.setError(message)
    }

    fun clearLog() {
        FilterState.clearLog()
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TARGET_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun hasVpnNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            @Suppress("DEPRECATION")
            manager.allNetworks.any { network ->
                manager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (t: Throwable) {
            false
        }
    }
}
