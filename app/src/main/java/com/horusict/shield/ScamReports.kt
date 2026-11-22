/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Scam links and numbers reported by the user. Kept on the phone (the link check warns about
 * links you reported), and sent to Horus ICT only when the user taps Send, so they can be
 * reviewed and added to the threat intel for everyone. What is sent: the link or number,
 * where it arrived (WhatsApp, SMS...), the app version and language. No name, account or device ID.
 */
object ScamReports {
    private const val ENDPOINT = "https://horusict.com/api/report"
    private const val KEY = "scamReports"

    data class Rep(val at: Long, val kind: String, val value: String, val channel: String, val sent: Boolean)

    fun list(): List<Rep> = try {
        val a = JSONArray(Prefs.str(KEY) ?: "[]")
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Rep(it.optLong("at"), it.optString("kind"), it.optString("value"), it.optString("channel"), it.optBoolean("sent")) } }
    } catch (e: Exception) { emptyList() }

    private fun save(l: List<Rep>) {
        val a = JSONArray()
        l.takeLast(100).forEach { r -> a.put(JSONObject().put("at", r.at).put("kind", r.kind).put("value", r.value).put("channel", r.channel).put("sent", r.sent)) }
        Prefs.put(KEY, a.toString())
    }

    /** Cleans what the user typed. null = not a link or phone number we can use. */
    /** One form for Egyptian numbers, so +20 10..., 0020 10... and 010... match. Same rule as Horus Studio. */
    fun canonPhone(p: String): String {
        var d = p.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.drop(2)
        if (d.startsWith("20") && d.length == 12) d = "0" + d.drop(2)
        return d
    }

    fun normalize(kind: String, raw: String): String? {
        val t = raw.trim()
        return when (kind) {
            "link" -> {
                val s = if (Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else "https://$t"
                if (s.length > 2048) return null
                val host = try { URI(s).host } catch (e: Exception) { null } ?: return null
                if (!host.contains('.') || host.length > 253) null else s
            }
            "phone" -> {
                val d = t.filter { it.isDigit() || it == '+' }
                val digits = d.count { it.isDigit() }
                if (digits in 7..15) d else null
            }
            else -> null
        }
    }

    fun hostOf(s: String): String? = try { URI(s).host?.lowercase()?.removePrefix("www.") } catch (e: Exception) { null }

    fun add(kind: String, value: String, channel: String): Rep {
        val r = Rep(System.currentTimeMillis(), kind, value, channel, false)
        save(list().filterNot { it.kind == kind && it.value == value } + r)
        return r
    }

    fun remove(r: Rep) = save(list().filterNot { it.at == r.at && it.value == r.value })

    /** true when the user reported this link's domain before */
    fun reportedDomain(domain: String): Boolean {
        val d = domain.lowercase().removePrefix("www.")
        return list().any { it.kind == "link" && hostOf(it.value) == d }
    }

    /** Sends every report not sent yet. Returns how many were accepted. Runs on a background thread. */
    fun sendPending(): Int {
        var ok = 0
        val all = list().toMutableList()
        for (i in all.indices) {
            val r = all[i]
            if (r.sent) continue
            if (send(r)) { all[i] = r.copy(sent = true); ok++ } else break
        }
        if (ok > 0) save(all)
        return ok
    }

    private fun send(r: Rep): Boolean {
        var c: HttpURLConnection? = null
        return try {
            c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10_000; c.readTimeout = 10_000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "HorusShield/" + BuildConfig.VERSION_NAME)
            val body = JSONObject().put("kind", r.kind).put("value", r.value).put("channel", r.channel)
                .put("app", BuildConfig.VERSION_NAME).put("lang", L.lang).toString()
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            c.responseCode in 200..299
        } catch (e: Exception) { false } finally { c?.disconnect() }
    }
}
