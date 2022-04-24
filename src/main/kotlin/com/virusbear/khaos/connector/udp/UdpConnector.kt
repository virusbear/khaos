package com.virusbear.khaos.connector.udp

import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.AbstractConnector
import com.virusbear.khaos.util.Blacklist
import com.virusbear.khaos.util.KhaosBufferPool
import com.virusbear.khaos.util.KhaosWorkerPool
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey

class UdpConnector(
    name: String,
    bind: InetSocketAddress,
    private val connect: InetSocketAddress,
    blacklist: Blacklist,
    private val workerPool: KhaosWorkerPool,
    private val bufferPool: KhaosBufferPool
): AbstractConnector(name, bind, blacklist, Protocol.udp) {
    override val socket = DatagramChannel.open()

    override fun onReadable(key: SelectionKey) {
        val channel = key.channel() as DatagramChannel

        key.interestOpsAnd(SelectionKey.OP_READ.inv())
        workerPool.submit {
            val buffer = bufferPool.borrow()
            try {
                while(true) {
                    val address = channel.receive(buffer) ?: break
                    val conn = (connections.firstOrNull { it.address == address } as? UdpConnection) ?: createConnection(address)
                    buffer.flip()
                    conn.transfer(buffer)
                }
            } finally {
                bufferPool.recycle(buffer)
            }
            key.interestOpsOr(SelectionKey.OP_READ)
            key.selector().wakeup()
        }
    }

    private fun createConnection(address: SocketAddress): UdpConnection =
        UdpConnection(this, address, connect, bufferPool, workerPool)

    override fun close() {
        super.close()
        bufferPool.release()
    }
}