package com.virusbear.khaos.util

import java.net.InetAddress

interface Blacklist {
    fun isEmpty(): Boolean
    fun accept(address: InetAddress): Boolean
}

