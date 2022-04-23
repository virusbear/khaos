package com.virusbear.khaos.connector

import com.virusbear.khaos.*
import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.tcp.TcpConnector
import com.virusbear.khaos.connector.udp.UdpConnector
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

const val MAX_UDP_PACKET_SIZE = 65535

//TODO: Implement
class ConnectorFactory(
    private val tcpBufferCount: Int,
    private val udpBufferCount: Int,
    private val tcpBufferSize: Int,
    private val workerPoolSize: Int,
    private val sharedTcpBufferPool: Lazy<SharedKhaosBufferPool>,
    private val sharedUdpBufferPool: Lazy<SharedKhaosBufferPool>
) {
    private fun createBufferPool(proto: Protocol, mode: BufferPoolMode = BufferPoolMode.dedicated): KhaosBufferPool =
        //TODO: refactor nested with
        when(mode) {
            BufferPoolMode.shared -> {
                when(proto) {
                    Protocol.udp -> sharedUdpBufferPool.value
                    Protocol.tcp -> sharedTcpBufferPool.value
                }
            }
            BufferPoolMode.dedicated -> {
                when(proto) {
                    Protocol.udp -> DedicatedKhaosBufferPool(udpBufferCount, MAX_UDP_PACKET_SIZE)
                    Protocol.tcp -> DedicatedKhaosBufferPool(tcpBufferCount, tcpBufferSize)
                }
            }
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
            Protocol.tcp -> TcpConnector(name, bind, connect, loadBlackLists(blacklists), buffers, workers)
            Protocol.udp -> UdpConnector(name, bind, connect, loadBlackLists(blacklists), workers, buffers)
        }
    }
}