package com.virusbear.khaos.config

import kotlinx.serialization.Serializable

@Serializable
enum class Protocol {
    tcp,
    udp
}