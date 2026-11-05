package dev.exa.app.core

import org.json.JSONArray
import org.json.JSONObject

object XrayConfig {
    const val SOCKS_PORT = 2080

    fun build(
        profile: Profile,
        settings: AppSettings = AppSettings(),
        socksPort: Int = SOCKS_PORT
    ): String {
        val outbound = JSONObject(profile.outboundJson).put("tag", "proxy")
        val direct = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject())
        val block = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put("settings", JSONObject())

        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))

        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning").put("access", "none"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(outbound).put(direct).put(block))

        val dns = settings.dnsServers.filter { it.isNotBlank() }.take(8)
        if (dns.isNotEmpty()) {
            config.put("dns", JSONObject().put("servers", JSONArray(dns)))
        }

        val rules = JSONArray()
        for (rule in settings.routingRules.take(200)) {
            val field = ruleToField(rule) ?: continue
            field.put("outboundTag", rule.action)
            rules.put(field)
        }
        if (rules.length() > 0 || settings.domainStrategy != "AsIs") {
            config.put(
                "routing",
                JSONObject()
                    .put("domainStrategy", settings.domainStrategy)
                    .put("rules", rules)
            )
        }
        return config.toString()
    }

    private fun ruleToField(rule: RouteRule): JSONObject? {
        val values = rule.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return null
        return when (rule.kind) {
            "domain" -> {
                val domains = values.mapNotNull { domainValue(it) }.distinct().take(200)
                if (domains.isEmpty()) null
                else JSONObject()
                    .put("type", "field")
                    .put("domain", JSONArray(domains))
            }
            "ip" -> {
                val ips = values.mapNotNull { ipValue(it) }.distinct().take(200)
                if (ips.isEmpty()) null
                else JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray(ips))
            }
            "port" -> {
                val ports = values.joinToString(",") { it.trim() }
                if (!Regex("^[0-9,\\-]+$").matches(ports)) null
                else JSONObject()
                    .put("type", "field")
                    .put("port", ports)
            }
            else -> null
        }
    }

    private fun domainValue(raw: String): String? {
        val value = raw.trim().lowercase()
        if (value.isEmpty() || value.length > 255) return null
        return when {
            value.startsWith("domain:") || value.startsWith("full:") ||
                value.startsWith("regexp:") || value.startsWith("geosite:") -> value
            value.contains("*") -> {
                val regex = value.replace(".", "\\.").replace("*", ".*")
                if (regex.length <= 255) "regexp:$regex" else null
            }
            else -> "domain:$value"
        }
    }

    private fun ipValue(raw: String): String? {
        val value = raw.trim().lowercase()
        if (value.isEmpty() || value.length > 255) return null
        if (value.startsWith("geoip:")) return value
        if (Regex("^[0-9a-fA-F:.]+(/[0-9]{1,3})?$").matches(value)) return value
        return null
    }
}
