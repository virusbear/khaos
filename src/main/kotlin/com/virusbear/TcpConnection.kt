package com.virusbear

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class TcpConnection(
    private val connector: TcpConnector,
    private val src: Connection,
    private val dest: Connection
) {
    private var inbound: Job? = null
    private var outbound: Job? = null

    fun close() {
        inbound?.cancel()
        outbound?.cancel()
        connector.remove(this)
    }

    fun CoroutineScope.start(context: CoroutineContext = EmptyCoroutineContext) {
        inbound = launch(context + CoroutineName("TcpConnection(inbound)")) {
            transfer(src.input, dest.output)
        }.apply { invokeOnCompletion { close() } }

        outbound = launch(context + CoroutineName("TcpConnection(outbound)")) {
            transfer(dest.input, src.output)
        }.apply { invokeOnCompletion { close() } }
    }

    private suspend fun transfer(src: ByteReadChannel, dest: ByteWriteChannel) {
        while(true) {
            //4088 as defined in ByteBufferChannel. Value used to align buffer sizes. might be multiple of this
            val n = src.copyTo(dest, limit = 4088)
            //TODO: [metrics] increment correct metric value (inbound outbound) how to pass correct metric tags?
            if(n == 0L) break
        }
    }
}