package com.virusbear

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.ktor.util.network.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun main() {
    (File("./config-template/connectors.d").listFiles() ?: emptyArray()).filter { it.isFile }.mapNotNull { file ->
        try {
            file to ConfigFactory.parseFile(file)
        } catch (ex: ConfigException) {
            //TODO: Logger.warn
            println("Invalid hocon configuration: ${ex.origin().description()}. Ignoring")
            null
        }
    }.mapNotNull { (file, config) ->
        try {
            file to Hocon.Default.decodeFromConfig<Map<String, Definition>>(config)
        } catch (ex: SerializationException) {
            //TODO: Logger warn
            println("Invalid connector definition: ${file.path}. Ignoring")
            null
        }
    }.map { it.second }.fold(emptyMap<String, Definition>()) { acc, def ->
        acc + def.filter { (name, _) ->
            if(name in acc) {
                println("Duplicate connector definition: $name. Ignoring")
                false
            } else {
                true
            }
        }
    }

    val config = ConfigFactory.parseFile(File("./config-template/connectors.d/minecraft.conf"))
    val def = Hocon.Default.decodeFromConfig<Map<String, Definition>>(config)
    println(def)
}

class NetworkAddressSerializer: KSerializer<NetworkAddress> {
    override fun deserialize(decoder: Decoder): NetworkAddress {
        val address = decoder.decodeString()
        val host = address.substringBeforeLast(":")
        val port = address.substringAfterLast(":").toIntOrNull() ?: error("Invalid NetworkAddress: Port is not an int")

        return NetworkAddress(host, port)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NetworkAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NetworkAddress) {
        encoder.encodeString(value.hostname.removePrefix("/") + ":" + value.port)
    }
}

class DurationSerializer: KSerializer<Duration> {
    override fun deserialize(decoder: Decoder): Duration {
        return Duration.parseOrNull(decoder.decodeString()) ?: error("Invalid Duraiton format")
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Duration", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
data class ConnectorDef(
    @Serializable(with = NetworkAddressSerializer::class)
    val listen: NetworkAddress,
    @Serializable(with = NetworkAddressSerializer::class)
    val connect: NetworkAddress,
    val protocol: ProtocolDef = ProtocolDef.tcp,
    @Serializable(with = DurationSerializer::class)
    val ttl: Duration = 2.0.seconds
)

@Serializable
data class Definition(
    val connector: ConnectorDef,
    val bandwidth: ConnectorLimits? = null,
    val blacklist: List<String> = emptyList()
)

@Serializable
data class ConnectorLimits(
    val connector: String? = null,
    val connection: String? = null
)

@Serializable
enum class ProtocolDef {
    tcp,
    udp
}