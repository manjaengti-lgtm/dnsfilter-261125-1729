package com.dnsfilter.data

import com.dnsfilter.model.LogEntry
import com.dnsfilter.model.TunnelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for the running filter. The service writes here on a
 * background thread, the screen reads here. Same process, so a plain object is
 * enough.
 */
object FilterState {

    private const val MAX_LOG = 60

    private val idCounter = AtomicLong(0)

    private val _status = MutableStateFlow(TunnelStatus.STOPPED)
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private val _blocked = MutableStateFlow(0)
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    private val _allowed = MutableStateFlow(0)
    val allowed: StateFlow<Int> = _allowed.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Fresh run: clear counters, log and error, move to Starting. */
    fun begin() {
        _blocked.value = 0
        _allowed.value = 0
        _log.value = emptyList()
        _error.value = null
        _status.value = TunnelStatus.STARTING
    }

    fun setStatus(value: TunnelStatus) {
        _status.value = value
    }

    fun setError(message: String?) {
        _error.value = message
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    /** Called for every query. Newest first, capped at 60. */
    fun record(host: String, blocked: Boolean) {
        if (blocked) _blocked.update { it + 1 } else _allowed.update { it + 1 }
        val entry = LogEntry(
            id = idCounter.incrementAndGet(),
            host = host,
            blocked = blocked,
            time = System.currentTimeMillis(),
        )
        _log.update { old -> (listOf(entry) + old).take(MAX_LOG) }
    }
}
