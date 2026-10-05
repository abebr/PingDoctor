package com.pingdoctor.app.model

enum class TestStatus {
    IDLE,
    TESTING,
    ALIVE,
    DEAD
}

enum class TargetService(
    val id: String,
    val labelEn: String,
    val labelFa: String,
    val host: String,
    val port: Int,
    val path: String
) {
    YOUTUBE("youtube", "YouTube", "یوتیوب", "www.youtube.com", 80, "/generate_204"),
    GOOGLE("google", "Google", "گوگل", "www.google.com", 80, "/generate_204"),
    TELEGRAM("telegram", "Telegram", "تلگرام", "149.154.167.50", 443, ""),
    CLOUDFLARE("cloudflare", "Cloudflare", "کلودفلر", "cp.cloudflare.com", 80, "/generate_204")
}

data class ProxyConfig(
    val id: String,
    val rawUri: String,
    val protocol: String, // VLESS, VMESS, TROJAN, SS
    val name: String,
    val host: String,
    val port: Int,
    val userOrUuid: String? = null,
    val passwordOrKey: String? = null,
    val sni: String? = null,
    val isTls: Boolean = false,
    val status: TestStatus = TestStatus.IDLE,
    val pingMs: Long = -1,
    val testedService: TargetService? = null,
    val errorMessage: String? = null
)
