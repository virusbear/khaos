package com.virusbear.khaos.nio.pool

import com.virusbear.khaos.core.pool.AbstractObjectPool
import java.nio.ByteBuffer

class ByteBufferPool(
    capacity: Int,
    private val bufferSize: Int,
    private val direct: Boolean = false
): AbstractObjectPool<ByteBuffer>(capacity) {
    override fun produce(): ByteBuffer =
        if(direct) {
            ByteBuffer.allocateDirect(bufferSize)
        } else {
            ByteBuffer.allocate(bufferSize)
        }

    override fun dispose(instance: ByteBuffer) {}

    override fun clear(instance: ByteBuffer) {
        instance.clear()
    }

    override fun validate(instance: ByteBuffer): Boolean =
        instance.isDirect == direct && instance.capacity() == bufferSize
}