package com.virusbear.khaos.connector

import com.virusbear.khaos.util.Blacklist
import com.virusbear.khaos.util.KhaosEventLoop
import com.virusbear.khaos.config.Protocol
import io.ktor.util.network.*
import mu.KotlinLogging
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.AlreadyBoundException
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel

abstract class AbstractConnector(
    final override val name: String,
    private val bind: InetSocketAddress,
    private val blacklist: Blacklist,
    private val protocol: Protocol
): Connector, KhaosEventLoop.Listener {
    private val Logger = KotlinLogging.logger("${protocol.name.uppercase()}Connector($name)")

    val eventLoop = KhaosEventLoop()

    protected val connections: MutableSet<Connection> = LinkedHashSet()

    final override fun start(wait: Boolean) {
        eventLoop.start()
        bind()

        if(wait) {
            join()
        }
    }

    final override fun accept(conn: Connection) {
        if(!blacklisted(conn.address)) {
            conn.close()
            return
        }

        conn.connect()

        synchronized(connections) {
            connections += conn
        }
    }

    final override fun cancel(conn: Connection) {
        synchronized(connections) {
            if(conn in connections) {
                connections -= conn
                conn.close()
            }
        }
    }

    private fun bind() {
        val channel = when(protocol) {
            Protocol.udp -> socket as DatagramChannel
            Protocol.tcp -> socket as ServerSocketChannel
        }

        try {
            channel.bind(bind)
            channel.configureBlocking(false)
            eventLoop.register(channel, SelectionKey.OP_ACCEPT, this)
        } catch (ex: AlreadyBoundException) {
            Logger.warn { "Error binding connector: Already bound" }
            close()
            return
        } catch (ex: BindException) {
            Logger.warn { "Error binding connector: ${ex.message}" }
            close()
            return
        }
    }

    final override fun join() {
        eventLoop.join()
    }

    protected abstract val socket: SelectableChannel

    override fun close() {
        Logger.info { "Closing." }
        connections.forEach { it.close() }
        eventLoop.close()
        socket.close()
    }

    private fun blacklisted(address: SocketAddress): Boolean =
        blacklist.accept(InetAddress.getByName(address.hostname))
}