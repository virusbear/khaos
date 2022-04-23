package com.virusbear.khaos.connector.udp

import com.virusbear.khaos.Blacklist
import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.Connector
import com.virusbear.khaos.connector.tcp.TcpConnection
import io.ktor.utils.io.pool.*
import mu.KotlinLogging
import java.net.BindException
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.channels.AlreadyBoundException
import java.nio.channels.DatagramChannel
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.ExecutorService
import kotlin.concurrent.thread

class UdpConnector(
    name: String,
    bind: InetSocketAddress,
    connect: InetSocketAddress,
    blacklist: Blacklist,
    val workerPool: ExecutorService,
    val bufferPool: DirectByteBufferPool
): Connector(name, bind, connect, blacklist, Protocol.udp) {
    private val Logger = KotlinLogging.logger("UdpConnector($name)")

    private val selector = Selector.open()
    private val socket = DatagramChannel(null)
    private var selectorThread: Thread? = null

    //TODO: refactor run implementation to abstract Connector class? Similar implementation as TcpConnector
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

    //TODO: refactor start implementation to abstract Connector class? Similar implementation as TcpConnector
    fun start(wait: Boolean = false) {
        try {
            socket.bind(bind)
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
}