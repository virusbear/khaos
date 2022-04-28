package com.virusbear.khaos.core.pool

class DslObjectPoolBuilder<T> {
    private var produce: (() -> T)? = null
    private var dispose: (T) -> Unit = {}
    private var clear: (T) -> Unit = {}
    private var validate: (T) -> Boolean = { true }

    fun produce(block: () -> T) {
        produce = block
    }

    fun dispose(block: (T) -> Unit) {
        dispose = block
    }

    fun clear(block: (T) -> Unit) {
        clear = block
    }

    fun validate(block: (T) -> Boolean) {
        validate = block
    }

    fun build(capacity: Int): DslObjectPool<T> =
        DslObjectPool(
            capacity,
            produce
                ?: error("No object producer specified. Call produce {} to define how instances should be created."),
            dispose,
            clear,
            validate
        )
}

fun <T> pool(capacity: Int, block: DslObjectPoolBuilder<T>.() -> Unit): DslObjectPool<T> =
    DslObjectPoolBuilder<T>().apply(block).build(capacity)