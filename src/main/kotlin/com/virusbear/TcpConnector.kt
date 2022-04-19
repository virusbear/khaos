package com.virusbear

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.LinkedList
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class TcpConnector(
    src: InetSocketAddress,
    dest: InetSocketAddress
): Connector(src, dest, Protocol.Tcp) {
    private val dispatcher = Dispatchers.IO
    private val selector = ActorSelectorManager(dispatcher)
    private lateinit var serverSocket: ServerSocket

    private val activeConnections: MutableList<TcpConnection> = ConcurrentList()

    private var activeConnectionCount = GlobalMetrixBinder.gauge(khaosIdentifier("connections.active"))[mapOf("protocol" to "tcp")]

    private var job: Job? = null

    override fun close() {
        job?.cancel()

        activeConnections.forEach {
            it.close()
        }
        //use separate loop & toList() to prevent ConcurrentModificationException
        activeConnections.toList().forEach {
            remove(it)
        }

        serverSocket.close()
    }

    override fun join() {
        runBlocking {
            job?.join()
        }
    }

    internal fun remove(connection: TcpConnection) {
        if(connection in activeConnections) {
            activeConnectionCount--
        }
        activeConnections -= connection
    }

    override fun CoroutineScope.start(context: CoroutineContext) {
        serverSocket = aSocket(selector).tcp().bind(src)
        job = launch(context + CoroutineName("TcpConnector($src -> $dest)")) {
            while(isActive) {
                val clientSocket = serverSocket.accept()

                activeConnectionCount++
                TcpConnection(
                    this@TcpConnector,
                    clientSocket.connection(true),
                    aSocket(selector).tcp().connect(dest).connection(true)
                ).apply {
                    start()
                    activeConnections += this
                }
            }
        }
    }
}

fun CoroutineScope.tcpConnector(context: CoroutineContext = EmptyCoroutineContext, src: InetSocketAddress, dest: InetSocketAddress): TcpConnector {
    return TcpConnector(src, dest).apply {
        start(context)
    }
}