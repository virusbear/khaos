package com.virusbear.khaos.connector

interface Connector: AutoCloseable {
    val name: String
    fun start(wait: Boolean = false)
    fun join()
}