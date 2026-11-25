package com.dnsfilter.model

/** The one app this filter is scoped to. */
const val TARGET_PACKAGE = "com.journey.indiab"

/** Where the tunnel is in its life cycle. */
enum class TunnelStatus {
    STOPPED,
    STARTING,
    ACTIVE,
    ERROR,
}

/** One line of the query log. */
data class LogEntry(
    val id: Long,
    val host: String,
    val blocked: Boolean,
    val time: Long,
)
