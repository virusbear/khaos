package com.virusbear.khaos.util

import java.io.File
import java.net.InetAddress

class NamedBlacklist(
    val name: String,
    private val blacklist: List<InetAddress>
): Blacklist {
    companion object {
        fun load(file: File): Blacklist {
            val name = file.nameWithoutExtension
            val list = file.readLines().mapNotNull {
                try {
                    InetAddress.getByName(it)
                } catch(ex: Throwable) {
                    null
                }
            }

            return NamedBlacklist(name, list)
        }
    }

    override fun isEmpty(): Boolean =
        blacklist.isEmpty()

    override fun accept(address: InetAddress): Boolean =
        address !in blacklist
}