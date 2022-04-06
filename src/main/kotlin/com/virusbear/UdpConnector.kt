package com.virusbear

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

class UdpConnector(
    src: InetSocketAddress,
    dest: InetSocketAddress
): Connector(src, dest, Protocol.Udp) {
    private val dispatcher = Dispatchers.IO //TODO: maybe use: ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 60L, TimeUnit.SECONDS, SynchronousQueue()).asCoroutineDispatcher()
    private val selector = ActorSelectorManager(dispatcher)
    private lateinit var serverSocket: BoundDatagramSocket

    private var activeConnectionCount = GlobalMetrixBinder.gauge(khaosIdentifier("connections.active"))[mapOf("protocol" to "udp")]

    private var job: Job? = null

    private val translationTable: MutableMap<NetworkAddress, UDPConnection> = ConcurrentHashMap()

    override fun CoroutineScope.start(context: CoroutineContext) {
        serverSocket = aSocket(selector).udp().bind(src)

        job = launch(context + CoroutineName("UdpConnector($src -> $dest)")) {
            while(isActive) {
                for(datagram in serverSocket.incoming) {
                    val connection = translationTable.computeIfAbsent(datagram.address) {
                        activeConnectionCount++
                        UDPConnection(
                            this@UdpConnector,
                            datagram.address,
                            dest,
                            serverSocket.outgoing,
                            aSocket(ActorSelectorManager(Dispatchers.IO)).udp().connect(dest)
                        ).apply { start() }
                    }

                    connection.send(datagram.packet)
                }
            }
        }
    }

    internal fun remove(addr: NetworkAddress) {
        if(addr in translationTable) {
            //Why is this always negativ?
            activeConnectionCount--
        }
        translationTable -= addr
    }

    override fun join() {
        runBlocking {
            job?.join()
        }
    }

    override fun close() {
        job?.cancel()

        translationTable.forEach { (_, conn) ->
            conn.close()
        }
        //use separate forEach to prevent ConcurrentModificationException
        translationTable.keys.forEach {
            remove(it)
        }

        serverSocket.close()
    }
}

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

                inboundSend.send(Datagram(datagram.packet, src))
                ttlTimer?.reset()
            }
        }.apply { invokeOnCompletion { close() } }
    }
}

fun CoroutineScope.udpConnector(context: CoroutineContext = EmptyCoroutineContext, src: InetSocketAddress, dest: InetSocketAddress): UdpConnector {
    return UdpConnector(src, dest).apply {
        start(context)
    }
}