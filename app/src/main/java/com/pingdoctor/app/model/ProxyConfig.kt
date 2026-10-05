package com.pingdoctor.app.model

enum class TestStatus {
    IDLE,
    TESTING,
    ALIVE,
    DEAD
}

data class ProxyConfig(
    val id: String,
    val rawUri: String,
    val protocol: String, // VLESS, VMESS, TROJAN, SS, HYSTERIA
    val name: String,
    val host: String,
    val port: Int,
    val sni: String? = null,
    val isTls: Boolean = false,
    val status: TestStatus = TestStatus.IDLE,
    val pingMs: Long = -1,
    val errorMessage: String? = null
)
