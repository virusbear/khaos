package com.virusbear.khaos.connector

import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.tcp.TcpConnector
import com.virusbear.khaos.util.*
import java.net.InetSocketAddress

const val MAX_UDP_PACKET_SIZE = 65535

class ConnectorFactory(
    private val tcpBufferCount: Int,
    private val udpBufferCount: Int,
    private val tcpBufferSize: Int,
    private val workerPoolSize: Int,
    sharedTcpBufferPool: () -> SharedKhaosBufferPool,
    sharedUdpBufferPool: () -> SharedKhaosBufferPool
) {
    private val sharedTcpBufferPool by lazy {
        sharedTcpBufferPool()
    }
    private val sharedUdpBufferPool by lazy {
        sharedUdpBufferPool()
    }

    private fun createBufferPool(proto: Protocol, mode: BufferPoolMode = BufferPoolMode.dedicated): KhaosBufferPool =
        when(mode) {
            BufferPoolMode.shared -> sharedBufferPoolForProtocol(proto)
            BufferPoolMode.dedicated -> dedicatedBufferPoolForProtocol(proto)
        }

    private fun sharedBufferPoolForProtocol(protocol: Protocol): KhaosBufferPool =
        when(protocol) {
            Protocol.udp -> sharedUdpBufferPool
            Protocol.tcp -> sharedTcpBufferPool
        }

    private fun dedicatedBufferPoolForProtocol(protocol: Protocol): KhaosBufferPool =
        when(protocol) {
            Protocol.udp -> DedicatedKhaosBufferPool(udpBufferCount, MAX_UDP_PACKET_SIZE)
            Protocol.tcp -> DedicatedKhaosBufferPool(tcpBufferCount, tcpBufferSize)
        }

    //virusbear [20220423]: may be problematic for large connector count.
    //having a dedicated WorkerPool for each connector may increase performance on single connector but results in high overall thread count
    //does corePoolSize=1 solve this problem?
    //dedicated WorkerPool may be overpowered for low connection count connectors. single Workerpool could be shared
    private fun createWorkerPool(): KhaosWorkerPool =
        ExecutorServiceKhaosWorkerPool.dynamicWorkerPool(workerPoolSize)

    private fun loadBlackLists(blacklists: List<String>): MultiBlackList =
        MultiBlackList(
            blacklists.map { name ->
                BlacklistProvider[name]
            }.filter {
                !it.isEmpty()
            }
        )

    fun create(name: String, bind: InetSocketAddress, connect: InetSocketAddress, proto: Protocol, blacklists: List<String>, bufferMode: BufferPoolMode): Connector {
        val buffers = createBufferPool(proto, bufferMode)
        val workers = createWorkerPool()

        return when(proto) {
            Protocol.tcp -> TcpConnector(name, bind, connect, loadBlackLists(blacklists), workers, buffers)
            //Protocol.udp -> UdpConnector(name, bind, connect, loadBlackLists(blacklists), workers, buffers)
            Protocol.udp -> error("UDP is not yet supported")
        }
    }
}