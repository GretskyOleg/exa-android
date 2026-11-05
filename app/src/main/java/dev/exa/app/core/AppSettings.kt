package dev.exa.app.core

data class RouteRule(
    val id: String,
    val action: String,   // proxy | direct | block
    val kind: String,     // domain | ip | port
    val value: String
)

data class AppSettings(
    val autoConnect: Boolean = true,
    val smartConnect: Boolean = false,
    val autoNetworkProfile: Boolean = false,
    val mtu: Int = 1500,
    val dnsServers: List<String> = listOf("1.1.1.1", "2606:4700:4700::1111"),
    val domainStrategy: String = "AsIs",
    val routingRules: List<RouteRule> = emptyList(),
    val blockScreenshots: Boolean = false,
    val allowedApps: Set<String> = emptySet(),
    val excludedApps: Set<String> = emptySet(),
    val wifiProfileId: String? = null,
    val mobileProfileId: String? = null
)
