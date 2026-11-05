package dev.exa.app.ui

import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.exa.app.BuildConfig
import dev.exa.app.R
import dev.exa.app.core.AppSettings
import dev.exa.app.core.LatencyTester
import dev.exa.app.core.Profile
import dev.exa.app.core.ProfileStore
import dev.exa.app.core.QrCode
import dev.exa.app.core.RouteRule
import dev.exa.app.core.SettingsStore
import dev.exa.app.core.Subscription
import dev.exa.app.core.SubscriptionFetcher
import dev.exa.app.core.SubscriptionResult
import dev.exa.app.core.SubscriptionStore
import dev.exa.app.core.VlessParser
import dev.exa.app.core.VpnController
import dev.exa.app.core.VpnLog
import dev.exa.app.core.VpnState
import dev.exa.app.core.XrayBridge
import dev.exa.app.core.XrayConfig
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ExaRoot(
    onConnect: (Profile) -> Unit,
    onDisconnect: () -> Unit,
    incomingLink: String? = null,
    onIncomingConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by VpnController.state.collectAsStateWithLifecycle()
    var profiles by remember { mutableStateOf(ProfileStore.load(context)) }
    var activeId by remember { mutableStateOf(ProfileStore.activeId(context)) }
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var subscriptions by remember { mutableStateOf(SubscriptionStore.load(context)) }
    var busyImport by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showProfilesDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val active = profiles.firstOrNull { it.id == activeId }

    BackHandler(enabled = subScreen != null) { subScreen = null }

    fun updateSettings(next: AppSettings) {
        SettingsStore.save(context, next)
        settings = next
    }

    val activity = context.findActivity()
    LaunchedEffect(settings.blockScreenshots, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (settings.blockScreenshots) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun refreshProfiles() {
        profiles = ProfileStore.load(context)
        activeId = ProfileStore.activeId(context)
    }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun postSubscriptions() {
        subscriptions = SubscriptionStore.load(context)
    }

    fun storeSubscriptionProfiles(sub: Subscription, result: SubscriptionResult): Int {
        val parsed = VlessParser.parseMany(result.body)
        if (parsed.isEmpty()) throw IllegalStateException("В подписке нет VLESS-профилей")
        val links = parsed.map { it.link }.toSet()
        val current = ProfileStore.load(context)
        val previousActive = current.firstOrNull { it.id == ProfileStore.activeId(context) }
        val kept = current
            .filterNot { it.subscriptionId == sub.id }
            .filterNot { it.subscriptionId == null && it.link in links }
        val fresh = parsed.map { it.copy(subscriptionId = sub.id) }
        val merged = kept + fresh
        ProfileStore.save(context, merged)
        val active = ProfileStore.activeId(context)
        if (active == null || merged.none { it.id == active }) {
            val restored = merged.firstOrNull { it.id == previousActive?.id }
                ?: merged.firstOrNull { it.link == previousActive?.link }
                ?: fresh.firstOrNull()
                ?: merged.firstOrNull()
            ProfileStore.setActive(context, restored?.id)
        }
        return fresh.size
    }

    suspend fun refreshOne(sub: Subscription, silent: Boolean): Boolean {
        return try {
            val result = SubscriptionFetcher.fetch(sub.url)
            val autoName = runCatching { URI(sub.url).host }.getOrNull()
            val displayName = if (!result.title.isNullOrBlank() && sub.name == autoName) {
                result.title!!.trim().take(64)
            } else {
                sub.name
            }
            val updated = sub.copy(
                name = displayName,
                updatedAt = System.currentTimeMillis(),
                upload = result.upload,
                download = result.download,
                total = result.total,
                expire = result.expire,
                lastError = null
            )
            val count = storeSubscriptionProfiles(updated, result)
            SubscriptionStore.upsert(context, updated)
            if (!silent) toast("«${sub.name}»: $count профилей")
            true
        } catch (t: Throwable) {
            val message = (t.message ?: "Ошибка загрузки").take(160)
            SubscriptionStore.upsert(context, sub.copy(lastError = message))
            if (!silent) toast(message)
            false
        } finally {
            postSubscriptions()
            refreshProfiles()
        }
    }

    fun addOrRefreshSubscription(urlText: String, nameText: String = "", manual: Boolean) {
        val url = urlText.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            toast("Нужен https:// URL подписки")
            return
        }
        if (url.length > 2048) {
            toast("Слишком длинный URL подписки")
            return
        }
        if (busyImport) return
        busyImport = true
        val previous = subscriptions.firstOrNull { it.url == url }
        val id = previous?.id ?: UUID.randomUUID().toString()
        val name = previous?.name ?: nameText.trim().take(64).ifBlank {
            runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Подписка"
        }
        val draft = Subscription(id = id, name = name, url = url)
        scope.launch {
            try {
                refreshOne(draft, silent = !manual)
            } finally {
                busyImport = false
            }
        }
    }

    fun refreshAllSubscriptions() {
        if (busyImport) return
        busyImport = true
        scope.launch {
            try {
                val list = SubscriptionStore.load(context)
                var ok = 0
                list.forEach { sub -> if (refreshOne(sub, silent = true)) ok++ }
                refreshProfiles()
                toast("Обновлено: $ok из ${list.size}")
            } finally {
                busyImport = false
            }
        }
    }

    fun deleteSubscription(sub: Subscription) {
        val rest = ProfileStore.load(context).filterNot { it.subscriptionId == sub.id }
        ProfileStore.save(context, rest)
        if (ProfileStore.activeId(context) !in rest.map { it.id }) {
            ProfileStore.setActive(context, rest.firstOrNull()?.id)
        }
        SubscriptionStore.remove(context, sub.id)
        postSubscriptions()
        refreshProfiles()
        toast("Подписка удалена")
    }

    fun refreshSubscription(sub: Subscription) {
        if (busyImport) return
        busyImport = true
        scope.launch {
            try {
                refreshOne(sub, silent = false)
            } finally {
                busyImport = false
            }
        }
    }

    fun importText(text: String) {
        val value = text.trim()
        if (value.isEmpty()) {
            toast("Вставь vless:// или https://")
            return
        }
        if (value.length > 100_000) {
            toast("Слишком длинная ссылка")
            return
        }
        if (value.startsWith("https://", ignoreCase = true)) {
            addOrRefreshSubscription(value, manual = true)
            return
        }
        scope.launch {
            try {
                val parsed = VlessParser.parseMany(value)
                if (parsed.isEmpty()) {
                    toast("VLESS-ссылки не найдены")
                    return@launch
                }
                var added = 0
                parsed.forEach { if (ProfileStore.add(context, it)) added++ }
                refreshProfiles()
                toast(if (added > 0) "Добавлено профилей: $added" else "Такие профили уже есть")
            } catch (t: Throwable) {
                toast(t.message ?: "Ошибка импорта")
            }
        }
    }

    LaunchedEffect(incomingLink) {
        if (incomingLink != null) {
            tab = 1
            importText(incomingLink)
            onIncomingConsumed()
        }
    }

    // Happ-style behaviour: refresh stale subscriptions in the background.
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val stale = SubscriptionStore.load(context)
            .filter { now - it.updatedAt > 6 * 60 * 60 * 1000L }
            .take(6)
        if (stale.isEmpty()) return@LaunchedEffect
        busyImport = true
        try {
            stale.forEach { refreshOne(it, silent = true) }
            refreshProfiles()
        } finally {
            busyImport = false
        }
    }

    fun select(id: String) {
        ProfileStore.setActive(context, id)
        activeId = id
    }

    fun delete(profile: Profile) {
        ProfileStore.remove(context, profile.id)
        refreshProfiles()
    }

    fun toggle() {
        when (state) {
            is VpnState.Connected, is VpnState.Connecting -> onDisconnect()
            else -> {
                val profile = active
                if (profile == null) {
                    tab = 1
                    toast("Сначала добавь профиль")
                } else {
                    onConnect(profile)
                }
            }
        }
    }

    if (subScreen != null) {
        Surface(Modifier.fillMaxSize(), color = ExaBg) {
            when (subScreen) {
            "routing" -> RoutingScreen(
                settings = settings,
                onSettings = ::updateSettings,
                onBack = { subScreen = null }
            )
            "apps" -> AppsScreen(
                settings = settings,
                onSettings = ::updateSettings,
                onBack = { subScreen = null }
            )
            "leak" -> LeakScreen(onBack = { subScreen = null })
            "config" -> ConfigScreen(
                profile = active,
                settings = settings,
                onBack = { subScreen = null }
            )
            "scan" -> QrScannerScreen(
                onResult = { value ->
                    subScreen = null
                    importText(value)
                },
                onBack = { subScreen = null }
            )
            else -> Unit
            }
        }
    } else {
        Scaffold(
            containerColor = ExaBg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { ExaNav(tab) { tab = it } }
        ) { padding ->
            Box(Modifier.fillMaxSize().background(ExaBg).padding(padding)) {
                key(tab) {
                    when (tab) {
                        0 -> HomeScreen(
                            state = state,
                            active = active,
                            profileCount = profiles.size,
                            onToggle = ::toggle,
                            onProfileClick = { showProfilesDialog = true },
                            onAdd = { tab = 1 },
                            onSettings = { tab = 3 }
                        )
                        1 -> ProfilesScreen(
                            profiles = profiles,
                            activeId = activeId,
                            settings = settings,
                            subscriptions = subscriptions,
                            busy = busyImport,
                            onSelect = ::select,
                            onDelete = ::delete,
                            onImport = ::importText,
                            onAddSubscription = { url, name ->
                                addOrRefreshSubscription(url, name, manual = true)
                            },
                            onRefreshSubscription = ::refreshSubscription,
                            onRefreshAll = ::refreshAllSubscriptions,
                            onDeleteSubscription = ::deleteSubscription,
                            onScan = { subScreen = "scan" }
                        )
                        2 -> LogsScreen()
                        else -> SettingsScreen(
                            settings = settings,
                            profiles = profiles,
                            state = state,
                            active = active,
                            onSettings = ::updateSettings,
                            onOpenRouting = { subScreen = "routing" },
                            onOpenApps = { subScreen = "apps" },
                            onOpenLeak = { subScreen = "leak" },
                            onOpenConfig = { subScreen = "config" },
                            onOpenScanner = { subScreen = "scan" }
                        )
                    }
                }
            }
        }
    }

    if (showProfilesDialog) {
        ProfileDialog(
            profiles = profiles,
            activeId = activeId,
            onSelect = { select(it); showProfilesDialog = false },
            onDismiss = { showProfilesDialog = false }
        )
    }
}

