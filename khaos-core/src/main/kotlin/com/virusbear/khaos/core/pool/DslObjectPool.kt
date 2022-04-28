package com.virusbear.khaos.core.pool

class DslObjectPool<T>(
    capacity: Int,
    private val produce: () -> T,
    private val dispose: (T) -> Unit,
    private val clear: (T) -> Unit,
    private val validate: (T) -> Boolean
): AbstractObjectPool<T>(capacity) {
    override fun produce(): T =
        produce.invoke()

    override fun dispose(instance: T) {
        dispose.invoke(instance)
    }

    override fun clear(instance: T) {
        clear.invoke(instance)
    }

    override fun validate(instance: T): Boolean =
        validate.invoke(instance)
}