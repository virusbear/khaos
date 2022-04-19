package com.virusbear

import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

class UDPConnection(
    private val connector: UdpConnector,
    private val src: NetworkAddress,
    private val dest: NetworkAddress,
    private val inboundSend: SendChannel<Datagram>,
    private val socket: ConnectedDatagramSocket
) {
    private var job: Job? = null
    private var ttlTimer: ResettableTimer? = null

    private var outboundPacketCount = GlobalMetrixBinder.counter(khaosIdentifier("udp.packets.sent")).get()
    private var inboundPacketCount = GlobalMetrixBinder.counter(khaosIdentifier("udp.packets.received")).get()
    private var outboundThroughput = GlobalMetrixBinder.counter(khaosIdentifier("bytes.sent"))[mapOf("protocol" to "udp")]
    private var inboundThroughput = GlobalMetrixBinder.counter(khaosIdentifier("bytes.received"))[mapOf("protocol" to "udp")]

    suspend fun send(packet: ByteReadPacket) {
        inboundPacketCount++
        inboundThroughput += packet.remaining

        if(socket.outgoing.isClosedForSend) {
            close()
            return
        }

        socket.outgoing.send(Datagram(packet, dest))
        ttlTimer?.reset()
    }

    fun close() {
        ttlTimer?.cancel()
        job?.cancel()
        socket.close()
        connector.remove(src)
    }

    fun CoroutineScope.start(context: CoroutineContext = EmptyCoroutineContext) {
        //TODO: add udp connection ttl to config
        ttlTimer = resettableTimer(2.seconds, context) {
            close()
        }
        job = launch(context) {
            for(datagram in socket.incoming) {
                outboundPacketCount++
                outboundThroughput += datagram.packet.remaining

                if(inboundSend.isClosedForSend) {
                    break
                }

                inboundSend.send(Datagram(datagram.packet, src))
                ttlTimer?.reset()
            }
        }.apply { invokeOnCompletion { close() } }
    }
}