package com.virusbear.khaos.util

interface KhaosWorkerPool {
    fun submit(work: () -> Unit)
}