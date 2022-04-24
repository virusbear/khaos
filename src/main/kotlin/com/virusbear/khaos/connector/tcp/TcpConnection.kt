package com.virusbear.khaos.connector.tcp

import com.virusbear.khaos.connector.Connection
import com.virusbear.khaos.statistics.TcpConnectorStatistics
import com.virusbear.khaos.util.KhaosBufferPool
import com.virusbear.khaos.util.KhaosEventLoop
import com.virusbear.khaos.util.KhaosWorkerPool
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

class TcpConnection(
    private val connector: TcpConnector,
    private val client: SocketChannel,
    private val connect: InetSocketAddress,
    private val bufferPool: KhaosBufferPool,
    private val workerPool: KhaosWorkerPool,
    private val statistics: TcpConnectorStatistics
): Connection {
    override val address: SocketAddress
        get() = client.remoteAddress

    private val server by lazy {
        SocketChannel.open(connect)
    }

    var isOpen: Boolean = false
        private set

    override fun connect() {
        client.configureBlocking(false)
        connector.eventLoop.register(client,
            SelectionKey.OP_READ, TransferListener(client, server, bufferPool, workerPool, statistics::updateReceived))

        server.configureBlocking(false)
        connector.eventLoop.register(server,
            SelectionKey.OP_READ, TransferListener(server, client, bufferPool, workerPool, statistics::updateSent))

        isOpen = true
        statistics.openConnection()
    }

    override fun close() {
        isOpen = false
        statistics.closeConnection()
        //TODO: here is some minor work todo as connector calls close of this connection again
        connector.cancel(this)
        client.close()
        server.close()
    }

    inner class TransferListener(
        private val from: SocketChannel,
        private val to: SocketChannel,
        private val bufferPool: KhaosBufferPool,
        private val workerPool: KhaosWorkerPool,
        private val logThroughput: (Int) -> Unit
    ): KhaosEventLoop.Listener {
        override fun onReadable(key: SelectionKey) {
            key.interestOpsAnd(SelectionKey.OP_READ.inv())
            workerPool.submit {
                transfer(from, to)
                key.interestOpsOr(SelectionKey.OP_READ)
                key.selector().wakeup()
            }
        }

        private fun transfer(from: SocketChannel, to: SocketChannel) {
            val buf = bufferPool.borrow()

            try {
                while(isOpen) {
                    val count = from.read(buf)
                    when {
                        count == 0 -> break
                        count < 0 -> {
                            close()
                            break
                        }
                    }

                    logThroughput(count)

                    buf.flip()

                    //TODO: How to handle blocking write
                    //TODO: this will block one thread of workerpool until write is done. this results in only n simultaneous connections possible. n being the number of threads in the assigned workerpool
                    //TODO: can this somehow be coordinated to only write when writing is possible without blocking?
                    //TODO: introduce some kind of state object that is registered for either writing to or reading from sockets depending on keys selected?
                    //TODO: select OP_READ on from socket. as soon as event is fired read buffer. if write returns any number != buf.remaining queue state on OP_WRITE of to socket
                    //TODO: this will copy all data from from socket to to socket without blocking any workerthreads if no write operations are possible
                    //TODO: are there any more efficient ways of handling this blocking?
                    while(buf.hasRemaining()) {
                        to.write(buf)
                    }

                    buf.clear()
                }
            } catch (ex: ClosedChannelException) {
                close()
            } catch (ex: AsynchronousCloseException) {
                close()
            } catch (ex: IOException) {
                //TODO: Handle Exception somehow?
                //TODO: Which exceptions may be thrown here?
                //TODO: Do we need some special handling for those?
                close()
            } finally {
                bufferPool.recycle(buf)
            }
        }
    }
}