@Composable
private fun ExaNav(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = ExaSurface, contentColor = ExaText, tonalElevation = 0.dp) {
        val items = listOf(
            Triple("Главная", Icons.Rounded.Home, 0),
            Triple("Профили", Icons.Rounded.Dns, 1),
            Triple("Логи", Icons.Rounded.Article, 2),
            Triple("Ещё", Icons.Rounded.Settings, 3)
        )
        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ExaRed,
                    selectedTextColor = ExaText,
                    indicatorColor = ExaRed.copy(alpha = 0.14f),
                    unselectedIconColor = ExaMuted2,
                    unselectedTextColor = ExaMuted2
                )
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: VpnState,
    active: Profile?,
    profileCount: Int,
    onToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    val connected = state is VpnState.Connected
    val connecting = state is VpnState.Connecting
    val error = (state as? VpnState.Error)?.message

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader(onSettings)
        HeroCard(connected, connecting, error, active, onToggle)
        ProfileCard(active, onProfileClick, onAdd)
        InfoCard(profileCount)
        Text(
            "EXA // SECURE TUNNEL // VLESS",
            color = ExaMuted2,
            fontSize = 9.sp,
            letterSpacing = 1.8.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
        )
    }
}

@Composable
private fun BrandHeader(onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.exa_logo),
            contentDescription = "EXA",
            modifier = Modifier.height(44.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("EXA", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("SECURE TUNNEL // VLESS", fontSize = 9.sp, color = ExaMuted2, letterSpacing = 1.4.sp)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Настройки", tint = ExaMuted)
        }
    }
}

