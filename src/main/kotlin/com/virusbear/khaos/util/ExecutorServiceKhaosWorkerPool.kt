package com.virusbear.khaos.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ExecutorServiceKhaosWorkerPool(
    private val executorService: ExecutorService
): KhaosWorkerPool {
    override fun submit(work: () -> Unit) {
        executorService.submit(work)
    }

    companion object {
        fun dynamicWorkerPool(maximumThreads: Int): KhaosWorkerPool =
            ExecutorServiceKhaosWorkerPool(
                ThreadPoolExecutor(
                    1,
                    maximumThreads,
                    60,
                    TimeUnit.SECONDS,
                    SynchronousQueue()
                )
            )
    }
}