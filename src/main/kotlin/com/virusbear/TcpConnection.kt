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
import mu.KotlinLogging
import java.net.SocketException
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class TcpConnection(
    private val connector: TcpConnector,
    private val src: Connection,
    private val dest: Connection
) {
    private val srcAddr = src.socket.remoteAddress
    private val destAddr = dest.socket.remoteAddress

    companion object {
        private val Logger = KotlinLogging.logger("TcpConnection")
    }

    private var inbound: Job? = null
    private var outbound: Job? = null

    private var outboundThroughput = GlobalMetrixBinder.counter(khaosIdentifier("bytes.sent"))[mapOf("protocol" to "tcp")]
    private var inboundThroughput = GlobalMetrixBinder.counter(khaosIdentifier("bytes.received"))[mapOf("protocol" to "tcp")]

    fun close() {
        inbound?.cancel()
        outbound?.cancel()
        connector.remove(this)
        Logger.info { "Closed connection $srcAddr -> $destAddr" }
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
        try {
            val buf = ByteBuffer.allocate(8192)

            while(!src.isClosedForRead && !dest.isClosedForWrite) {
                //4088 as defined in ByteBufferChannel. Value used to align buffer sizes. might be multiple of this
                //TODO: Optimize with local bytebuffer to avoid unnecessary creation of byte buffers
                //TODO: use local ByteBuffer.allocate(n)
                //TODO: reuse local ByteBuffer

                src.readAvailable(buf)

                val n = src.copyTo(dest, limit = 4088)
                counter += n
                if(n == 0L) break
            }
        } catch (ex: SocketException) {
            if(ex.message == "Connection Reset") {
                Logger.info("Connection Reset. Closing connection between $srcAddr and $destAddr.")
            }
        }
    }
}