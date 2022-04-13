package com.virusbear

import com.virusbear.metrix.Identifier
import com.virusbear.metrix.micrometer.MetrixBinder
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import java.io.File
import java.net.BindException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("khaos")

    val configPath by parser.option(ArgType.String, "config-path", description = "path to configuration").default("/etc/khaos")

    parser.parse(args)

    val LOGGER = KotlinLogging.logger("khaos")
    GlobalMetrixBinder.bindTo(LoggingMeterRegistry())

    val configRoot = File(configPath)
    if(!configRoot.exists()) {
        LOGGER.error("configuration directory does not exist: ${configRoot.absolutePath}")
        exitProcess(1)
    }

    val connectorsConfig = File(configPath, "connectors")
    if(!connectorsConfig.exists()) {
        LOGGER.warn("no connectors configuration found")
        exitProcess(0)
    }

    val lines = connectorsConfig.readLines().map {
        it.substringBefore("#").trim()
    }.filter { it.isNotEmpty() }


    val connectors = lines.mapNotNull {
        try {
            Connector.getByDefinition(it)
        } catch (ex: ConnectorParsingException) {
            LOGGER.warn("error parsing connector: ${ex.message} (Definition: $it)")
            null
        }
    }

    val scope = CoroutineScope(EmptyCoroutineContext)
    connectors.mapNotNull { connector ->
        //TODO: quite ugly -> fix
        scope.run {
            connector.run {
                try {
                    start()
                    this
                } catch(ex: BindException) {
                    LOGGER.warn(ex.message)
                    null
                }
            }
        }
    }.forEach {
        it.join()
    }
}

val GlobalMetrixBinder = MetrixBinder()
fun khaosIdentifier(path: String): Identifier =
    Identifier("khaos", path)