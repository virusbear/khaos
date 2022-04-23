package com.virusbear.khaos.statistics

import com.virusbear.khaos.util.KhaosEventLoop
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

class KhaosEventLoopStatistics(
    private val eventLoop: KhaosEventLoop,
    private val selector: Selector
): Statistics {
    private val listener = Listener()

    init {
        StatisticsManager.register(this)
        eventLoop.addListener(listener)
    }

    fun close() {
        eventLoop.removeListener(listener)
        StatisticsManager.remove(this)
    }

    val running: Boolean
        get() = eventLoop.running

    val keysRegistered: Int
        get() =
            if(selector.isOpen) {
                selector.keys().size
            } else {
                0
            }

    var acceptableEvents: Long = 0
        private set
    var connectableEvents: Long = 0
        private set
    var readableEvents: Long = 0
        private set
    var writableEvents: Long = 0
        private set

    private inner class Listener: KhaosEventLoop.Listener {
        override fun onAcceptable(key: SelectionKey) {
            acceptableEvents++
        }

        override fun onConnectable(key: SelectionKey) {
            connectableEvents++
        }

        override fun onReadable(key: SelectionKey) {
            readableEvents++
        }

        override fun onWritable(key: SelectionKey) {
            writableEvents++
        }
    }
}