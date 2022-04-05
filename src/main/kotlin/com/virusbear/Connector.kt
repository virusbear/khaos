package com.virusbear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class Connector: Closeable {

    companion object {
        fun getByDefinition(definition: String): Connector {
            val parts = definition.split(" ")
            if(parts.size != 3) {
                throw ConnectorParsingException("Invalid connector syntax")
            }

            val protocol = parts[2]

            val srcPort = parts[0].substringAfter("/").toIntOrNull() ?: throw ConnectorParsingException("Invalid connector syntax")
            val destPort = parts[1].substringAfter("/").toIntOrNull() ?: throw ConnectorParsingException("Invalid connector syntax")
            val srcAddr: InetAddress?
            val destAddr: InetAddress?
            try {
                srcAddr = InetAddress.getByName(parts[0].substringBefore("/"))
                if(NetworkInterface.getByInetAddress(srcAddr) == null && !srcAddr.isAnyLocalAddress && !srcAddr.isLoopbackAddress) {
                    throw ConnectorParsingException("Source Address not local network interface")
                }
                destAddr = InetAddress.getByName(parts[1].substringBefore("/"))
            } catch(ex: UnknownHostException) {
                throw ConnectorParsingException("Invalid address syntax")
            }

            val src = InetSocketAddress(srcAddr, srcPort)
            val dest = InetSocketAddress(destAddr, destPort)

            return when(protocol.lowercase()) {
                "tcp" -> TcpConnector(src, dest)
                "udp" -> TODO("UdpConnector(src, dest)")
                else -> throw ConnectorParsingException("Invalid protocol: $protocol")
            }
        }
    }

    abstract fun CoroutineScope.start(context: CoroutineContext = EmptyCoroutineContext)

    abstract fun join()
}