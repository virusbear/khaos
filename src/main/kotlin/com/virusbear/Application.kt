package com.virusbear

import com.virusbear.khaos.config.ConnectorDefinition
import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.tcp.TcpConnector
import com.virusbear.metrix.Identifier
import com.virusbear.metrix.micrometer.MetrixBinder
import io.ktor.utils.io.pool.*
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

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

    val connectorsDir = File(configPath, "connectors.d")
    if(!connectorsDir.exists()) {
        LOGGER.warn("no connector configurations found")
        exitProcess(0)
    }

    val connectorDefinitions = (connectorsDir.listFiles() ?: emptyArray()).filter { it.isFile }.mapNotNull {
        ConnectorDefinition.parse(it)
    }.distinctBy { (name, _) -> name }

    //TODO: load parameters from khaos.conf
    //TODO: Bufferpool per connector or for all connectors?
    //see TcpConnector.kt
    val tcpBufferPool = DirectByteBufferPool(64, 8192)
    //TODO: load from khaos.conf -> use
    val maxTcpWorkerThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(8)
    val tcpWorkerThreadsKeepAlive = 60.0.seconds
    val tcpWorkerPool = ThreadPoolExecutor(
        1,
        maxTcpWorkerThreads,
        tcpWorkerThreadsKeepAlive.inWholeMilliseconds,
        TimeUnit.MILLISECONDS,
        SynchronousQueue()
    )

    //TODO: Temporary
    connectorDefinitions.filter { (_, definition) -> definition.protocol == Protocol.tcp }.map { (name, def) ->
        TcpConnector(name, def.listen, def.connect, def.blacklist/*Load blacklist from blacklist.d directory*/, tcpBufferPool, tcpWorkerPool)
    }.onEach {
        it.start(wait = false)
    }.forEach {
        it.join()
    }
}

val GlobalMetrixBinder = MetrixBinder()
fun khaosIdentifier(path: String): Identifier =
    Identifier("khaos", path)