@Composable
private fun HeroCard(
    connected: Boolean,
    connecting: Boolean,
    error: String?,
    active: Profile?,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ExaSurface),
        border = BorderStroke(1.dp, ExaLine)
    ) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            Image(
                painter = painterResource(R.drawable.exa_hero),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.28f
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ExaSurface.copy(alpha = 0.82f), ExaSurface)
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(connected)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            connected -> "Подключено"
                            connecting -> "Подключение…"
                            else -> "Отключено"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text("EXA // 001", fontSize = 9.sp, color = ExaMuted2, letterSpacing = 1.2.sp)
                }

                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(114.dp).clip(CircleShape)
                            .background(if (connected) ExaGreen.copy(alpha = 0.10f) else ExaRed.copy(alpha = 0.10f))
                            .border(1.dp, if (connected) ExaGreen else ExaRed, CircleShape)
                            .clickable { onToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = ExaAmber,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (connected) ExaGreen else ExaRed,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (connected) "Отключить" else "Подключить",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        if (active != null) "${active.name} · ${active.host}:${active.port}" else "Нет профиля",
                        fontSize = 12.sp,
                        color = ExaMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    error ?: if (connected) "Весь трафик идёт через VLESS" else "Нажми, чтобы включить VPN",
                    color = if (error != null) ExaRed else ExaMuted2,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(active: Profile?, onProfileClick: () -> Unit, onAdd: () -> Unit) {
    ExaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Профиль", fontSize = 11.sp, color = ExaMuted2)
                Text(
                    active?.name ?: "Не выбран",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    active?.let { "${it.security.uppercase()} // ${it.network.uppercase()}" }
                        ?: "Добавь vless://",
                    fontSize = 11.sp,
                    color = ExaMuted
                )
            }
            TextButton(onClick = onProfileClick) { Text("Выбрать") }
            Button(onClick = onAdd) { Text("Добавить") }
        }
    }
}

