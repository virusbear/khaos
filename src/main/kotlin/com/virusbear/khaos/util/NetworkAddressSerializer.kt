package com.virusbear.khaos.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.InetSocketAddress

class InetSocketAddressSerializer: KSerializer<InetSocketAddress> {
    override fun deserialize(decoder: Decoder): InetSocketAddress {
        val address = decoder.decodeString()
        val host = address.substringBeforeLast(":")
        val port = address.substringAfterLast(":").toIntOrNull() ?: error("Invalid InetSocketAddress")

        return InetSocketAddress(host, port)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InetSocketAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InetSocketAddress) {
        encoder.encodeString(value.hostString + ":" + value.port)
    }
}