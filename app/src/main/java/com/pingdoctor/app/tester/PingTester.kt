package com.pingdoctor.app.tester

import com.pingdoctor.app.model.ProxyConfig
import com.pingdoctor.app.model.TargetService
import com.pingdoctor.app.model.TestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object PingTester {

    private const val DEFAULT_TIMEOUT_MS = 3000

    suspend fun testSingle(
        config: ProxyConfig,
        target: TargetService = TargetService.YOUTUBE,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): ProxyConfig = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null

        try {
            rawSocket = Socket()
            rawSocket.soTimeout = timeoutMs
            rawSocket.connect(InetSocketAddress(config.host, config.port), timeoutMs)

            val activeSocket: Socket
            if (config.isTls) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sniHost = config.sni ?: config.host
                val secure = sslFactory.createSocket(rawSocket, sniHost, config.port, true) as SSLSocket
                secure.soTimeout = timeoutMs

                try {
                    val params = SSLParameters()
                    params.serverNames = listOf(SNIHostName(sniHost))
                    secure.sslParameters = params
                } catch (e: Exception) {
                    // Ignore on older runtimes
                }

                secure.startHandshake()
                sslSocket = secure
                activeSocket = secure
            } else {
                activeSocket = rawSocket
            }

            // Probe target service through protocol if supported
            var serviceVerified = false
            val out = activeSocket.getOutputStream()
            val input = activeSocket.getInputStream()

            if (config.protocol.equals("VLESS", ignoreCase = true) && !config.userOrUuid.isNullOrBlank()) {
                serviceVerified = probeVless(out, input, config.userOrUuid, target, timeoutMs)
            } else if (config.protocol.equals("TROJAN", ignoreCase = true) && !config.passwordOrKey.isNullOrBlank()) {
                serviceVerified = probeTrojan(out, input, config.passwordOrKey, target, timeoutMs)
            } else {
                // Generic server alive
                serviceVerified = true
            }

            val elapsed = System.currentTimeMillis() - start
            if (serviceVerified) {
                config.copy(
                    status = TestStatus.ALIVE,
                    pingMs = elapsed,
                    testedService = target,
                    errorMessage = null
                )
            } else {
                config.copy(
                    status = TestStatus.DEAD,
                    pingMs = -1,
                    testedService = target,
                    errorMessage = "${target.labelEn} Blocked"
                )
            }
        } catch (e: SocketTimeoutException) {
            config.copy(
                status = TestStatus.DEAD,
                pingMs = -1,
                testedService = target,
                errorMessage = "Timeout"
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            val errorLabel = when {
                msg.contains("refused", ignoreCase = true) -> "Refused"
                msg.contains("ssl", ignoreCase = true) || msg.contains("handshake", ignoreCase = true) -> "TLS Blocked"
                else -> "Unreachable"
            }
            config.copy(
                status = TestStatus.DEAD,
                pingMs = -1,
                testedService = target,
                errorMessage = errorLabel
            )
        } finally {
            try { sslSocket?.close() } catch (e: Exception) {}
            try { rawSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun probeVless(
        out: OutputStream,
        input: InputStream,
        uuidStr: String,
        target: TargetService,
        timeoutMs: Int
    ): Boolean {
        return try {
            val uuidBytes = uuidToBytes(UUID.fromString(uuidStr.trim()))
            val hostBytes = target.host.toByteArray(Charsets.UTF_8)
            val path = if (target.path.isNotBlank()) target.path else "/generate_204"
            val httpPayload = "GET $path HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: PingDoctor\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)

            val buffer = ByteBuffer.allocate(1 + 16 + 1 + 1 + 2 + 1 + 1 + hostBytes.size + httpPayload.size)
            buffer.put(0x00.toByte()) // version 0
            buffer.put(uuidBytes)
            buffer.put(0x00.toByte()) // addons length 0
            buffer.put(0x01.toByte()) // command TCP
            buffer.putShort(target.port.toShort()) // port (big endian)
            buffer.put(0x02.toByte()) // address type domain
            buffer.put(hostBytes.size.toByte())
            buffer.put(hostBytes)
            buffer.put(httpPayload)

            out.write(buffer.array())
            out.flush()

            val response = ByteArray(512)
            val read = input.read(response)
            if (read > 2) {
                val str = String(response, 0, read, Charsets.ISO_8859_1)
                str.contains("HTTP/1.", ignoreCase = true) || str.contains("204") || str.contains("200") || str.contains("301") || str.contains("302")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun probeTrojan(
        out: OutputStream,
        input: InputStream,
        password: String,
        target: TargetService,
        timeoutMs: Int
    ): Boolean {
        return try {
            val sha224Hex = sha224Hex(password.trim()).toByteArray(Charsets.US_ASCII)
            val hostBytes = target.host.toByteArray(Charsets.UTF_8)
            val path = if (target.path.isNotBlank()) target.path else "/generate_204"
            val httpPayload = "GET $path HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: PingDoctor\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)

            val buffer = ByteBuffer.allocate(56 + 2 + 1 + 1 + 1 + hostBytes.size + 2 + 2 + httpPayload.size)
            buffer.put(sha224Hex)
            buffer.put("\r\n".toByteArray(Charsets.US_ASCII))
            buffer.put(0x01.toByte()) // CONNECT
            buffer.put(0x03.toByte()) // domain
            buffer.put(hostBytes.size.toByte())
            buffer.put(hostBytes)
            buffer.putShort(target.port.toShort())
            buffer.put("\r\n".toByteArray(Charsets.US_ASCII))
            buffer.put(httpPayload)

            out.write(buffer.array())
            out.flush()

            val response = ByteArray(512)
            val read = input.read(response)
            if (read > 0) {
                val str = String(response, 0, read, Charsets.ISO_8859_1)
                str.contains("HTTP/1.", ignoreCase = true) || str.contains("204") || str.contains("200")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return bb.array()
    }

    private fun sha224Hex(text: String): String {
        val md = MessageDigest.getInstance("SHA-224")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun testAll(
        configs: List<ProxyConfig>,
        target: TargetService = TargetService.YOUTUBE,
        concurrency: Int = 15,
        onConfigTested: (ProxyConfig) -> Unit
    ) = coroutineScope {
        configs.chunked(concurrency).forEach { chunk ->
            chunk.map { config ->
                async(Dispatchers.IO) {
                    val result = testSingle(config, target)
                    withContext(Dispatchers.Main) {
                        onConfigTested(result)
                    }
                }
            }.awaitAll()
        }
    }
}
