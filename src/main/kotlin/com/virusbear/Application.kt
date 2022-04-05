package com.virusbear

import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging
import java.io.File
import java.net.BindException
import kotlin.coroutines.EmptyCoroutineContext

fun main(args: Array<String>) {
    val LOGGER = KotlinLogging.logger("khaos")
    val lines = File("connectors").readLines().map {
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