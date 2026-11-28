/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Everything Horus Studio can change without touching the code. These files ship inside the app
 * (assets/studio/) and are edited from horusict.com/studio, then reach people with the next update:
 *  - texts.json     text edits, keyed by the original English text
 *  - features.json  which features are shown, and the order of the home tiles
 *  - helplines.json the help numbers on the "Someone is watching me" screen
 *  - egapps.json    official Egyptian bank and wallet apps (optional; the built-in list is used without it)
 * The urgent alert is the one thing that arrives at once, from horusict.com/alert.json.
 */
object Studio {
    data class Helpline(val number: String, val msa: String, val eg: String, val en: String)
    data class Alert(val id: String, val msa: String, val eg: String, val en: String, val level: String, val until: LocalDate?)

    private var features: Map<String, Boolean> = emptyMap()
    var homeOrder: List<String> = listOf("link", "bank", "whatsapp", "report", "repair", "trackers"); private set
    var helplines: List<Helpline> = emptyList(); private set
    var brands: List<EgApps.Brand>? = null; private set
    var alert by mutableStateOf<Alert?>(null)

    fun on(feature: String): Boolean = features[feature] ?: true

    private fun asset(ctx: Context, name: String): String? = try {
        ctx.assets.open("studio/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) { null }

    fun load(ctx: Context) {
        asset(ctx, "texts.json")?.let { s ->
            try {
                // an empty field keeps the text written in the code
                fun f(o: JSONObject, k: String): String? = o.optString(k, "").takeIf { it.isNotBlank() }
                val o = JSONObject(s)
                L.overrides = o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { v -> k to arrayOf(f(v, "msa"), f(v, "eg"), f(v, "en")) } }.toMap()
            } catch (e: Exception) { }
        }
        asset(ctx, "features.json")?.let { s ->
            try {
                val o = JSONObject(s)
                o.optJSONObject("features")?.let { f -> features = f.keys().asSequence().associateWith { f.optBoolean(it, true) } }
                o.optJSONArray("homeOrder")?.let { a -> homeOrder = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } }
            } catch (e: Exception) { }
        }
        asset(ctx, "helplines.json")?.let { s ->
            try {
                val a = JSONArray(s)
                helplines = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Helpline(it.optString("number"), it.optString("msa"), it.optString("eg"), it.optString("en")) } }
                    .filter { it.number.matches(Regex("^[0-9+]{3,15}$")) }
            } catch (e: Exception) { }
        }
        asset(ctx, "egapps.json")?.let { s ->
            try {
                val a = JSONArray(s)
                brands = (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { b ->
                        val pk = b.optJSONArray("packages") ?: JSONArray()
                        val name = b.optString("name")
                        // a blank pattern would match every app; fall back to the brand's own name
                        val match = b.optString("match").ifBlank { Regex.escape(name) }
                        if (name.isBlank() || pk.length() == 0) null
                        else {
                            val pn = b.optJSONArray("pins") ?: JSONArray()
                            val pins = (0 until pn.length()).map { pn.optString(it).replace(":", "").uppercase() }.filter { it.matches(Regex("^[0-9A-F]{40}$")) }.toSet()
                            EgApps.Brand(name, b.optString("nameAr"), (0 until pk.length()).map { pk.optString(it) }.toSet(), Regex(match, RegexOption.IGNORE_CASE), pins)
                        }
                    }
                }.ifEmpty { null }
            } catch (e: Exception) { brands = null }
        }
        Prefs.str("alertCache")?.let { parseAlert(it)?.let { a -> if (!expired(a)) alert = a } }
    }

    // ------------------------------------------------------------------ urgent alert
    private const val ALERT_URL = "https://horusict.com/alert.json"

    private fun expired(a: Alert) = a.until != null && LocalDate.now().isAfter(a.until)

    fun dismissed(a: Alert) = Prefs.str("alertDismissed") == a.id
    fun dismiss(a: Alert) { Prefs.put("alertDismissed", a.id); alert = null }

    internal fun parseAlert(s: String): Alert? = try {
        val o = JSONObject(s)
        if (!o.optBoolean("active", false)) null else {
            val id = o.optString("id"); val t = o.optJSONObject("text") ?: JSONObject()
            val until = try { LocalDate.parse(o.optString("until")) } catch (e: Exception) { null }
            val level = if (o.optString("level") == "danger") "danger" else "warn"
            // plain text only, and short: the alert can never carry links or markup
            fun clean(x: String) = x.replace(Regex("[\\u0000-\\u001F]"), " ").take(280)
            if (id.isEmpty() || t.optString("en").isEmpty()) null
            else Alert(id.take(40), clean(t.optString("msa")), clean(t.optString("eg")), clean(t.optString("en")), level, until)
        }
    } catch (e: Exception) { null }

    /** Asks horusict.com for an urgent alert, at most every 30 minutes. Background thread only. */
    fun fetchAlert(force: Boolean = false): Alert? {
        if (!force && System.currentTimeMillis() - Prefs.long("alertChecked") < 30 * 60_000L) return alert
        var c: HttpURLConnection? = null
        return try {
            c = URL(ALERT_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "HorusShield/" + BuildConfig.VERSION_NAME)
            if (c.responseCode != 200) return alert
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(8192) }
            Prefs.putLong("alertChecked", System.currentTimeMillis())
            val a = parseAlert(body)
            Prefs.put("alertCache", if (a == null) null else body)
            a?.takeIf { !expired(it) }
        } catch (e: Exception) { alert } finally { c?.disconnect() }
    }

    fun text(a: Alert) = when (L.lang) { "ar" -> a.msa.ifEmpty { a.en }; "eg" -> a.eg.ifEmpty { a.msa.ifEmpty { a.en } }; else -> a.en }
}
