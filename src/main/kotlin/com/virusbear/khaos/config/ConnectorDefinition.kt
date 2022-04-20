package com.virusbear.khaos.config

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.virusbear.khaos.util.DurationSerializer
import com.virusbear.khaos.util.InetSocketAddressSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import mu.KotlinLogging
import java.io.File
import java.net.InetSocketAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ConnectorDefinition(
    @Serializable(with = InetSocketAddressSerializer::class)
    val listen: InetSocketAddress,
    @Serializable(with = InetSocketAddressSerializer::class)
    val connect: InetSocketAddress,
    val protocol: Protocol = Protocol.tcp,
    @Serializable(with = DurationSerializer::class)
    val ttl: Duration = 2.0.seconds,
    //val bandwidth: BandwidthConfig? = null,
    val blacklist: List<String> = emptyList()
) {
    companion object {
        private val Logger = KotlinLogging.logger("ConnectorDefinition")

        fun parse(file: File): Pair<String, ConnectorDefinition>? {
            try {
                val config = ConfigFactory.parseFile(file)
                val definitions = Hocon.decodeFromConfig<Map<String, ConnectorDefinition>>(config)
                when {
                    definitions.isEmpty() -> {
                        Logger.warn("No connector specified in ${file.path}")
                        return null
                    }
                    definitions.size > 1 -> {
                        Logger.warn("Multiple connectors specified in ${file.path}")
                        return null
                    }
                }
                return definitions.keys.first().let { name ->
                    name to definitions[name]!!
                }
            } catch(ex: ConfigException) {
                Logger.warn("Invalid hocon configuration: ${ex.origin().description()}")
                return null
            } catch(ex: SerializationException) {
                Logger.warn("Invalid connector definition: ${file.path}")
                return null
            }
        }
    }
}