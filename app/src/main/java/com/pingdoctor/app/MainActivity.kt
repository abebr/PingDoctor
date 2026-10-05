package com.pingdoctor.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingdoctor.app.model.ProxyConfig
import com.pingdoctor.app.model.TargetService
import com.pingdoctor.app.model.TestStatus
import com.pingdoctor.app.parser.ConfigParser
import com.pingdoctor.app.tester.PingTester
import com.pingdoctor.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PingDoctorTheme {
                PingDoctorApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingDoctorApp() {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val scope = rememberCoroutineScope()

    var isPersian by remember { mutableStateOf(true) }
    var configs by remember { mutableStateOf(listOf<ProxyConfig>()) }
    var selectedTarget by remember { mutableStateOf(TargetService.YOUTUBE) }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, ALIVE, DEAD
    var isTesting by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var testedCount by remember { mutableStateOf(0) }

    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr

    fun stopTesting() {
        testJob?.cancel()
        testJob = null
        isTesting = false
    }

    fun startTesting() {
        if (configs.isEmpty() || isTesting) return
        isTesting = true
        testedCount = 0

        // Reset status to testing
        configs = configs.map {
            it.copy(
                status = TestStatus.TESTING,
                pingMs = -1,
                testedService = selectedTarget,
                errorMessage = null
            )
        }

        testJob = scope.launch {
            PingTester.testAll(configs, target = selectedTarget, concurrency = 20) { updated ->
                configs = configs.map { if (it.id == updated.id) updated else it }
                testedCount++
            }
            isTesting = false
            testJob = null
        }
    }

    val displayedConfigs = when (filterMode) {
        "ALIVE" -> configs.filter { it.status == TestStatus.ALIVE }
        "DEAD" -> configs.filter { it.status == TestStatus.DEAD }
        else -> configs
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "پینگ‌دکتر" else "PingDoctor",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isPersian) "تست بازگشایی یوتیوب و تلگرام" else "YouTube & Telegram Unblock Tester",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { isPersian = !isPersian }) {
                            Text(
                                text = if (isPersian) "EN" else "فا",
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary,
                                fontSize = 14.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
                )
            },
            containerColor = DarkBg
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Dashboard Metrics Card
                item {
                    val total = configs.size
                    val alive = configs.filter { it.status == TestStatus.ALIVE }
                    val dead = configs.filter { it.status == TestStatus.DEAD }
                    val aliveCount = alive.size
                    val deadCount = dead.size
                    val avgPing = if (aliveCount > 0) alive.map { it.pingMs }.average().toLong() else 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricBox(
                                    title = if (isPersian) "کل کانفیگ‌ها" else "Total",
                                    value = total.toString(),
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBox(
                                    title = if (isPersian) "سالم و سریع" else "Alive",
                                    value = if (avgPing > 0) "$aliveCount (${avgPing}ms)" else "$aliveCount",
                                    color = EmeraldFast,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBox(
                                    title = if (isPersian) "سوخته / بلاک" else "Dead",
                                    value = deadCount.toString(),
                                    color = RoseDead,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Progress indicator during testing
                            if (isTesting && total > 0) {
                                val progress = testedCount.toFloat() / total.toFloat()
                                val animProgress by animateFloatAsState(targetValue = progress, label = "p")

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isPersian) "تست زنده اتصال به ${selectedTarget.labelFa}..." else "Testing connection to ${selectedTarget.labelEn}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanPrimary
                                        )
                                        Text(
                                            text = "$testedCount / $total",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { animProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = CyanPrimary,
                                        trackColor = DarkBg
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Target Service Selector (YouTube, Google, Telegram, Cloudflare)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isPersian) "🎯 مقصد سنجش کیفیت واقعی:" else "🎯 Real Target Service Test:",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(TargetService.values()) { target ->
                                val isSelected = selectedTarget == target
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isTesting) selectedTarget = target
                                    },
                                    label = {
                                        Text(
                                            text = when (target) {
                                                TargetService.YOUTUBE -> "▶️ " + if (isPersian) "یوتیوب" else "YouTube"
                                                TargetService.GOOGLE -> "🌐 " + if (isPersian) "گوگل" else "Google"
                                                TargetService.TELEGRAM -> "✈️ " + if (isPersian) "تلگرام" else "Telegram"
                                                TargetService.CLOUDFLARE -> "⚡ " + if (isPersian) "کلودفلر" else "Cloudflare"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = CyanLight,
                                        containerColor = CardBg,
                                        labelColor = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = if (isSelected) CyanPrimary else CardBorder,
                                        selectedBorderColor = CyanPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Action Buttons Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Paste Button
                            Button(
                                onClick = {
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        val parsed = ConfigParser.parseClipboardText(text)
                                        if (parsed.isNotEmpty()) {
                                            configs = (configs + parsed).distinctBy { it.rawUri }
                                            Toast.makeText(
                                                context,
                                                if (isPersian) "${parsed.size} کانفیگ شناسایی و اضافه شد" else "Imported ${parsed.size} configs",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (isPersian) "کانفیگ معتبری در کلیپ‌بورد یافت نشد" else "No valid configs in clipboard",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "پیست (Paste)" else "Paste", fontSize = 12.sp, color = TextPrimary)
                            }

                            // Start / Stop Test Button
                            Button(
                                onClick = {
                                    if (isTesting) stopTesting() else startTesting()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTesting) RoseDead else CyanPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isTesting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = DarkBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTesting) {
                                        if (isPersian) "توقف" else "Stop"
                                    } else {
                                        if (isPersian) "تست ${selectedTarget.labelFa}" else "Test ${selectedTarget.labelEn}"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkBg
                                )
                            }
                        }

                        // Secondary actions: Purge Dead & Copy Working
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Purge Dead
                            OutlinedButton(
                                onClick = {
                                    val before = configs.size
                                    configs = configs.filterNot { it.status == TestStatus.DEAD }
                                    val removed = before - configs.size
                                    Toast.makeText(
                                        context,
                                        if (isPersian) "$removed کانفیگ سوخته حذف شد 🗑️" else "Purged $removed dead configs",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDead),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RoseDead.copy(alpha = 0.5f)),
                                enabled = configs.any { it.status == TestStatus.DEAD }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "حذف سوخته‌ها" else "Purge Dead", fontSize = 11.sp)
                            }

                            // Copy Working
                            OutlinedButton(
                                onClick = {
                                    val alive = configs.filter { it.status == TestStatus.ALIVE }
                                    if (alive.isNotEmpty()) {
                                        val text = alive.joinToString("\n") { it.rawUri }
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Alive Configs", text))
                                        Toast.makeText(
                                            context,
                                            if (isPersian) "${alive.size} کانفیگ سالم کپی شد 📋" else "Copied ${alive.size} working configs",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isPersian) "هنوز کانفیگ سالمی تأیید نشده است" else "No alive configs tested yet",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldFast),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldFast.copy(alpha = 0.5f)),
                                enabled = configs.any { it.status == TestStatus.ALIVE }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPersian) "کپی سالم‌ها" else "Copy Alive", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 4. Config List Header & Quick Filter Pills
                item {
                    val total = configs.size
                    val aliveCount = configs.count { it.status == TestStatus.ALIVE }
                    val deadCount = configs.count { it.status == TestStatus.DEAD }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPersian) "لیست کانفیگ‌ها (${displayedConfigs.size})" else "Configs (${displayedConfigs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )

                            if (configs.isNotEmpty()) {
                                TextButton(
                                    onClick = { configs = emptyList() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                                ) {
                                    Text(if (isPersian) "پاک کردن همه" else "Clear All", fontSize = 11.sp)
                                }
                            }
                        }

                        // Filter Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = filterMode == "ALL",
                                onClick = { filterMode = "ALL" },
                                label = { Text(if (isPersian) "همه ($total)" else "All ($total)", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = filterMode == "ALIVE",
                                onClick = { filterMode = "ALIVE" },
                                label = { Text("🟢 " + if (isPersian) "سالم ($aliveCount)" else "Alive ($aliveCount)", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = filterMode == "DEAD",
                                onClick = { filterMode = "DEAD" },
                                label = { Text("🔴 " + if (isPersian) "سوخته ($deadCount)" else "Dead ($deadCount)", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Empty State
                if (displayedConfigs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = if (configs.isEmpty()) {
                                        if (isPersian) "کانفیگی وارد نشده است" else "No configs added"
                                    } else {
                                        if (isPersian) "موردی با این فیلتر یافت نشد" else "No items in this filter"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isPersian) "لینک‌های VLESS, VMess, Trojan یا SS را کپی کنید و دکمه «پیست» را بزنید." else "Copy your proxy links and tap 'Paste' to begin diagnosis.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Config Items
                items(displayedConfigs, key = { it.id }) { item ->
                    ConfigItemRow(
                        item = item,
                        isPersian = isPersian,
                        onDelete = {
                            configs = configs.filterNot { it.id == item.id }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun MetricBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, fontSize = 11.sp, color = TextSecondary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ConfigItemRow(
    item: ProxyConfig,
    isPersian: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when (item.status) {
                    TestStatus.ALIVE -> EmeraldFast.copy(alpha = 0.4f)
                    TestStatus.DEAD -> RoseDead.copy(alpha = 0.3f)
                    TestStatus.TESTING -> CyanPrimary.copy(alpha = 0.4f)
                    TestStatus.IDLE -> CardBorder
                },
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Protocol Tag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (item.protocol) {
                            "VLESS" -> Color(0xFF6366F1).copy(alpha = 0.2f)
                            "VMESS" -> Color(0xFFA855F7).copy(alpha = 0.2f)
                            "TROJAN" -> Color(0xFFEC4899).copy(alpha = 0.2f)
                            else -> Color(0xFF0EA5E9).copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = item.protocol,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (item.protocol) {
                                "VLESS" -> Color(0xFFA5B4FC)
                                "VMESS" -> Color(0xFFD8B4FE)
                                "TROJAN" -> Color(0xFFF472B6)
                                else -> Color(0xFF7DD3FC)
                            }
                        )
                    }

                    // Config Name
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Host and Port info
                Text(
                    text = "${item.host}:${item.port}" + (if (!item.sni.isNullOrBlank()) " | SNI: ${item.sni}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (item.status) {
                    TestStatus.ALIVE -> {
                        val isFast = item.pingMs < 600
                        val targetIcon = when (item.testedService) {
                            TargetService.YOUTUBE -> "▶️ "
                            TargetService.GOOGLE -> "🌐 "
                            TargetService.TELEGRAM -> "✈️ "
                            TargetService.CLOUDFLARE -> "⚡ "
                            null -> ""
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFast) EmeraldFast.copy(alpha = 0.15f) else AmberMedium.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFast) EmeraldFast.copy(alpha = 0.6f) else AmberMedium.copy(alpha = 0.6f)
                            )
                        ) {
                            Text(
                                text = "$targetIcon${item.pingMs} ms",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFast) EmeraldFast else AmberMedium
                            )
                        }
                    }
                    TestStatus.DEAD -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RoseDead.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RoseDead.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = item.errorMessage ?: (if (isPersian) "قطع" else "Dead"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseDead
                            )
                        }
                    }
                    TestStatus.TESTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CyanPrimary
                        )
                    }
                    TestStatus.IDLE -> {
                        Text(
                            text = "-",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                // Delete Icon
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
