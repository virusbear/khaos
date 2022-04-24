package com.virusbear

import com.virusbear.khaos.config.ConnectorDefinition
import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.ConnectorFactory
import com.virusbear.khaos.connector.MAX_UDP_PACKET_SIZE
import com.virusbear.khaos.connector.tcp.TcpConnector
import com.virusbear.khaos.util.SharedKhaosBufferPool
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
    val tcpBufferCount by parser.option(ArgType.Int, "tcp-buffers", description = "the number of buffers to allocate for each tcp connector").default(1024)
    val udpBufferCount by parser.option(ArgType.Int, "udp-buffers", description = "the number of buffers to allocate for each udp connector").default(1024)
    val tcpBufferSize by parser.option(ArgType.Int, "tcp-buffer-size", description = "the size of each tcp buffer").default(8192)
    val connectorWorkerCount by parser.option(ArgType.Int, "connector-workers", description = "the number of worker threads to start per connector").default(Runtime.getRuntime().availableProcessors().coerceAtLeast(8))
    val sharedUdpBufferCount by parser.option(ArgType.Int, "shared-udp-buffers", description = "the number of buffers in the shared udp buffer pool").default(1024)
    val sharedTcpBufferCount by parser.option(ArgType.Int, "shared-tcp-buffers", description = "the number of buffers in the shared tcp buffer pool").default(1024)

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

    val sharedTcpBufferPool: () -> SharedKhaosBufferPool = { SharedKhaosBufferPool(sharedTcpBufferCount, tcpBufferSize) }
    val sharedUdpBufferPool: () -> SharedKhaosBufferPool = { SharedKhaosBufferPool(sharedUdpBufferCount, MAX_UDP_PACKET_SIZE) }

    val connectorFactory = ConnectorFactory(tcpBufferCount, udpBufferCount, tcpBufferSize, connectorWorkerCount, sharedTcpBufferPool, sharedUdpBufferPool)

    connectorDefinitions.map { (name, def) ->
        connectorFactory.create(name, def.listen, def.connect, def.protocol, def.blacklist, def.bufferMode)
    }.onEach {
        it.start(wait = false)
    }.forEach {
        it.join()
    }
}

val GlobalMetrixBinder = MetrixBinder()
fun khaosIdentifier(path: String): Identifier =
    Identifier("khaos", path)