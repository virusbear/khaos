package com.virusbear.khaos.connector

import com.virusbear.khaos.Blacklist
import com.virusbear.khaos.KhaosEventLoop
import com.virusbear.khaos.config.Protocol
import java.net.BindException
import java.net.InetSocketAddress
import java.nio.channels.AlreadyBoundException
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel

//TODO: Implement
abstract class AbstractConnector(
    override val name: String,
    private val bind: InetSocketAddress,
    private val connect: InetSocketAddress,
    private val blacklist: Blacklist,
    private val protocol: Protocol
): Connector {
    private val eventLoop = KhaosEventLoop()

    override fun start(wait: Boolean) {
        eventLoop.start()
        bind()

        if(wait) {
            join()
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
            eventLoop.register(channel, SelectionKey.OP_ACCEPT)
        } catch (ex: AlreadyBoundException) {
            //TODO: Logging
            close()
            return
        } catch (ex: BindException) {
            //TODO: Logging
            close()
            return
        }
    }

    override fun join() {
        eventLoop.join()
    }

    protected abstract val socket: SelectableChannel

    override fun close() {
        eventLoop.close()
        socket.close()
    }
}