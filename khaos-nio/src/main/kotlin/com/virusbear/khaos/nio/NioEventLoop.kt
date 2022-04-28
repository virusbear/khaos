package com.virusbear.khaos.nio

import com.virusbear.khaos.core.Event
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey

class NioEventLoop {
    //TODO: Not Implemented
}

data class NioSelectionEvent(
    val channel: SelectableChannel,
    val key: SelectionKey,
    val interest: Interest
): Event

enum class Interest {
    Connect,
    Accept,
    Read,
    Write
}