package com.virusbear.khaos

import java.nio.ByteBuffer

interface KhaosBufferPool {
    fun borrow(): ByteBuffer
    fun recycle(buffer: ByteBuffer)
    fun release()
    fun destroy()
}