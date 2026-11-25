package com.dnsfilter.dns

/**
 * A parsed DNS question. Only the pieces needed to build a reply are kept: the
 * id, the flags, the asked name and the raw question bytes.
 */
internal class DnsQuery(
    val id: Int,
    val flags: Int,
    val host: String,
    val type: Int,
    val question: ByteArray,
)

internal object DnsMessage {

    const val TYPE_AAAA = 28
    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    /** Returns null for anything that is not a well-formed IPv4 DNS question. */
    fun parse(buffer: ByteArray, offset: Int, length: Int): DnsQuery? {
        if (length < 12) return null
        if (offset < 0 || offset + length > buffer.size) return null

        val id = u16(buffer, offset)
        val flags = u16(buffer, offset + 2)
        if (flags and 0x8000 != 0) return null // already a response

        val questions = u16(buffer, offset + 4)
        if (questions < 1) return null

        val end = offset + length
        var cursor = offset + 12
        val name = StringBuilder()
        var labels = 0

        while (cursor < end) {
            val labelLength = buffer[cursor].toInt() and 0xFF
            if (labelLength == 0) {
                cursor += 1
                break
            }
            if (labelLength and 0xC0 == 0xC0) {
                cursor += 2
                break
            }
            cursor += 1
            if (cursor + labelLength > end) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(buffer, cursor, labelLength, Charsets.US_ASCII))
            cursor += labelLength
            labels += 1
            if (labels > 127) return null
        }

        if (cursor + 4 > end) return null
        val type = u16(buffer, cursor)
        val questionEnd = cursor + 4
        val question = buffer.copyOfRange(offset + 12, questionEnd)
        return DnsQuery(id, flags, name.toString(), type, question)
    }

    /** Builds a reply with the given result code and no answers. */
    fun buildResponse(query: DnsQuery, rcode: Int): ByteArray {
        val out = ByteArray(12 + query.question.size)
        writeU16(out, 0, query.id)
        val flags = 0x8000 or (query.flags and 0x0100) or 0x0080 or (rcode and 0x0F)
        writeU16(out, 2, flags)
        writeU16(out, 4, 1) // one question
        writeU16(out, 6, 0) // no answers
        writeU16(out, 8, 0)
        writeU16(out, 10, 0)
        query.question.copyInto(out, 12)
        return out
    }
}

internal fun u16(buffer: ByteArray, offset: Int): Int =
    ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

internal fun writeU16(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = ((value ushr 8) and 0xFF).toByte()
    buffer[offset + 1] = (value and 0xFF).toByte()
}
