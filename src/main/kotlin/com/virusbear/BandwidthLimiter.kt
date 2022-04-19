package com.virusbear

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BandwidthLimiter {
    suspend fun acquire(length: Int)
}

//Leaky bucket limiter
class Test: BandwidthLimiter {
    val mutex = Mutex()
    val test: Channel<Int> = Channel()
    val delta: Int = 50
    val burst: Int = 100

    var tokens: Int = delta

    override suspend fun acquire(length: Int) {
        test.send(length)
    }

    fun run() {
        GlobalScope.launch {
            for(tick in ticker(1)) {
                mutex.withLock {
                    tokens += delta
                    tokens = tokens.coerceAtMost(burst)
                }
            }
        }

        GlobalScope.launch {
            for(packet in test) {
                //TODO: calculate delta time since last packet
                //TODO: add tokens aquired sind last packet to tokens

                if(tokens - packet < 0) {
                    //TODO: delay() time it takes to aquire necessary tokens
                }

                //TODO: save time
            }
        }
    }
}