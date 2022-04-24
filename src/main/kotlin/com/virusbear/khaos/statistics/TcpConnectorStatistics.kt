package com.virusbear.khaos.statistics

import java.util.concurrent.atomic.AtomicLong

class TcpConnectorStatistics(
    val name: String
): Statistics {
    init {
        StatisticsManager.register(this)
    }

    fun close() {
        StatisticsManager.remove(this)
    }

    private val _bytesSent = AtomicLong()
    private val _bytesReceived = AtomicLong()
    private val _openConnections = AtomicLong()

    val bytesSent: Long
        get() = _bytesSent.get()
    val bytesReceived: Long
        get() = _bytesReceived.get()

    val openConnections: Long
        get() = _openConnections.get()

    fun updateSent(delta: Int) {
        _bytesSent.addAndGet(delta.toLong())
    }

    fun updateReceived(delta: Int) {
        _bytesReceived.addAndGet(delta.toLong())
    }

    fun openConnection() {
        _openConnections.incrementAndGet()
    }

    fun closeConnection() {
        _openConnections.decrementAndGet()
    }
}