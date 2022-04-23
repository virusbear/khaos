package com.virusbear.khaos.connector

import com.virusbear.khaos.BlacklistProvider
import com.virusbear.khaos.MultiBlackList
import com.virusbear.khaos.config.Protocol
import com.virusbear.khaos.connector.tcp.TcpConnector
import com.virusbear.khaos.connector.udp.UdpConnector
import io.ktor.utils.io.pool.*
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

const val MAX_UDP_PACKET_SIZE = 65535

//TODO: Implement
class ConnectorFactory(
    private val tcpBufferCount: Int,
    private val udpBufferCount: Int,
    private val tcpBufferSize: Int,
    private val workerPoolSize: Int
) {
    //virusbear [20220423]: may be problematic for large connector count
    //when creating a n connectors the reserved memory will be n * bufferCount * bufferSize
    //maybe consider using single bufferpool for all connections
    //when having multiple connectors with low load this could help sharing buffers between all connectors resulting in lower memory footprint
    //Idea: introduce configuration parameter: BufferMode -> (Shared, Dedicated)?
    //    : Shared: Use a shared BufferPool (Low connection count connector)
    //    : Dedicated: Create dedicated BufferPool for specific connector (High connection count connector)
    //    : Specify SharedBufferPoolSize using CLI argument
    private fun createBufferPool(proto: Protocol): DirectByteBufferPool =
        when(proto) {
            //TODO: Make this function return KhaosBufferPool
            //TODO: Interface: borrow(), return(), release()[releases sharedBuffer from current connector instance (calls destroy for dedicatedPool)], destroy()[Closes all attached buffers and renders this pool unusable]
            //TODO: Implementations: DedicatedKhaosBufferPool -> close closes all attached buffers
            //TODO:                : SharedKhaosBufferPool -> close does not close attached buffers but will be kept alive -> find some other way of calling close() when it does not close anything?
            //TODO:                : Use DirectByteBufferPool under the hood. Helps change the underlying implementation later down the line
            Protocol.udp -> DirectByteBufferPool(udpBufferCount, MAX_UDP_PACKET_SIZE)
            Protocol.tcp -> DirectByteBufferPool(tcpBufferCount, tcpBufferSize)
        }

    //virusbear [20220423]: may be problematic for large connector count. See createBufferPool
    //having a dedicated WorkerPool for each connector may increase performance on single connector but results in high overall thread count
    //does corePoolSize=1 solve this problem?
    private fun createWorkerPool(): ExecutorService =
        ThreadPoolExecutor(
            1,
            workerPoolSize,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue()
        )

    private fun loadBlackLists(blacklists: List<String>): MultiBlackList =
        MultiBlackList(
            blacklists.map { name ->
                BlacklistProvider[name]
            }.filter {
                !it.isEmpty()
            }
        )

    fun create(name: String, bind: InetSocketAddress, connect: InetSocketAddress, proto: Protocol, blacklists: List<String>): Connector {
        val buffers = createBufferPool(proto)
        val workers = createWorkerPool()

        return when(proto) {
            Protocol.tcp -> TcpConnector(name, bind, connect, loadBlackLists(blacklists), buffers, workers)
            Protocol.udp -> UdpConnector(name, bind, connect, loadBlackLists(blacklists), workers, buffers)
        }
    }
}