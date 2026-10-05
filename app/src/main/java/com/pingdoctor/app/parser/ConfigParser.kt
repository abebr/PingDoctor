package com.pingdoctor.app.parser

import android.net.Uri
import android.util.Base64
import com.pingdoctor.app.model.ProxyConfig
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID

object ConfigParser {

    fun parseClipboardText(text: String): List<ProxyConfig> {
        val configs = mutableListOf<ProxyConfig>()
        val lines = text.lines()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            try {
                when {
                    line.startsWith("vless://", ignoreCase = true) -> parseVless(line)?.let { configs.add(it) }
                    line.startsWith("trojan://", ignoreCase = true) -> parseTrojan(line)?.let { configs.add(it) }
                    line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line)?.let { configs.add(it) }
                    line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line)?.let { configs.add(it) }
                }
            } catch (e: Exception) {
                // Skip invalid lines
            }
        }
        return configs
    }

    private fun parseVless(uriString: String): ProxyConfig? {
        return parseStandardUri(uriString, "VLESS")
    }

    private fun parseTrojan(uriString: String): ProxyConfig? {
        return parseStandardUri(uriString, "TROJAN")
    }

    private fun parseStandardUri(uriString: String, protocol: String): ProxyConfig? {
        val uri = Uri.parse(uriString)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val rawName = uri.fragment ?: "$protocol-$host"
        val name = try { URLDecoder.decode(rawName, "UTF-8") } catch (e: Exception) { rawName }

        val security = uri.getQueryParameter("security")?.lowercase()
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer")
        val isTls = security == "tls" || security == "reality" || port == 443 || !sni.isNullOrBlank()

        return ProxyConfig(
            id = UUID.randomUUID().toString(),
            rawUri = uriString,
            protocol = protocol,
            name = name.ifBlank { "$protocol-$host:$port" },
            host = host,
            port = port,
            sni = sni,
            isTls = isTls
        )
    }

    private fun parseShadowsocks(uriString: String): ProxyConfig? {
        try {
            val clean = uriString.substring(5) // remove ss://
            val hashIndex = clean.indexOf('#')
            val fragment = if (hashIndex != -1) clean.substring(hashIndex + 1) else ""
            val name = try { URLDecoder.decode(fragment, "UTF-8") } catch (e: Exception) { fragment }

            val mainPart = if (hashIndex != -1) clean.substring(0, hashIndex) else clean

            // Case 1: ss://base64@host:port
            if (mainPart.contains('@')) {
                val atIndex = mainPart.lastIndexOf('@')
                val hostPort = mainPart.substring(atIndex + 1)
                val parts = hostPort.split(":")
                if (parts.size >= 2) {
                    val host = parts[0]
                    val port = parts[1].toIntOrNull() ?: 8388
                    return ProxyConfig(
                        id = UUID.randomUUID().toString(),
                        rawUri = uriString,
                        protocol = "SS",
                        name = name.ifBlank { "SS-$host:$port" },
                        host = host,
                        port = port,
                        isTls = false
                    )
                }
            } else {
                // Case 2: base64(method:password@host:port)
                val decoded = String(Base64.decode(mainPart, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
                val atIndex = decoded.lastIndexOf('@')
                if (atIndex != -1) {
                    val hostPort = decoded.substring(atIndex + 1)
                    val parts = hostPort.split(":")
                    if (parts.size >= 2) {
                        val host = parts[0]
                        val port = parts[1].toIntOrNull() ?: 8388
                        return ProxyConfig(
                            id = UUID.randomUUID().toString(),
                            rawUri = uriString,
                            protocol = "SS",
                            name = name.ifBlank { "SS-$host:$port" },
                            host = host,
                            port = port,
                            isTls = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun parseVmess(uriString: String): ProxyConfig? {
        try {
            val b64 = uriString.substring(8)
            val jsonStr = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            val json = JSONObject(jsonStr)

            val host = json.optString("add").ifBlank { return null }
            val port = json.optInt("port", 443)
            val name = json.optString("ps", "VMess-$host:$port")
            val tls = json.optString("tls").lowercase()
            val sni = json.optString("sni").ifBlank { null }
            val isTls = tls == "tls" || port == 443 || !sni.isNullOrBlank()

            return ProxyConfig(
                id = UUID.randomUUID().toString(),
                rawUri = uriString,
                protocol = "VMESS",
                name = name,
                host = host,
                port = port,
                sni = sni,
                isTls = isTls
            )
        } catch (e: Exception) {
            return null
        }
    }
}
