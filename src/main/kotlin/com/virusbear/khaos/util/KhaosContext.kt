package com.virusbear.khaos.util

import java.nio.channels.SelectionKey

enum class Interest {
    Accept,
    Connect,
    Write,
    Read
}

val Interest.selectionOp: Int
    get() = when(this) {
        Interest.Accept -> SelectionKey.OP_ACCEPT
        Interest.Connect -> SelectionKey.OP_CONNECT
        Interest.Write -> SelectionKey.OP_WRITE
        Interest.Read -> SelectionKey.OP_READ
    }


//TODO: how to access channel associated with Context?
//TODO: add additional interface handling acceptable and readable/writable objects to be retrieved here. those objects would wrap *Channel implementations to hide implementation details
class KhaosContext(
    private val eventLoop: KhaosEventLoop,
    private val key: SelectionKey,
    val interest: Interest
) {
    fun wakeup() {
        eventLoop.wakeup()
    }

    fun disable() {
        key.interestOpsAnd(interest.selectionOp)
    }

    fun enable() {
        key.interestOpsOr(interest.selectionOp)
        wakeup()
    }

    val enabled: Boolean
        get() =
            key.interestOps() and interest.selectionOp != 0

    fun cancel() {
        key.cancel()
    }
}

interface KhaosEventHandler {
    fun handle(ctx: KhaosContext)
}