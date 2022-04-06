package com.virusbear

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class UdpConnector(
    src: InetSocketAddress,
    dest: InetSocketAddress
): Connector(src, dest, Protocol.Udp) {
    private val dispatcher = Dispatchers.IO //TODO: maybe use: ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 60L, TimeUnit.SECONDS, SynchronousQueue()).asCoroutineDispatcher()
    private val selector = ActorSelectorManager(dispatcher)
    private lateinit var serverSocket: BoundDatagramSocket

    private var job: Job? = null

    private val translationTable: MutableMap<NetworkAddress, UDPConnection> = ConcurrentHashMap()

    override fun CoroutineScope.start(context: CoroutineContext) {
        serverSocket = aSocket(selector).udp().bind(src)

        job = launch(context + CoroutineName("UdpConnector($src -> $dest)")) {
            while(isActive) {
                for(datagram in serverSocket.incoming) {
                    val connection = translationTable.computeIfAbsent(datagram.address) {
                        //TODO: [metrics] increment active connection count
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
        translationTable -= addr
        //TODO: [metrics] decrement active connection count
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

    suspend fun send(packet: ByteReadPacket) {
        //TODO: [metrics] increment packet count and througput with correct labels
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
                //TODO: [metrics] increment packet count and througput with correct labels
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