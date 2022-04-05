package com.virusbear

import io.ktor.network.sockets.*

fun Socket.connection(autoFlush: Boolean = false): Connection =
    Connection(this, openReadChannel(), openWriteChannel(autoFlush))