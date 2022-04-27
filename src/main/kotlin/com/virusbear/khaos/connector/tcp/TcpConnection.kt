package com.virusbear.khaos.connector.tcp

import com.tinder.StateMachine
import com.virusbear.khaos.connector.Connection
import com.virusbear.khaos.statistics.TcpConnectorStatistics
import com.virusbear.khaos.util.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel

//TODO: build core module containing basic abstractions and implementation of eventloop
//TODO: create 2 separate modules for TCP and UDP connections
//TODO: create app module to plug everything together and make runnable as application

class TcpConnection(
    private val connector: TcpConnector,
    private val client: SocketChannel,
    private val connect: InetSocketAddress,
    private val bufferPool: KhaosBufferPool,
    private val workerPool: KhaosWorkerPool,
    private val statistics: TcpConnectorStatistics
): Connection, KhaosEventHandler {
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
            Interest.Read, TransferListener(client, server, bufferPool, workerPool, statistics::updateReceived))

        server.configureBlocking(false)
        connector.eventLoop.register(server,
            Interest.Read, TransferListener(server, client, bufferPool, workerPool, statistics::updateSent))

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

    override fun handle(ctx: KhaosContext) {
        when(ctx.channel) {
            client -> {

            }
            server -> {

            }
        }
    }

    sealed class State {
        object Idle: State()
        object Reading: State()
        object Writing: State()
        //TODO: find better names for those
        object Receiving: State()
        object Sending: State()
        object Closed: State()
    }

    sealed class Event {
        object OnStart: Event()
        object OnNotify: Event()
        object OnQueue: Event()
        object OnFinished: Event()
        object OnClose: Event()
    }

    sealed class SideEffect {
        object Write: SideEffect()
        object Read: SideEffect()
        object Close: SideEffect()
        object QueueRead: SideEffect()
        object QueueWrite: SideEffect()
    }

    val handlerStateMachine = StateMachine.create<State, Event, SideEffect> {
        initialState(State.Idle)

        state<State.Idle> {
            on<Event.OnStart> {
                transitionTo(State.Reading, SideEffect.Read)
            }
        }
        state<State.Reading> {
            on<Event.OnClose> {
                transitionTo(State.Closed, SideEffect.Close)
            }
            on<Event.OnFinished> {
                transitionTo(State.Writing, SideEffect.Write)
            }
            on<Event.OnQueue> {
                transitionTo(State.Receiving, SideEffect.QueueRead)
            }
        }
        state<State.Writing> {
            on<Event.OnClose> {
                transitionTo(State.Closed, SideEffect.Close)
            }
            on<Event.OnFinished> {
                transitionTo(State.Reading, SideEffect.Read)
            }
            on<Event.OnQueue> {
                transitionTo(State.Sending, SideEffect.QueueWrite)
            }
        }
        state<State.Receiving> {
            on<Event.OnNotify> {
                transitionTo(State.Reading, SideEffect.Read)
            }
        }
        state<State.Sending> {
            on<Event.OnNotify> {
                transitionTo(State.Writing, SideEffect.Write)
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            when (validTransition.sideEffect) {
                SideEffect.Close -> TODO()
                SideEffect.QueueRead -> TODO()
                SideEffect.QueueWrite -> TODO()
                SideEffect.Read -> read(from)
                SideEffect.Write -> write(to)
                null -> return@onTransition
            }
        }
    }

    fun write(to: SocketChannel) {
        val buf: ByteBuffer

        buf.flip()
        val count = to.write(buf)
        when {
            count == 0 -> handlerStateMachine.transition(Event.OnQueue)
            count < 0 -> handlerStateMachine.transition(Event.OnClose)
            else ->handlerStateMachine.transition(Event.OnFinished)
        }
    }

    fun read(from: SocketChannel) {
        val buf: ByteBuffer

        val count = from.read(buf)
        when {
            count == 0 -> handlerStateMachine.transition(Event.OnQueue)
            count < 0 -> handlerStateMachine.transition(Event.OnClose)
            else -> handlerStateMachine.transition(Event.OnFinished)
        }
    }

    class Handler(
        private val eventLoop: KhaosEventLoop,
        private val from: SocketChannel,
        private val to: SocketChannel,
        private val bufferPool: KhaosBufferPool
    ) {
        private var state: State = State.Idle
        set(value) {
            if(field == value)
                return

            field = value

            when(value) {
                State.Reading -> state = read()
                State.Writing -> state = write()
                State.Receiving -> registerRead()
                State.Sending -> registerWrite()
            }
        }

        private var buffer: ByteBuffer? = null

        fun read(): State {
            val buf = buffer ?: bufferPool.borrow()

            val count = from.read(buf)
            when {
                count == 0 -> return State.Receiving
                count < 0 -> return State.Closing
            }

            return State.Writing
        }

        fun write(): State {
            val buf = buffer ?: return State.Reading

            buf.flip()

            val count = to.write(buf)
            when {
                count == 0 -> return State.Sending
                count < 0 -> return State.Closing
            }

            return State.Reading
        }

        fun registerRead() {
            //TODO: make sure interest is added, not replaced
            eventLoop.register(from, Interest.Read, this)
        }

        fun registerWrite() {
            //TODO: make sure interest is added, not replaced
            eventLoop.register(to, Interest.Write, this)
        }


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