@Composable
private fun InfoCard(profileCount: Int) {
    val xray = remember { XrayBridge.version() }
    ExaCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoItem("Xray", xray, Modifier.weight(1f))
            InfoItem("SOCKS", "127.0.0.1:${XrayConfig.SOCKS_PORT}", Modifier.weight(1f))
            InfoItem("Профилей", profileCount.toString(), Modifier.weight(0.7f))
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 9.sp, color = ExaMuted2, letterSpacing = 0.8.sp)
        Text(
            value,
            fontSize = 11.sp,
            color = ExaText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfilesScreen(
    profiles: List<Profile>,
    activeId: String?,
    settings: AppSettings,
    subscriptions: List<Subscription>,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (Profile) -> Unit,
    onImport: (String) -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onRefreshAll: () -> Unit,
    onDeleteSubscription: (Subscription) -> Unit,
    onScan: () -> Unit
) {
    var latencies by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    var qrProfile by remember { mutableStateOf<Profile?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    val scope = rememberCoroutineScope()

    fun testAll() {
        if (testing) return
        testing = true
        scope.launch {
            val result = LinkedHashMap<String, Long?>()
            profiles.forEach { result[it.id] = LatencyTester.measure(it, settings) }
            latencies = result
            testing = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Профили", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "ПОДПИСКИ // VLESS // REALITY",
                        fontSize = 9.5.sp,
                        color = ExaMuted2,
                        letterSpacing = 1.2.sp
                    )
                }
                TextButton(onClick = ::testAll, enabled = profiles.isNotEmpty() && !testing) {
                    Text(if (testing) "Тест…" else "Тест всех")
                }
            }
        }

        item { ImportCard(busy = busy, onImport = onImport, onAddSubscription = onAddSubscription, onScan = onScan) }

        if (subscriptions.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Подписки · ${subscriptions.size}",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefreshAll, enabled = !busy) {
                        Text(if (busy) "Обновление…" else "Обновить все")
                    }
                }
            }
            items(subscriptions, key = { it.id }) { sub ->
                SubscriptionCard(
                    sub = sub,
                    count = profiles.count { it.subscriptionId == sub.id },
                    busy = busy,
                    onRefresh = { onRefreshSubscription(sub) },
                    onDelete = { pendingDelete = sub }
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Конфигурации · ${profiles.size}",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (profiles.isEmpty()) {
            item {
                ExaCard {
                    Text(
                        "Пока пусто. Вставь vless://, отсканируй QR или добавь HTTPS-подписку — " +
                            "профили появятся здесь сразу, как в Happ.",
                        color = ExaMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            val known = subscriptions.map { it.id }.toSet()
            val grouped = profiles.groupBy { it.subscriptionId }
            subscriptions.forEach { sub ->
                val list = grouped[sub.id].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "header-${sub.id}") {
                        GroupHeader(sub.name, list.size)
                    }
                    items(list, key = { it.id }) { profile ->
                        ProfileCardItem(
                            profile = profile,
                            selected = profile.id == activeId,
                            tested = latencies.containsKey(profile.id),
                            latency = latencies[profile.id],
                            onSelect = { onSelect(profile.id) },
                            onDelete = { onDelete(profile) },
                            onQr = { qrProfile = profile }
                        )
                    }
                }
            }
            val rest = profiles.filter { it.subscriptionId == null || it.subscriptionId !in known }
            if (rest.isNotEmpty()) {
                item(key = "header-manual") { GroupHeader("Вручную", rest.size) }
                items(rest, key = { it.id }) { profile ->
                    ProfileCardItem(
                        profile = profile,
                        selected = profile.id == activeId,
                        tested = latencies.containsKey(profile.id),
                        latency = latencies[profile.id],
                        onSelect = { onSelect(profile.id) },
                        onDelete = { onDelete(profile) },
                        onQr = { qrProfile = profile }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    qrProfile?.let { profile ->
        QrShareDialog(profile = profile, onDismiss = { qrProfile = null })
    }

    pendingDelete?.let { sub ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить подписку?") },
            text = {
                Text(
                    "«${sub.name}» и все её профили будут удалены. Это действие нельзя отменить.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSubscription(sub)
                    pendingDelete = null
                }) { Text("Удалить", color = ExaRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
            containerColor = ExaSurface,
            titleContentColor = ExaText,
            textContentColor = ExaMuted
        )
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            fontSize = 9.5.sp,
            color = ExaMuted2,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Badge("$count")
    }
}

@Composable
private fun SubscriptionCard(
    sub: Subscription,
    count: Int,
    busy: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val used = (sub.upload + sub.download).coerceAtLeast(0)
    ExaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    runCatching { URI(sub.url).host }.getOrNull() ?: sub.url,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = ExaMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = ExaMuted)
            }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Rounded.Delete, contentDescription = "Удалить", tint = ExaMuted2)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Badge("$count профилей")
            Badge(relativeTime(sub.updatedAt))
            if (sub.expire > 0) Badge("до ${formatDate(sub.expire)}")
        }
        if (sub.total > 0) {
            val fraction = (used.toDouble() / sub.total.toDouble()).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp),
                color = when {
                    fraction >= 0.95f -> ExaRed
                    fraction >= 0.8f -> ExaAmber
                    else -> ExaGreen
                },
                trackColor = ExaLine
            )
            Text(
                "${formatBytes(used)} / ${formatBytes(sub.total)}",
                fontSize = 10.sp,
                color = ExaMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (used > 0) {
            Text(
                "Трафик: ${formatBytes(used)}",
                fontSize = 10.sp,
                color = ExaMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (sub.lastError != null) {
            Text(
                "Ошибка: ${sub.lastError}",
                fontSize = 9.5.sp,
                color = ExaRed,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ImportCard(
    busy: Boolean,
    onImport: (String) -> Unit,
    onAddSubscription: (String, String) -> Unit,
    onScan: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("vless") }
    var text by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    ExaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Добавить конфигурацию",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = ExaRed,
                    strokeWidth = 2.dp
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            FilterChip(
                selected = mode == "vless",
                onClick = { mode = "vless" },
                label = { Text("VLESS / QR", fontSize = 10.sp) }
            )
            FilterChip(
                selected = mode == "sub",
                onClick = { mode = "sub" },
                label = { Text("Подписка", fontSize = 10.sp) }
            )
        }

        if (mode == "vless") {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ExaBg)
                    .border(1.dp, ExaLine, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        "vless://uuid@host:443?...#Имя",
                        fontSize = 12.sp,
                        color = ExaMuted2,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = ExaText
                    ),
                    cursorBrush = SolidColor(ExaRed),
                    maxLines = 6
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Вставить", fontSize = 12.sp)
                }
                TextButton(onClick = onScan) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("QR", fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onImport(text); text = "" },
                    enabled = text.isNotBlank() && !busy
                ) { Text("Импортировать") }
            }
        } else {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text("https:// подписка", fontSize = 11.sp) },
                placeholder = { Text("https://panel/sub?id=…", fontSize = 11.sp) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true,
                label = { Text("Название (необязательно)", fontSize = 11.sp) },
                textStyle = MaterialTheme.typography.bodySmall
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Трафик и срок подтянутся из заголовков.",
                    fontSize = 9.5.sp,
                    color = ExaMuted2,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onAddSubscription(url, name)
                        url = ""
                        name = ""
                    },
                    enabled = url.trim().startsWith("https://") && !busy
                ) { Text(if (busy) "Загрузка…" else "Добавить") }
            }
        }
        Text(
            "Подписки обновляются автоматически при запуске. Профили группируются по подписке.",
            fontSize = 9.5.sp,
            color = ExaMuted2,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "не обновлялась"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> "${diff / 60_000} мин назад"
        diff < 86_400_000 -> "${diff / 3_600_000} ч назад"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} дн назад"
        else -> formatDate(timestamp / 1000)
    }
}

