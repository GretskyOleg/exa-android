package dev.exa.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SubscriptionStore {
    private const val PREFS = "exa_subscriptions"
    private const val KEY = "subscriptions"

    fun load(context: Context): List<Subscription> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until minOf(arr.length(), 50)).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, subscriptions: List<Subscription>) {
        val arr = JSONArray()
        subscriptions.take(50).forEach { arr.put(toJson(it)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(context: Context, subscription: Subscription) {
        val list = load(context).toMutableList()
        val index = list.indexOfFirst { it.id == subscription.id || it.url == subscription.url }
        if (index >= 0) list[index] = subscription else list.add(subscription)
        save(context, list)
    }

    fun remove(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(s: Subscription): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("url", s.url)
        .put("updatedAt", s.updatedAt)
        .put("upload", s.upload)
        .put("download", s.download)
        .put("total", s.total)
        .put("expire", s.expire)
        .put("lastError", s.lastError ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject): Subscription? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val url = o.optString("url").takeIf { it.startsWith("https://") } ?: return null
        return Subscription(
            id = id,
            name = o.optString("name").take(64).ifBlank { "Подписка" },
            url = url.take(2048),
            updatedAt = o.optLong("updatedAt", 0).coerceAtLeast(0),
            upload = o.optLong("upload", 0).coerceAtLeast(0),
            download = o.optLong("download", 0).coerceAtLeast(0),
            total = o.optLong("total", 0).coerceAtLeast(0),
            expire = o.optLong("expire", 0).coerceAtLeast(0),
            lastError = if (o.isNull("lastError")) null
            else o.optString("lastError").takeIf { it.isNotBlank() }
        )
    }
}
