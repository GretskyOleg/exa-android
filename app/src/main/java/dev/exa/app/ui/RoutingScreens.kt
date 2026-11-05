package dev.exa.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.exa.app.core.AppSettings
import dev.exa.app.core.LeakTester
import dev.exa.app.core.LatencyTester
import dev.exa.app.core.Profile
import dev.exa.app.core.RouteRule
import dev.exa.app.core.VpnController
import dev.exa.app.core.VpnState
import dev.exa.app.core.XrayConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/* ------------------------------------------------------------------ routing */

@Composable
fun RoutingScreen(
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var dnsText by rememberSaveable { mutableStateOf(settings.dnsServers.joinToString(", ")) }
    var strategy by remember { mutableStateOf(settings.domainStrategy) }
    var rules by remember { mutableStateOf(settings.routingRules) }

    fun commit(
        nextRules: List<RouteRule> = rules,
        nextDns: List<String> = parseDns(dnsText),
        nextStrategy: String = strategy
    ) {
        rules = nextRules
        onSettings(
            settings.copy(
                routingRules = nextRules,
                dnsServers = nextDns,
                domainStrategy = nextStrategy
            )
        )
    }

    fun addRule(action: String, kind: String, value: String) {
        if (value.isBlank()) return
        val clean = value.trim()
        if (clean.length > 500) return
        if (rules.any { it.action == action && it.kind == kind && it.value == clean }) return
        commit(rules + RouteRule(UUID.randomUUID().toString(), action, kind, clean))
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in rules.indices) return
        val list = rules.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        commit(list)
    }

    fun remove(rule: RouteRule) {
        commit(rules.filterNot { it.id == rule.id })
    }

    Column(Modifier.fillMaxSize().background(ExaBg).statusBarsPadding().padding(horizontal = 18.dp)) {
        SubHeader("Xray-политики", "Правила по порядку · proxy / direct / block", onBack)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ExaCard {
                    Text("Как это работает", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Правила проверяются сверху вниз. Первое совпадение решает, куда пойдёт трафик: " +
                            "на VLESS-сервер (proxy), напрямую (direct) или в blackhole (block).",
                        fontSize = 11.sp,
                        color = ExaMuted
                    )
                }
            }

            item {
                ExaCard {
                    Text("Пресеты", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = { addRule("direct", "ip", "geoip:ru") },
                            label = { Text("RU напрямую", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { addRule("block", "domain", "geosite:category-ads-all") },
                            label = { Text("Реклама", fontSize = 10.sp) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                addRule("direct", "ip", "geoip:private")
                                addRule("direct", "domain", "local")
                            },
                            label = { Text("Локальная сеть", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { addRule("proxy", "domain", "geosite:geolocation-!cn") },
                            label = { Text("Всё кроме CN", fontSize = 10.sp) }
                        )
                    }
                }
            }

            item { RuleTester(rules) }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Правила · ${rules.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить")
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    ExaCard {
                        Text(
                            "Правил нет. Весь трафик идёт через выбранный профиль. " +
                                "Добавь direct для банков/локальных сайтов или block для рекламы.",
                            fontSize = 12.sp,
                            color = ExaMuted
                        )
                    }
                }
            } else {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    ExaCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                fontSize = 10.sp,
                                color = ExaMuted2,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(20.dp)
                            )
                            ActionBadge(rule.action)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    rule.kind.uppercase(),
                                    fontSize = 9.sp,
                                    color = ExaMuted2,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    rule.value,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { move(index, -1) }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Выше", tint = ExaMuted)
                            }
                            IconButton(onClick = { move(index, 1) }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Ниже", tint = ExaMuted)
                            }
                            IconButton(onClick = { remove(rule) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Удалить", tint = ExaMuted2)
                            }
                        }
                    }
                }
            }

            item {
                ExaCard {
                    Text("DNS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = dnsText,
                        onValueChange = { dnsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        "1.1.1.1, 8.8.8.8, tls://dns.google, https://cloudflare-dns.com/dns-query",
                        fontSize = 10.sp,
                        color = ExaMuted2
                    )
                    TextButton(onClick = { commit(nextDns = parseDns(dnsText)) }) {
                        Text("Сохранить DNS")
                    }
                }
            }

            item {
                ExaCard {
                    Text("Domain strategy", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { value ->
                            FilterChip(
                                selected = strategy == value,
                                onClick = {
                                    strategy = value
                                    commit(nextStrategy = value)
                                },
                                label = { Text(value, fontSize = 10.sp) }
                            )
                        }
                    }
                    Text(
                        "AsIs — быстрее, IPIfNonMatch — резолвит домен только если нужно, " +
                            "IPOnDemand — резолвит всегда.",
                        fontSize = 10.sp,
                        color = ExaMuted2
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAdd) {
        RuleDialog(
            onAdd = { action, kind, value ->
                addRule(action, kind, value)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
private fun RuleDialog(
    onAdd: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var action by remember { mutableStateOf("direct") }
    var kind by remember { mutableStateOf("domain") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое правило") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Действие", fontSize = 11.sp, color = ExaMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("proxy" to "Прокси", "direct" to "Напрямую", "block" to "Блок").forEach {
                        FilterChip(
                            selected = action == it.first,
                            onClick = { action = it.first },
                            label = { Text(it.second, fontSize = 10.sp) }
                        )
                    }
                }
                Text("Тип", fontSize = 11.sp, color = ExaMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("domain" to "Домен", "ip" to "IP", "port" to "Порт").forEach {
                        FilterChip(
                            selected = kind == it.first,
                            onClick = { kind = it.first },
                            label = { Text(it.second, fontSize = 10.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            when (kind) {
                                "ip" -> "1.2.3.0/24, geoip:ru, geoip:private"
                                "port" -> "443, 1000-2000"
                                else -> "example.com, full:example.com, geosite:category-ads-all"
                            },
                            fontSize = 11.sp
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    "Можно несколько значений через запятую. Порядок правил меняется стрелками.",
                    fontSize = 10.sp,
                    color = ExaMuted2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(action, kind, value.trim()) },
                enabled = value.isNotBlank()
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = ExaSurface,
        titleContentColor = ExaText,
        textContentColor = ExaText
    )
}

private fun parseDns(text: String): List<String> {
    val values = text.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
    return if (values.isEmpty()) listOf("1.1.1.1", "2606:4700:4700::1111") else values
}

/* --------------------------------------------------------------------- apps */

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?
)

@Composable
fun AppsScreen(
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlySelected by remember { mutableStateOf(settings.allowedApps.isNotEmpty()) }
    var selected by remember { mutableStateOf(if (onlySelected) settings.allowedApps else settings.excludedApps) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    fun persist(nextOnly: Boolean = onlySelected, nextSelected: Set<String> = selected) {
        onlySelected = nextOnly
        selected = nextSelected
        val next = if (nextOnly) {
            settings.copy(allowedApps = nextSelected, excludedApps = emptySet())
        } else {
            settings.copy(allowedApps = emptySet(), excludedApps = nextSelected)
        }
        onSettings(next)
    }

    Column(Modifier.fillMaxSize().background(ExaBg).statusBarsPadding().padding(horizontal = 18.dp)) {
        SubHeader("Приложения", "Какие приложения используют VPN", onBack)
        ExaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Только выбранные", fontWeight = FontWeight.Bold)
                    Text(
                        if (onlySelected) "VPN только для отмеченных приложений"
                        else "VPN для всех, кроме отмеченных",
                        fontSize = 11.sp,
                        color = ExaMuted
                    )
                }
                Switch(
                    checked = onlySelected,
                    onCheckedChange = { enabled ->
                        persist(
                            nextOnly = enabled,
                            nextSelected = if (enabled) settings.allowedApps else settings.excludedApps
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ExaRed,
                        checkedBorderColor = ExaRed,
                        uncheckedThumbColor = ExaMuted,
                        uncheckedTrackColor = ExaSurface2,
                        uncheckedBorderColor = ExaLine
                    )
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Поиск приложения", fontSize = 12.sp) }
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ExaRed)
            }
        } else {
            val filtered = apps.filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val next = if (selected.contains(app.packageName)) {
                                selected - app.packageName
                            } else {
                                selected + app.packageName
                            }
                            persist(nextSelected = next)
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                    .background(ExaSurface2)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName,
                                fontSize = 9.5.sp,
                                color = ExaMuted2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(
                            checked = selected.contains(app.packageName),
                            onCheckedChange = { checked ->
                                val next = if (checked) selected + app.packageName
                                else selected - app.packageName
                                persist(nextSelected = next)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ExaRed)
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { info ->
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
            val icon = runCatching {
                info.loadIcon(pm).toBitmap(48, 48).asImageBitmap()
            }.getOrNull()
            AppEntry(info.packageName, label, icon)
        }
        .sortedBy { it.label.lowercase() }
}

/* --------------------------------------------------------------------- leak */

@Composable
fun LeakScreen(onBack: () -> Unit) {
    val state by VpnController.state.collectAsStateWithLifecycle()
    var direct by remember { mutableStateOf<String?>(null) }
    var tunnel by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun check() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            direct = LeakTester.directIp()
            tunnel = LeakTester.tunnelIp(XrayConfig.SOCKS_PORT)
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(ExaBg).statusBarsPadding().padding(horizontal = 18.dp)) {
        SubHeader("Проверка утечек", "IP напрямую vs IP через туннель", onBack)

        ExaCard {
            Text("Что проверяем", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "Приложение исключено из VPN, поэтому прямой запрос показывает твой реальный IP. " +
                    "Второй запрос идёт через локальный SOCKS и показывает выходной IP VLESS-сервера.",
                fontSize = 11.sp,
                color = ExaMuted
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = ::check, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Проверить")
            }
            if (state !is VpnState.Connected) {
                Text(
                    "VPN сейчас не подключён — проверка туннеля может не сработать.",
                    fontSize = 10.sp,
                    color = ExaAmber
                )
            }
        }

        ExaCard {
            ResultRow("Прямой IP", direct, ExaMuted)
            ResultRow("IP через VPN", tunnel, ExaMuted)
            if (error != null) {
                Text(error!!, color = ExaRed, fontSize = 11.sp)
            }
            val verdict = when {
                direct == null && tunnel == null && !busy -> "Нажми «Проверить»."
                tunnel == null && !busy -> "Туннель не отвечает. Подключи VPN и проверь снова."
                direct != null && tunnel != null && direct != tunnel ->
                    "Утечки не обнаружены: реальный IP скрыт."
                direct != null && tunnel != null && direct == tunnel ->
                    "Возможна утечка: IP совпадает."
                else -> "Проверка…"
            }
            Text(
                verdict,
                color = when {
                    direct != null && tunnel != null && direct != tunnel -> ExaGreen
                    direct != null && tunnel != null -> ExaRed
                    else -> ExaMuted
                },
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String?, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = ExaMuted, modifier = Modifier.weight(1f))
        Text(
            value ?: "—",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* --------------------------------------------------------------- rule test */

@Composable
private fun RuleTester(rules: List<RouteRule>) {
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("") }
    val port = portText.trim().toIntOrNull()?.takeIf { it in 1..65535 }
    val result = remember(host, port, rules) { matchRules(host, port, rules) }

    ExaCard {
        Text("Песочница маршрутов", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            "Проверь домен или IP: видно, какое правило сработает первым.",
            fontSize = 10.5.sp,
            color = ExaMuted
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("example.com / 1.2.3.4", fontSize = 10.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                label = { Text("Домен или IP", fontSize = 10.sp) }
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                modifier = Modifier.width(84.dp),
                singleLine = true,
                placeholder = { Text("443", fontSize = 10.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                label = { Text("Порт", fontSize = 10.sp) }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (host.isBlank()) {
            Text("Введи значение для проверки.", fontSize = 11.sp, color = ExaMuted2)
        } else {
            val matched = result.first
            when {
                matched != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionBadge(matched.action)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            matched.kind.uppercase() + " // " + matched.value,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ExaText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> Text(
                    "Совпадений нет — сработает прокси по умолчанию.",
                    fontSize = 11.sp,
                    color = ExaGreen
                )
            }
            if (result.second != null) {
                Text(result.second!!, fontSize = 9.5.sp, color = ExaAmber)
            }
        }
    }
}

private fun matchRules(
    hostRaw: String,
    port: Int?,
    rules: List<RouteRule>
): Pair<RouteRule?, String?> {
    val host = hostRaw.trim().lowercase().removePrefix("http://").removePrefix("https://")
        .substringBefore('/').substringBefore(':')
    if (host.isBlank()) return null to null
    val hostIp = parseIpv4(host)
    var skippedGeo = false

    for (rule in rules) {
        val values = rule.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        when (rule.kind) {
            "domain" -> {
                if (hostIp != null) continue
                for (value in values) {
                    val raw = value.lowercase()
                    val ok = when {
                        raw.startsWith("geosite:") -> {
                            skippedGeo = true
                            false
                        }
                        raw.startsWith("regexp:") -> runCatching {
                            Regex(raw.removePrefix("regexp:")).containsMatchIn(host)
                        }.getOrDefault(false)
                        raw.startsWith("full:") -> host == raw.removePrefix("full:")
                        raw.startsWith("domain:") -> {
                            val d = raw.removePrefix("domain:")
                            d.isNotBlank() && (host == d || host.endsWith(".$d"))
                        }
                        raw.contains("*") -> globMatch(raw, host)
                        else -> host == raw || host.endsWith(".$raw")
                    }
                    if (ok) return rule to null
                }
            }
            "ip" -> {
                if (hostIp == null) continue
                for (value in values) {
                    val raw = value.lowercase()
                    if (raw.startsWith("geoip:")) {
                        skippedGeo = true
                        continue
                    }
                    if (ipv4InCidr(hostIp, raw)) return rule to null
                }
            }
            "port" -> {
                if (port == null) continue
                for (value in values) {
                    if (portMatches(port, value)) return rule to null
                }
            }
        }
    }
    return null to if (skippedGeo) {
        "Правила geosite/geoip проверяются только внутри Xray, локально они пропущены."
    } else {
        null
    }
}

private fun globMatch(pattern: String, host: String): Boolean {
    val regex = "^" + Regex.escape(pattern).replace("\\*", ".*") + "$"
    return runCatching { Regex(regex).matches(host) }.getOrDefault(false)
}

private fun portMatches(port: Int, raw: String): Boolean {
    val value = raw.trim()
    if (value.isEmpty()) return false
    return value.split(',').any { part ->
        val piece = part.trim()
        when {
            piece.contains('-') -> {
                val bounds = piece.split('-', limit = 2)
                val from = bounds.getOrNull(0)?.trim()?.toIntOrNull()
                val to = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                from != null && to != null && port in from..to
            }
            else -> piece.toIntOrNull() == port
        }
    }
}

private fun parseIpv4(text: String): Long? {
    val parts = text.split('.')
    if (parts.size != 4) return null
    var result = 0L
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        result = (result shl 8) or octet.toLong()
    }
    return result
}

private fun ipv4InCidr(ip: Long, raw: String): Boolean {
    val pieces = raw.split('/')
    val base = parseIpv4(pieces[0].trim()) ?: return false
    if (pieces.size == 1) return ip == base
    val prefix = pieces[1].trim().toIntOrNull() ?: return false
    if (prefix !in 0..32) return false
    val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    return (ip and mask) == (base and mask)
}

/* --------------------------------------------------------------- json view */

@Composable
fun ConfigScreen(profile: Profile?, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val json = remember(profile, settings) {
        profile?.let {
            runCatching { JSONObject(XrayConfig.build(it, settings)).toString(2) }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().background(ExaBg).statusBarsPadding().padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SubHeader("Xray JSON", "Итоговый конфиг до подключения", onBack)
        if (json == null) {
            ExaCard {
                Text(
                    "Сначала добавь и выбери профиль — тогда здесь появится полный JSON, " +
                        "который EXA передаст Xray-ядру.",
                    fontSize = 12.sp,
                    color = ExaMuted
                )
            }
        } else {
            ExaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile?.name ?: "Профиль", fontWeight = FontWeight.Bold)
                        Text(
                            "${settings.routingRules.size} правил · ${settings.domainStrategy} · " +
                                "DNS ${settings.dnsServers.size}",
                            fontSize = 10.5.sp,
                            color = ExaMuted
                        )
                    }
                    Text(
                        "${json.length} B",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = ExaMuted2
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(json))
                        copied = true
                    }) { Text(if (copied) "Скопировано" else "Копировать") }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Экспорт Xray JSON"))
                        }
                    }) { Text("Отправить") }
                }
                Text(
                    "Секреты профиля (id, publicKey) остаются в этом JSON. Не публикуй его.",
                    fontSize = 9.5.sp,
                    color = ExaAmber
                )
            }
            Spacer(Modifier.height(10.dp))
            ExaCard {
                SelectionContainer {
                    Text(
                        json,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        color = ExaMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
