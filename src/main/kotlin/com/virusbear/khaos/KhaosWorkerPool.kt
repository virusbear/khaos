package com.virusbear.khaos

interface KhaosWorkerPool {
    fun submit(work: () -> Unit)
}