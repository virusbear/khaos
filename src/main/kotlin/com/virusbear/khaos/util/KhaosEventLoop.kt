package com.virusbear.khaos.util

import com.virusbear.khaos.statistics.KhaosEventLoopStatistics
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.*
import kotlin.concurrent.thread

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
                when {
                    key.isAcceptable -> listeners.forEach { it.onAcceptable(key) }
                    key.isConnectable -> listeners.forEach { it.onConnectable(key) }
                    key.isReadable -> listeners.forEach { it.onReadable(key) }
                    key.isWritable -> listeners.forEach { it.onWritable(key) }
                }
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

    fun register(sel: SelectableChannel, interestOps: Int): SelectionKey =
        sel.register(selector, interestOps)

    fun wakeup() {
        selector.wakeup()
    }

    val running: Boolean
    get() = eventLoopThread?.isAlive ?: false

    override fun close() {
        statistics.close()
        selector.close()
    }
}