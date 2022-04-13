package com.virusbear

import com.virusbear.metrix.Counter
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

    private var outboundThroughput = GlobalMetrixBinder.counter(khaosIdentifier("bytes.sent"))[mapOf("protocol" to "tcp")]
    private var inboundThroughput = GlobalMetrixBinder.counter(khaosIdentifier("bytes.received"))[mapOf("protocol" to "tcp")]

    fun close() {
        inbound?.cancel()
        outbound?.cancel()
        connector.remove(this)
    }

    fun CoroutineScope.start(context: CoroutineContext = EmptyCoroutineContext) {
        inbound = launch(context + CoroutineName("TcpConnection(inbound)")) {
            transfer(src.input, dest.output, inboundThroughput)
        }.apply { invokeOnCompletion { close() } }

        outbound = launch(context + CoroutineName("TcpConnection(outbound)")) {
            transfer(dest.input, src.output, outboundThroughput)
        }.apply { invokeOnCompletion { close() } }
    }

    private suspend fun transfer(src: ByteReadChannel, dest: ByteWriteChannel, counter: Counter.TaggedCounter) {
        while(!src.isClosedForRead && !dest.isClosedForWrite) {
            //4088 as defined in ByteBufferChannel. Value used to align buffer sizes. might be multiple of this
            val n = src.copyTo(dest, limit = 4088)
            counter += n
            if(n == 0L) break
        }
    }
}