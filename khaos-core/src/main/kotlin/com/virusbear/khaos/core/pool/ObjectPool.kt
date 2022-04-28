package com.virusbear.khaos.core.pool

interface ObjectPool<T> {
    val capacity: Int

    fun borrow(): T
    fun recycle(instance: T)

    fun clear()
}