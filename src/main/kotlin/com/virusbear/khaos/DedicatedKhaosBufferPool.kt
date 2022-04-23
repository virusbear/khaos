package com.virusbear.khaos

import io.ktor.utils.io.pool.*
import java.nio.ByteBuffer

class DedicatedKhaosBufferPool(capacity: Int, bufferSize: Int): KhaosBufferPool {
    private val pool = DirectByteBufferPool(capacity, bufferSize)

    override fun borrow(): ByteBuffer =
        pool.borrow()

    override fun recycle(buffer: ByteBuffer) =
        pool.recycle(buffer)

    override fun release() {
        destroy()
    }

    override fun destroy() {
        pool.close()
    }
}