private fun formatDate(epochSeconds: Long): String =
    runCatching {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(epochSeconds * 1000))
    }.getOrDefault("—")

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B"
    else String.format(Locale.US, "%.1f %s", value, units[unit])
}

@Composable
private fun ProfileCardItem(
    profile: Profile,
    selected: Boolean,
    tested: Boolean,
    latency: Long?,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onQr: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ExaRed.copy(alpha = 0.055f) else ExaSurface
        ),
        border = BorderStroke(1.dp, if (selected) ExaRed.copy(alpha = 0.38f) else ExaLine)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = ExaRed)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${profile.host}:${profile.port}",
                    fontSize = 10.5.sp,
                    color = ExaMuted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Badge(profile.security)
                    Badge(profile.network)
                    if (tested) {
                        Badge(if (latency != null) "$latency мс" else "нет ответа")
                    }
                }
            }
            IconButton(onClick = onQr) {
                Icon(Icons.Rounded.QrCode2, contentDescription = "QR-код профиля", tint = ExaMuted)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Удалить", tint = ExaMuted2)
            }
        }
    }
}

@Composable
private fun QrShareDialog(profile: Profile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val qr = remember(profile.link) { QrCode.bitmap(profile.link, 720) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ExaSurface),
            border = BorderStroke(1.dp, ExaLine)
        ) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Поделиться профилем",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    profile.name,
                    fontSize = 11.sp,
                    color = ExaMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(14.dp))
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "QR профиля",
                        modifier = Modifier
                            .size(232.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                } else {
                    Text("Не удалось создать QR", color = ExaRed, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Сканируется другим устройством в EXA → Профили → QR",
                    fontSize = 9.5.sp,
                    color = ExaMuted2,
                    textAlign = TextAlign.Center
                )
                Text(
                    "QR содержит ключ доступа. Не публикуй его.",
                    fontSize = 9.5.sp,
                    color = ExaAmber,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(profile.link))
                    }) { Text("Копировать") }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, profile.link)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, "Поделиться профилем"))
                        }
                    }) { Text("Отправить") }
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}

