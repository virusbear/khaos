package com.virusbear.khaos.connector.tcp

import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.AbstractConnector
import com.virusbear.khaos.statistics.TcpConnectorStatistics
import com.virusbear.khaos.util.*
import java.net.InetSocketAddress
import java.nio.channels.*

class TcpConnector(
    name: String,
    bind: InetSocketAddress,
    private val connect: InetSocketAddress,
    blacklist: Blacklist,
    private val workerPool: KhaosWorkerPool,
    private val bufferPool: KhaosBufferPool
): AbstractConnector(name, bind, blacklist, Protocol.tcp) {
    override val socket = ServerSocketChannel.open()

    val statistics = TcpConnectorStatistics(name)

    override fun onAcceptable(key: SelectionKey) {
        val channel = key.channel() as ServerSocketChannel
        val client = channel.accept()
        accept(TcpConnection(this, client, connect, bufferPool, workerPool, statistics))
    }

    override fun close() {
        super.close()
        bufferPool.release()
        statistics.close()
    }
}