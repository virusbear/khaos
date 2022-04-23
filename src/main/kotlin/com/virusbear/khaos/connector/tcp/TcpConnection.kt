package com.virusbear.khaos.connector.tcp

import io.ktor.utils.io.pool.*
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.ExperimentalTime

class TcpConnection(
    private val connector: TcpConnector,
    private val client: SocketChannel,
    private val server: SocketChannel,
    private val bufferPool: ObjectPool<ByteBuffer>,
    private val workerPool: ExecutorService
) {
    private var clientRunning = AtomicBoolean()
    private var serverRunning = AtomicBoolean()

    companion object {
        fun configure(connector: TcpConnector, client: SocketChannel, backend: InetSocketAddress, selector: Selector, bufferPool: ObjectPool<ByteBuffer>, workerPool: ExecutorService): TcpConnection {
            val server = SocketChannel.open(backend)
            val connection = TcpConnection(connector, client, server, bufferPool, workerPool)

            client.configureBlocking(false)
            client.register(selector, SelectionKey.OP_READ).attach(connection)

            server.configureBlocking(false)
            server.register(selector, SelectionKey.OP_READ).attach(connection)

            return connection
        }
    }

    @OptIn(ExperimentalTime::class)
    fun service(key: SelectionKey) {
        when(key.channel()) {
            client -> {
                //Remove OP_READ before launch to ensure no second accidental call to service is made.
                key.interestOpsAnd(SelectionKey.OP_READ.inv())
                workerPool.submit {
                    clientRunning.set(true)
                    transfer(client, server, clientRunning)
                    key.interestOpsOr(SelectionKey.OP_READ)
                    key.selector().wakeup()
                }
            }
            server -> {
                //Remove OP_READ before launch to ensure no second accidental call to service is made.
                key.interestOpsAnd(SelectionKey.OP_READ.inv())
                workerPool.submit {
                    serverRunning.set(true)
                    transfer(server, client, serverRunning)
                    key.interestOpsOr(SelectionKey.OP_READ)
                    key.selector().wakeup()
                }
            }
        }
    }

    fun close() {
        clientRunning.compareAndSet(true, false)
        serverRunning.compareAndSet(true, false)

        client.close()
        server.close()
        connector.removeConnection(this)
    }

    private fun transfer(from: SocketChannel, to: SocketChannel, running: AtomicBoolean) {
        val buf = bufferPool.borrow()

        try {
            while(running.get()) {
                val count = from.read(buf)
                when {
                    count == 0 -> break
                    count < 0 -> {
                        close()
                        break
                    }
                }

                //TODO: Log count to monitoring
                //TODO: get correct meter from socketchannel somehow

                buf.flip()

                while(buf.hasRemaining()) {
                    to.write(buf)
                }
                // WARNING: the above loop is evil.  Because
                // it's writing back to the same nonblocking
                // channel it read the data from, this code can
                // potentially spin in a busy loop.  In real life
                // you'd do something more useful than this.

                buf.clear()
            }
        } catch(ex: ClosedChannelException) {
            close()
        } catch(ex: AsynchronousCloseException) {
            close()
        } catch(ex: IOException) {
            //TODO: Handle Exception somehow?
            //TODO: Which exceptions may be thrown here?
            //TODO: Do we need some special handling for those?
            close()
        } finally {
            bufferPool.recycle(buf)
        }
    }
}