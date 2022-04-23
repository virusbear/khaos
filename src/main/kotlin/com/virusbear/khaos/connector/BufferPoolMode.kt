package com.virusbear.khaos.connector

import kotlinx.serialization.Serializable

@Serializable
enum class BufferPoolMode {
    shared,
    dedicated
}