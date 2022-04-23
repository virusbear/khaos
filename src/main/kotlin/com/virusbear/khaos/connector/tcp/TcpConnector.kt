package com.virusbear.khaos.connector.tcp

import io.ktor.utils.io.pool.*
import mu.KotlinLogging
import java.net.BindException
import java.net.InetSocketAddress
import java.nio.channels.AlreadyBoundException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.util.*
import java.util.concurrent.ExecutorService
import kotlin.concurrent.thread

//TODO: provide builder to build testConnector
//TODO: for builder provide function of(ConnectorConfig)
class TcpConnector(
    val name: String,
    val bind: InetSocketAddress,
    val connect: InetSocketAddress,
    val blacklist: List<String>,
    val bufferPool: DirectByteBufferPool,
    val workerPool: ExecutorService
): AutoCloseable {
    private val Logger = KotlinLogging.logger("TcpConnector($name)")
    private val connections = ArrayList<TcpConnection>()

    //TODO: introduce SelectorEventLoop to handle all kind of interactions with selectors incl. dedicated threads for running selector
    private val selector = Selector.open()
    private val socket = ServerSocketChannel.open()
    private var selectorThread: Thread? = null

    private fun run() {
        Logger.info("Connector started")

        while(selector.isOpen) {
            selector.select { key ->
                if(key.isAcceptable) {
                    val channel = (key.channel() as ServerSocketChannel)
                    val client = channel.accept()
                    if((client.remoteAddress as? InetSocketAddress)?.hostString !in blacklist) {
                        Logger.debug("Accepting connection from ${client.remoteAddress}")
                        TcpConnection.configure(this, client, connect, selector, bufferPool, workerPool)
                            .let { connection ->
                            connections += connection
                        }
                    } else {
                        Logger.debug("Rejecting connection from ${client.remoteAddress}")
                        client.close()
                    }
                }
                if(key.isReadable) {
                    val connection = (key.attachment() as TcpConnection)
                    connection.service(key)
                }
            }
        }

        Logger.info("Closing connector")
        close()
    }

    fun removeConnection(connection: TcpConnection) {
        connections -= connection
    }

    fun start(wait: Boolean = false) {
        try {
            socket.bind(bind)
            socket.configureBlocking(false)
            socket.register(selector, SelectionKey.OP_ACCEPT)
        } catch(ex: AlreadyBoundException) {
            Logger.warn("Error binding connector: Already bound")
            close()
            return
        } catch(ex: BindException) {
            Logger.warn("Error binding connector: ${ex.message}")
            close()
            return
        }

        thread(isDaemon = true, name = "Connector($name)") {
            use {
                run()
            }
        }.apply {
            selectorThread = this

            if(wait) {
                join()
            }
        }
    }

    fun join() {
        selectorThread?.join()
    }

    override fun close() {
        connections.map { it }.forEach(TcpConnection::close)

        try {
            selector.close()
        } catch (ex: Throwable) { /* ignored */ }

        socket.close()

        //TODO: Bufferpool per connector or for all connectors?
        //see Application.kt
        bufferPool.close()
    }
}