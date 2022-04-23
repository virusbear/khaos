package com.virusbear.khaos.util

import java.net.InetAddress

class MultiBlackList(
    private val blacklists: List<Blacklist>
): Blacklist {
    override fun isEmpty(): Boolean =
        blacklists.all { it.isEmpty() }

    override fun accept(address: InetAddress): Boolean =
        blacklists.all { accept(address) }
}