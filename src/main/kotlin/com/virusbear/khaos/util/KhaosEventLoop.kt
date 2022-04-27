package com.virusbear.khaos.util

import com.virusbear.khaos.statistics.KhaosEventLoopStatistics
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.*
import kotlin.concurrent.thread

//TODO: use single eventloop for all connectors.
//    : as eventloop is not doing very much it is quite overkill to create dedicated thread per connector
//    : how to access/handle global eventloop without passing as parameter or using Global state
class KhaosEventLoop: AutoCloseable {
    interface Listener {
        fun onAcceptable(key: SelectionKey) {}
        fun onConnectable(key: SelectionKey) {}
        fun onReadable(key: SelectionKey) {}
        fun onWritable(key: SelectionKey) {}
    }

    private val listeners: MutableList<Listener> = LinkedList()
    private val selector = Selector.open()

    private val statistics = KhaosEventLoopStatistics(this, selector)
    private var eventLoopThread: Thread? = null

    private fun run() {
        while(selector.isOpen) {
            selector.keys()
            selector.select { key ->
                //TODO: create abstractionlayer above SelectionKey for Listeners. Avoid having to deal with low level SelectionKeys. This would also help swapping out Selector for different implementations

                val handler = (key.attachment() as? KhaosEventHandler?) ?: return@select

                if(key.isAcceptable) {
                    handler.handle(KhaosContext(this, key, Interest.Accept))
                }
                if(key.isConnectable) {
                    handler.handle(KhaosContext(this, key, Interest.Connect))
                }
                if(key.isWritable) {
                    handler.handle(KhaosContext(this, key, Interest.Write))
                }
                if(key.isReadable) {
                    handler.handle(KhaosContext(this, key, Interest.Read))
                }

                //TODO: Handle listener for statistics
            }
        }

        close()
    }

    fun start() {
        eventLoopThread = thread {
            use {
                run()
            }
        }
    }

    fun join() {
        eventLoopThread?.join()
    }

    fun addListener(eventListener: Listener) {
        synchronized(listeners) {
            listeners += eventListener
        }
    }

    fun removeListener(eventListener: Listener) {
        synchronized(listeners) {
            listeners -= eventListener
        }
    }

    fun register(sel: SelectableChannel, interest: Interest, handler: KhaosEventHandler): KhaosContext {
        val key = sel.keyFor(selector) ?: sel.register(selector, interest.selectionOp)
        //TODO: which object do we want to attach here?
        key.attach(handler)
        return KhaosContext(this, key, interest)
    }


    fun unregister(sel: SelectableChannel, interest: Interest) {
        val key = sel.keyFor(selector) ?: return

        KhaosContext(this, key, interest).disable()
    }

    fun wakeup() {
        selector.wakeup()
    }

    val running: Boolean
    get() = eventLoopThread?.isAlive ?: false

    override fun close() {
        statistics.close()
        selector.close()
        //maybe call join() at this point()
    }
}