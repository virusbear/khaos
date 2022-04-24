package com.virusbear.khaos.connector.udp

import com.virusbear.khaos.connector.Connection
import com.virusbear.khaos.util.*
import kotlinx.coroutines.GlobalScope
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import kotlin.time.Duration.Companion.seconds

class UdpConnection(
    private val connector: UdpConnector,
    private val src: SocketAddress,
    private val dest: InetSocketAddress,
    private val bufferPool: KhaosBufferPool,
    private val workerPool: KhaosWorkerPool
): Connection {
    private var timer: ResettableTimer? = null
    override val address: SocketAddress = src

    private val server by lazy {
        DatagramChannel.open().connect(dest)
    }

    override fun connect() {
        server.configureBlocking(false)
        connector.eventLoop.register(server, SelectionKey.OP_READ, TransferListener(src, server, bufferPool, workerPool))

        timer = GlobalScope.resettableTimer(2.0.seconds) {
            close()
        }
    }

    fun transfer(buf: ByteBuffer) {
        server.write(buf)
        timer?.reset()
    }

    override fun close() {
        connector.cancel(this)
        server.close()
    }

    inner class TransferListener(
        private val src: SocketAddress,
        private val dest: DatagramChannel,
        private val bufferPool: KhaosBufferPool,
        private val workerPool: KhaosWorkerPool
    ): KhaosEventLoop.Listener {
        override fun onReadable(key: SelectionKey) {
            key.interestOpsAnd(SelectionKey.OP_READ.inv())
            workerPool.submit {
                val buffer = bufferPool.borrow()
                try {
                    while(true) {
                        dest.read(buffer)
                        buffer.flip()

                    }
                } finally {
                    bufferPool.recycle(buffer)
                }
                key.interestOpsOr(SelectionKey.OP_READ)
                key.selector().wakeup()
            }
        }
    }
}