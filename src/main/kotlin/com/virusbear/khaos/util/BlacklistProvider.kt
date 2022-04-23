package com.virusbear.khaos.util

import java.io.File

object BlacklistProvider {
    private val blacklists = LinkedHashMap<String, Blacklist>()

    operator fun get(name: String): Blacklist =
        synchronized(blacklists) {
            blacklists[name] ?: run {
                //TODO: load from directory from config
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