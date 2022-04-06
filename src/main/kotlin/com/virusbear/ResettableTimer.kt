package com.virusbear

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

class ResettableTimer(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    private val delay: Duration,
    private val block: suspend () -> Unit
) {
    private var job: Job? = null

    init {
        reset()
    }

    fun reset() {
        job?.cancel()
        job = scope.launch(context) {
            delay(delay.inWholeMilliseconds)
            block()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    suspend fun await() {
        job?.join()
    }
}

fun CoroutineScope.resettableTimer(delay: Duration, context: CoroutineContext = EmptyCoroutineContext, block: suspend () -> Unit): ResettableTimer {
    return ResettableTimer(this, context, delay, block)
}