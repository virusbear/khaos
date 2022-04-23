package com.virusbear.khaos

import io.ktor.utils.io.pool.*
import java.nio.ByteBuffer

class SharedKhaosBufferPool(capacity: Int, bufferSize: Int): KhaosBufferPool {
    private val bufferPool = DirectByteBufferPool(capacity, bufferSize)

    override fun borrow(): ByteBuffer =
        bufferPool.borrow()

    override fun recycle(buffer: ByteBuffer) {
        bufferPool.recycle(buffer)
    }

    override fun release() {}

    override fun destroy() {
        bufferPool.close()
    }
}