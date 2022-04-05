package com.virusbear

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.util.LinkedList
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class TcpConnector(
    private val src: InetSocketAddress,
    private val dest: InetSocketAddress
): Connector() {
    private val dispatcher = Dispatchers.IO //TODO: maybe use: ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 60L, TimeUnit.SECONDS, SynchronousQueue()).asCoroutineDispatcher()
    private val selector = ActorSelectorManager(dispatcher)
    private lateinit var serverSocket: ServerSocket

    private val activeConnections: MutableList<TcpConnection> = LinkedList()

    private var job: Job? = null

    override fun close() {
        job?.cancel()
        activeConnections.forEach {
            it.close()
        }
        activeConnections.forEach {
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
        activeConnections -= connection
        //TODO: [metrics] decrement active connection count
    }

    override fun CoroutineScope.start(context: CoroutineContext) {
        serverSocket = aSocket(selector).tcp().bind(src)
        job = launch(context + CoroutineName("TcpConnector($src -> $dest)")) {
            while(isActive) {
                val clientSocket = serverSocket.accept()

                TcpConnection(
                    this@TcpConnector,
                    clientSocket.connection(true),
                    aSocket(selector).tcp().connect(dest).connection(true)
                ).apply {
                    start()
                    activeConnections += this
                    //TODO: [metrics] increment active connection count
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