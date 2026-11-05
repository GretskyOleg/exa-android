package dev.exa.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SettingsStore {
    private const val PREFS = "exa_settings"
    private const val KEY = "settings"

    fun load(context: Context): AppSettings {
        val raw = prefs(context).getString(KEY, null)
        if (raw == null) {
            val legacy = context.getSharedPreferences("exa", Context.MODE_PRIVATE)
            return AppSettings(autoConnect = legacy.getBoolean("autoConnect", true))
        }
        return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(AppSettings())
    }

    fun save(context: Context, settings: AppSettings) {
        prefs(context).edit().putString(KEY, toJson(settings).toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(s: AppSettings): JSONObject = JSONObject()
        .put("autoConnect", s.autoConnect)
        .put("smartConnect", s.smartConnect)
        .put("autoNetworkProfile", s.autoNetworkProfile)
        .put("mtu", s.mtu)
        .put("dnsServers", JSONArray(s.dnsServers))
        .put("domainStrategy", s.domainStrategy)
        .put("routingRules", JSONArray(s.routingRules.map { ruleToJson(it) }))
        .put("blockScreenshots", s.blockScreenshots)
        .put("allowedApps", JSONArray(s.allowedApps.toList()))
        .put("excludedApps", JSONArray(s.excludedApps.toList()))
        .put("wifiProfileId", s.wifiProfileId ?: JSONObject.NULL)
        .put("mobileProfileId", s.mobileProfileId ?: JSONObject.NULL)

    private fun ruleToJson(r: RouteRule): JSONObject = JSONObject()
        .put("id", r.id)
        .put("action", r.action)
        .put("kind", r.kind)
        .put("value", r.value)

    private fun fromJson(o: JSONObject): AppSettings {
        val rules = ArrayList<RouteRule>()
        val arr = o.optJSONArray("routingRules")
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), 200)) {
                val rule = arr.optJSONObject(i) ?: continue
                val action = rule.optString("action")
                val kind = rule.optString("kind")
                val value = rule.optString("value").trim()
                if (action !in setOf("proxy", "direct", "block")) continue
                if (kind !in setOf("domain", "ip", "port")) continue
                if (value.isEmpty() || value.length > 500) continue
                rules.add(
                    RouteRule(
                        id = rule.optString("id").ifBlank { UUID.randomUUID().toString() },
                        action = action,
                        kind = kind,
                        value = value
                    )
                )
            }
        }
        // Migrate the first MVP quick lists into real rules.
        if (rules.isEmpty()) {
            readList(o.optJSONArray("directDomains")).forEach {
                rules.add(RouteRule(UUID.randomUUID().toString(), "direct", "domain", it))
            }
            readList(o.optJSONArray("blockDomains")).forEach {
                rules.add(RouteRule(UUID.randomUUID().toString(), "block", "domain", it))
            }
        }
        return AppSettings(
            autoConnect = o.optBoolean("autoConnect", true),
            smartConnect = o.optBoolean("smartConnect", false),
            autoNetworkProfile = o.optBoolean("autoNetworkProfile", false),
            mtu = o.optInt("mtu", 1500).coerceIn(1280, 9000),
            dnsServers = readDns(o.optJSONArray("dnsServers")),
            domainStrategy = o.optString("domainStrategy", "AsIs")
                .takeIf { it in setOf("AsIs", "IPIfNonMatch", "IPOnDemand") } ?: "AsIs",
            routingRules = rules.take(200),
            blockScreenshots = o.optBoolean("blockScreenshots", false),
            allowedApps = readSet(o.optJSONArray("allowedApps")),
            excludedApps = readSet(o.optJSONArray("excludedApps")),
            wifiProfileId = if (o.isNull("wifiProfileId")) null
            else o.optString("wifiProfileId").takeIf { it.isNotBlank() },
            mobileProfileId = if (o.isNull("mobileProfileId")) null
            else o.optString("mobileProfileId").takeIf { it.isNotBlank() }
        )
    }

    private fun readDns(arr: JSONArray?): List<String> {
        if (arr == null) return listOf("1.1.1.1", "2606:4700:4700::1111")
        val out = LinkedHashSet<String>()
        for (i in 0 until minOf(arr.length(), 8)) {
            val value = arr.optString(i).trim()
            if (value.isNotEmpty() && value.length <= 255) out.add(value)
        }
        return if (out.isEmpty()) listOf("1.1.1.1", "2606:4700:4700::1111") else out.toList()
    }

    private fun readList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = LinkedHashSet<String>()
        for (i in 0 until minOf(arr.length(), 200)) {
            val value = arr.optString(i).trim().lowercase()
            if (value.isNotEmpty() && value.length <= 255) out.add(value)
        }
        return out.toList()
    }

    private fun readSet(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        val out = LinkedHashSet<String>()
        for (i in 0 until minOf(arr.length(), 500)) {
            val value = arr.optString(i).trim()
            if (value.isNotEmpty() && value.length <= 200) out.add(value)
        }
        return out
    }
}
