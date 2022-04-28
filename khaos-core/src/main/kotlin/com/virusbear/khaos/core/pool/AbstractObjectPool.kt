package com.virusbear.khaos.core.pool

import java.util.concurrent.ConcurrentLinkedDeque

abstract class AbstractObjectPool<T>(
    override val capacity: Int
): ObjectPool<T> {
    private val pool = ConcurrentLinkedDeque<T>()
    private var instanceCount = 0;

    protected abstract fun produce(): T
    protected abstract fun dispose(instance: T)

    protected abstract fun clear(instance: T)
    protected abstract fun validate(instance: T): Boolean

    final override fun borrow(): T =
        pool.pop() ?: produce()

    final override fun recycle(instance: T) {
        if(!validate(instance)) {
            dispose(instance)
            return
        }

        if(instanceCount >= capacity) {
            dispose(instance)
            return
        }

        clear(instance)
        pool.offer(instance)
        instanceCount++
    }

    final override fun clear() {
        pool.forEach(::dispose)
        pool.clear()
        instanceCount = 0
    }
}