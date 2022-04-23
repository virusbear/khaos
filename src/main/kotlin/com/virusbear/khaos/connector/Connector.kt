package com.virusbear.khaos.connector

import com.virusbear.khaos.Blacklist
import com.virusbear.khaos.config.Protocol
import io.ktor.utils.io.pool.*
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService

//TODO: Implement
abstract class Connector(
    val name: String,
    val bind: InetSocketAddress,
    val connect: InetSocketAddress,
    val blacklist: Blacklist,
    val bufferPool: DirectByteBufferPool,
    val workerPool: ExecutorService,
    val protocol: Protocol
) {
    abstract fun start(wait: Boolean = false)
    abstract fun join()
}