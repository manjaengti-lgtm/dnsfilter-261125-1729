package com.dnsfilter.dns

/** Builds the raw IPv4 + UDP packet the tunnel needs. */
internal object Ipv4 {

    private const val PROTOCOL_UDP = 17

    fun buildUdp(
        source: ByteArray,
        destination: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
        identification: Int,
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        writeU16(packet, 2, totalLength)
        writeU16(packet, 4, identification and 0xFFFF)
        packet[6] = 0x40 // don't fragment
        packet[8] = 64 // ttl
        packet[9] = PROTOCOL_UDP.toByte()
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        writeU16(packet, 10, checksum(packet, 0, 20, 0))

        writeU16(packet, 20, sourcePort)
        writeU16(packet, 22, destinationPort)
        writeU16(packet, 24, udpLength)
        payload.copyInto(packet, 28)

        var pseudo = 0
        var i = 0
        while (i < 4) {
            pseudo += u16(source, i)
            pseudo += u16(destination, i)
            i += 2
        }
        pseudo += PROTOCOL_UDP + udpLength
        val udpChecksum = checksum(packet, 20, udpLength, pseudo)
        writeU16(packet, 26, if (udpChecksum == 0) 0xFFFF else udpChecksum)

        return packet
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int, initial: Int): Int {
        var sum = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
