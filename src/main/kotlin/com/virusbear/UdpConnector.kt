package com.virusbear

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class UdpConnector(
    src: InetSocketAddress,
    dest: InetSocketAddress
): Connector(src, dest, Protocol.Udp) {
    private val dispatcher = Dispatchers.IO
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

fun CoroutineScope.udpConnector(context: CoroutineContext = EmptyCoroutineContext, src: InetSocketAddress, dest: InetSocketAddress): UdpConnector {
    return UdpConnector(src, dest).apply {
        start(context)
    }
}