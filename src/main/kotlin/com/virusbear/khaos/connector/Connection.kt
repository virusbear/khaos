package com.virusbear.khaos.connector

import java.net.SocketAddress

interface Connection: AutoCloseable {
    val address: SocketAddress

    fun connect()
}