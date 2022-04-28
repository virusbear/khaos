package com.virusbear.khaos.core

fun interface EventHandler {
    fun handle(event: Event)
}