@Composable
private fun LogsScreen() {
    val logs by VpnLog.lines.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Логи", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("core, VPN, HEV", fontSize = 10.sp, color = ExaMuted2, letterSpacing = 1.2.sp)
            }
            TextButton(onClick = { VpnLog.clear() }) { Text("Очистить") }
        }
        ExaCard(Modifier.weight(1f)) {
            if (logs.isEmpty()) {
                Text("Пока пусто. Подключись, и здесь появятся события.", color = ExaMuted, fontSize = 12.sp)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = ExaMuted,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    profiles: List<Profile>,
    state: VpnState,
    active: Profile?,
    onSettings: (AppSettings) -> Unit,
    onOpenRouting: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenLeak: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    val connected = state is VpnState.Connected
    var applied by remember { mutableStateOf<String?>(null) }

    fun rule(action: String, kind: String, value: String) =
        RouteRule(UUID.randomUUID().toString(), action, kind, value)

    fun applyScenario(name: String, rules: List<RouteRule>, strategy: String) {
        onSettings(settings.copy(routingRules = rules, domainStrategy = strategy))
        applied = name
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Центр управления", fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    "EXA // POLICIES & PRIVACY",
                    fontSize = 9.5.sp,
                    color = ExaMuted2,
                    letterSpacing = 1.5.sp
                )
            }
            Badge("PRO")
        }

        ExaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(connected)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            connected -> "Туннель активен"
                            state is VpnState.Connecting -> "Подключение…"
                            else -> "Туннель выключен"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        active?.let { "${it.name} · ${it.security.uppercase()}" } ?: "Профиль не выбран",
                        fontSize = 10.5.sp,
                        color = ExaMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (connected) "ON" else "OFF",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (connected) ExaGreen else ExaMuted2
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("ПРОФИЛИ", profiles.size.toString(), Modifier.weight(1f))
                StatTile("ПРАВИЛА", settings.routingRules.size.toString(), Modifier.weight(1f))
                StatTile(
                    "ПРИЛОЖЕНИЯ",
                    if (settings.allowedApps.isNotEmpty()) settings.allowedApps.size.toString()
                    else if (settings.excludedApps.isNotEmpty()) "−${settings.excludedApps.size}"
                    else "все",
                    Modifier.weight(1f)
                )
            }
        }

        SectionLabel("Сценарии одним тапом")
        ExaCard {
            Text(
                "Мгновенно переключает набор Xray-правил. Свои правила можно донастроить ниже.",
                fontSize = 10.5.sp,
                color = ExaMuted
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = applied == "Весь трафик",
                    onClick = { applyScenario("Весь трафик", emptyList(), "AsIs") },
                    modifier = Modifier.weight(1f),
                    label = { Text("Весь трафик", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = applied == "RU напрямую",
                    onClick = {
                        applyScenario(
                            "RU напрямую",
                            listOf(
                                rule("direct", "ip", "geoip:ru"),
                                rule("direct", "domain", "geosite:category-ru"),
                                rule("direct", "ip", "geoip:private"),
                                rule("block", "domain", "geosite:category-ads-all")
                            ),
                            "IPIfNonMatch"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("RU напрямую", fontSize = 10.sp) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = applied == "Приватность",
                    onClick = {
                        applyScenario(
                            "Приватность",
                            listOf(
                                rule("block", "domain", "geosite:category-ads-all"),
                                rule("direct", "ip", "geoip:private")
                            ),
                            "IPOnDemand"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Приватность", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = applied == "Антиреклама",
                    onClick = {
                        applyScenario(
                            "Антиреклама",
                            listOf(rule("block", "domain", "geosite:category-ads-all")),
                            settings.domainStrategy
                        )
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Антиреклама", fontSize = 10.sp) }
                )
            }
            if (applied != null) {
                Text(
                    "Применено: $applied",
                    fontSize = 10.sp,
                    color = ExaGreen,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        SectionLabel("Подключение")
        ExaCard {
            SettingSwitch(
                "Автоподключение",
                "При загрузке Android, если VPN уже разрешён",
                settings.autoConnect
            ) { onSettings(settings.copy(autoConnect = it)) }
            SettingSwitch(
                "EXA Pulse",
                "Тестировать профили и выбирать самый быстрый",
                settings.smartConnect
            ) { onSettings(settings.copy(smartConnect = it)) }
            SettingSwitch(
                "Профиль по сети",
                "Разные профили для Wi-Fi и мобильной сети",
                settings.autoNetworkProfile
            ) { onSettings(settings.copy(autoNetworkProfile = it)) }
            if (settings.autoNetworkProfile && profiles.isNotEmpty()) {
                ProfilePicker("Wi-Fi", profiles, settings.wifiProfileId) {
                    onSettings(settings.copy(wifiProfileId = it))
                }
                ProfilePicker("Мобильная сеть", profiles, settings.mobileProfileId) {
                    onSettings(settings.copy(mobileProfileId = it))
                }
            }
        }

        SectionLabel("Политики Xray")
        ExaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${settings.routingRules.size} правил",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
                Spacer(Modifier.weight(1f))
                Badge(settings.domainStrategy)
            }
            Text(
                "DNS ${settings.dnsServers.joinToString(", ")}",
                fontSize = 10.5.sp,
                color = ExaMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenRouting) { Text("Маршрутизация") }
                TextButton(onClick = onOpenConfig) { Text("Xray JSON") }
                TextButton(onClick = onOpenLeak) { Text("Утечки") }
            }
            Text(
                "Домены, IP, порты, geoip/geosite, порядок правил, DNS и domainStrategy. " +
                    "JSON-превью показывает итоговый конфиг до подключения.",
                fontSize = 10.sp,
                color = ExaMuted2
            )
        }

        SectionLabel("Приложения")
        ExaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (settings.allowedApps.isEmpty()) "VPN для всех приложений"
                        else "Только выбранные: ${settings.allowedApps.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        if (settings.excludedApps.isNotEmpty()) "Исключено: ${settings.excludedApps.size}"
                        else "Per-app split tunneling",
                        fontSize = 11.sp,
                        color = ExaMuted
                    )
                }
                TextButton(onClick = onOpenApps) { Text("Настроить") }
            }
        }

        SectionLabel("Система")
        ExaCard {
            Text(
                "MTU ${settings.mtu} · SOCKS 127.0.0.1:${XrayConfig.SOCKS_PORT}",
                fontSize = 11.sp,
                color = ExaMuted
            )
            SettingSwitch(
                "Защита от скриншотов",
                "Блокировать снимки экрана и превью в недавних",
                settings.blockScreenshots
            ) { onSettings(settings.copy(blockScreenshots = it)) }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
            }) { Text("Always-on VPN / kill switch") }
            Text(
                "Включи Always-on VPN для EXA, чтобы при сбое трафик не уходил напрямую.",
                fontSize = 10.sp,
                color = ExaMuted2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Плитка в шторке: потяни шторку → карандаш → перетащи «EXA» наверх. " +
                    "Один тап подключает или отключает VPN.",
                fontSize = 10.sp,
                color = ExaMuted2
            )
        }

        SectionLabel("Быстрый импорт")
        ExaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("QR-сканер", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Профиль или подписка с другого устройства", fontSize = 11.sp, color = ExaMuted)
                }
                Button(onClick = onOpenScanner) { Text("Сканировать") }
            }
            Text(
                "Работает офлайн: QR декодируется на устройстве через ZXing.",
                fontSize = 10.sp,
                color = ExaMuted2
            )
        }

        SectionLabel("О приложении")
        ExaCard {
            Spacer(Modifier.height(2.dp))
            Text("EXA // SECURE TUNNEL", fontSize = 12.sp, color = ExaMuted)
            Text("Версия ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = ExaMuted)
            Text(
                "Xray: ${XrayBridge.version()}",
                fontSize = 12.sp,
                color = ExaMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Android VPN-клиент: весь системный трафик через VLESS/Reality. " +
                    "Без рекламы, телеметрии и сторонних SDK.",
                fontSize = 11.sp,
                color = ExaMuted2
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/GretskyOleg/exa-switch"))
                    )
                }
            }) { Text("GitHub") }
        }

        ExaCard {
            Text("Как пользоваться", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "1. Профили → QR или вставь vless://…\n" +
                    "2. Выбери профиль (или включи EXA Pulse)\n" +
                    "3. Настрой сценарий и правила маршрутизации\n" +
                    "4. Подключись и разреши VPN\n" +
                    "5. Проверь утечки и Xray JSON",
                fontSize = 12.sp,
                color = ExaMuted,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ExaBg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = ExaText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, fontSize = 8.sp, color = ExaMuted2, letterSpacing = 0.9.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 14.dp, height = 2.dp)
                .background(ExaRed, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text.uppercase(),
            fontSize = 9.5.sp,
            color = ExaMuted2,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, fontSize = 11.sp, color = ExaMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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

@Composable
private fun ProfilePicker(
    label: String,
    profiles: List<Profile>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId }?.name ?: "Не выбран"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = ExaMuted, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Не выбран") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        onClick = {
                            expanded = false
                            onSelect(profile.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    profiles: List<Profile>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль") },
        text = {
            if (profiles.isEmpty()) {
                Text("Нет профилей. Добавь vless:// на вкладке «Профили».")
            } else {
                LazyColumn {
                    items(profiles, key = { it.id }) { profile ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(profile.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = profile.id == activeId,
                                onClick = { onSelect(profile.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = ExaRed)
                            )
                            Column {
                                Text(profile.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${profile.host}:${profile.port}",
                                    fontSize = 11.sp,
                                    color = ExaMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        containerColor = ExaSurface,
        titleContentColor = ExaText,
        textContentColor = ExaText
    )
}

@Composable
private fun StatusDot(on: Boolean) {
    Box(
        Modifier.size(9.dp).clip(CircleShape)
            .background(if (on) ExaGreen else ExaMuted2)
            .then(
                if (on) Modifier.border(1.dp, ExaGreen.copy(alpha = 0.5f), CircleShape)
                else Modifier
            )
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
