package com.virusbear.khaos

import java.io.File
import java.net.InetAddress

interface Blacklist {
    fun isEmpty(): Boolean
    fun accept(address: InetAddress): Boolean
}

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

class MultiBlackList(
    private val blacklists: List<Blacklist>
): Blacklist {
    override fun isEmpty(): Boolean =
        blacklists.all { it.isEmpty() }

    override fun accept(address: InetAddress): Boolean =
        blacklists.all { accept(address) }
}

object BlacklistProvider {
    private val blacklists = LinkedHashMap<String, Blacklist>()

    operator fun get(name: String): Blacklist =
        synchronized(blacklists) {
            blacklists[name] ?: run {
                //TODO: load from config
                val blacklistFile = File("/etc/khaos/blacklist.d/$name")
                return if(!blacklistFile.exists()) {
                    NamedBlacklist(name, emptyList())
                } else {
                    NamedBlacklist.load(blacklistFile).also {
                        blacklists[name] = it
                    }
                }
            }
        }
}