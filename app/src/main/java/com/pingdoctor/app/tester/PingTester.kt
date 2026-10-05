package com.pingdoctor.app.tester

import com.pingdoctor.app.model.ProxyConfig
import com.pingdoctor.app.model.TestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object PingTester {

    private const val DEFAULT_TIMEOUT_MS = 2500

    suspend fun testSingle(config: ProxyConfig, timeoutMs: Int = DEFAULT_TIMEOUT_MS): ProxyConfig = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null

        try {
            rawSocket = Socket()
            rawSocket.soTimeout = timeoutMs
            rawSocket.connect(InetSocketAddress(config.host, config.port), timeoutMs)

            if (config.isTls) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sniHost = config.sni ?: config.host
                sslSocket = sslFactory.createSocket(rawSocket, sniHost, config.port, true) as SSLSocket
                sslSocket.soTimeout = timeoutMs

                // Set SNI Hostname parameter
                try {
                    val params = SSLParameters()
                    params.serverNames = listOf(SNIHostName(sniHost))
                    sslSocket.sslParameters = params
                } catch (e: Exception) {
                    // Ignore on older Android runtimes
                }

                sslSocket.startHandshake()
            }

            val elapsed = System.currentTimeMillis() - start
            config.copy(
                status = TestStatus.ALIVE,
                pingMs = elapsed,
                errorMessage = null
            )
        } catch (e: SocketTimeoutException) {
            config.copy(
                status = TestStatus.DEAD,
                pingMs = -1,
                errorMessage = "Timeout"
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            config.copy(
                status = TestStatus.DEAD,
                pingMs = -1,
                errorMessage = if (msg.contains("refused", ignoreCase = true)) "Refused"
                else if (msg.contains("ssl", ignoreCase = true) || msg.contains("handshake", ignoreCase = true)) "TLS Blocked"
                else "Unreachable"
            )
        } finally {
            try { sslSocket?.close() } catch (e: Exception) {}
            try { rawSocket?.close() } catch (e: Exception) {}
        }
    }

    suspend fun testAll(
        configs: List<ProxyConfig>,
        concurrency: Int = 15,
        onConfigTested: (ProxyConfig) -> Unit
    ) = coroutineScope {
        configs.chunked(concurrency).forEach { chunk ->
            chunk.map { config ->
                async(Dispatchers.IO) {
                    val result = testSingle(config)
                    withContext(Dispatchers.Main) {
                        onConfigTested(result)
                    }
                }
            }.awaitAll()
        }
    }
}
