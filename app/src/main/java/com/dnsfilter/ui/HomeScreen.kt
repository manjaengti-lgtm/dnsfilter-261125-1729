package com.dnsfilter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dnsfilter.model.LogEntry
import com.dnsfilter.model.TARGET_PACKAGE
import com.dnsfilter.model.TunnelStatus
import com.dnsfilter.viewmodel.HomeUiState
import com.dnsfilter.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onStartRequested: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshChecks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DNS Filter",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state.status)

            ButtonRow(
                state = state,
                onStartRequested = onStartRequested,
                onStopRequested = viewModel::stopNow,
            )

            state.error?.let { message ->
                BannerCard(
                    icon = Icons.Filled.Warning,
                    title = "Problem",
                    body = message,
                    container = MaterialTheme.colorScheme.errorContainer,
                    onContainer = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (!state.targetInstalled) {
                BannerCard(
                    icon = Icons.Filled.Info,
                    title = "Target app missing",
                    body = "This filter only covers $TARGET_PACKAGE (Castle). Install that app, " +
                        "then start filtering.",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Counters(state)

            Reminders(state)

            LogCard(
                entries = state.log,
                onClear = viewModel::clearLog,
            )
        }
    }
}

@Composable
private fun StatusCard(status: TunnelStatus) {
    val scheme = MaterialTheme.colorScheme
    val (label, detail, icon, container, onContainer) = when (status) {
        TunnelStatus.STOPPED -> StatusLook(
            "Stopped",
            "No queries are being filtered right now.",
            Icons.Filled.Security,
            scheme.surfaceVariant,
            scheme.onSurfaceVariant,
        )
        TunnelStatus.STARTING -> StatusLook(
            "Starting…",
            "Loading the block list. This takes a second or two.",
            Icons.Filled.Security,
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
        TunnelStatus.ACTIVE -> StatusLook(
            "Active",
            "Ad domains used by Castle are answered locally with NXDOMAIN.",
            Icons.Filled.CheckCircle,
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
        )
        TunnelStatus.ERROR -> StatusLook(
            "Error",
            "Filtering is not running.",
            Icons.Filled.Warning,
            scheme.errorContainer,
            scheme.onErrorContainer,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
            }
        }
    }
}

private data class StatusLook(
    val label: String,
    val detail: String,
    val icon: ImageVector,
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun ButtonRow(
    state: HomeUiState,
    onStartRequested: () -> Unit,
    onStopRequested: () -> Unit,
) {
    val running = state.status == TunnelStatus.ACTIVE || state.status == TunnelStatus.STARTING
    if (running) {
        OutlinedButton(
            onClick = onStopRequested,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Stop filtering", fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onStartRequested,
            enabled = state.targetInstalled,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Start filtering", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Counters(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CounterCard(
            modifier = Modifier.weight(1f),
            value = state.blocked,
            label = "Blocked",
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        CounterCard(
            modifier = Modifier.weight(1f),
            value = state.allowed,
            label = "Allowed",
            container = MaterialTheme.colorScheme.surfaceVariant,
            onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CounterCard(
    modifier: Modifier,
    value: Int,
    label: String,
    container: Color,
    onContainer: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = onContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
        }
    }
}

@Composable
private fun Reminders(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Before you rely on this",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ReminderRow(
                title = "Private DNS",
                body = when (state.privateDnsMode.lowercase(Locale.US)) {
                    "hostname" -> "Strict mode bypasses this filter. Set Private DNS to Off " +
                        "or Automatic" +
                        if (state.privateDnsHostname.isNotBlank()) {
                            " (now: ${state.privateDnsHostname})."
                        } else {
                            "."
                        }
                    "opportunistic" -> "Automatic is fine, but a strict hostname would " +
                        "bypass the filter."
                    else -> "Off — every DNS query goes through this filter. Good."
                },
                ok = state.privateDnsMode.lowercase(Locale.US) != "hostname",
            )

            ReminderRow(
                title = "Other VPNs",
                body = if (state.vpnNetworkPresent && state.status != TunnelStatus.ACTIVE) {
                    "Another VPN connection is up. Only one VPN can run at a time — turn " +
                        "the other one off first."
                } else {
                    "Only one VPN can be active at a time. Starting this replaces any other VPN."
                },
                ok = !(state.vpnNetworkPresent && state.status != TunnelStatus.ACTIVE),
            )

            ReminderRow(
                title = "VIP is server-side",
                body = "Blocking ads does not unlock VIP. Their server decides entitlement, " +
                    "so premium streams stay locked.",
                ok = true,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Domain blocked by mistake? Add it to assets/allow.txt and rebuild. " +
                    "The whole branch is then never blocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderRow(title: String, body: String, ok: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) scheme.secondary else scheme.error,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogCard(entries: List<LogEntry>, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Query log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "Nothing yet. Start filtering and open Castle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                Text(
                    text = "Newest first, last 60 kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.format(Date(entry.time)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.host,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (entry.blocked) "Blocked" else "Allowed",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.blocked) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BannerCard(
    icon: ImageVector,
    title: String,
    body: String,
    container: Color,
    onContainer: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
    }
}
