/*
 * Horus Sec Mobile
 * Copyright (c) 2026 Medhat Seif Abd El Hamid. All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horussec.mobile

import org.json.JSONArray
import org.json.JSONObject

/** The last scans, newest first, for the "recent scans" table on the home screen. */
object History {
    data class Entry(val at: Long, val score: Int, val verdict: String, val deep: Boolean, val auto: Boolean, val apps: Int)

    private const val KEY = "history"
    private const val MAX = 10

    fun list(): List<Entry> = try {
        val a = JSONArray(Prefs.str(KEY) ?: "[]")
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            Entry(it.optLong("at"), it.optInt("score"), it.optString("verdict"), it.optBoolean("deep"), it.optBoolean("auto"), it.optInt("apps"))
        }
    } catch (e: Exception) { emptyList() }

    @Synchronized
    fun add(r: ScanResult, auto: Boolean) {
        val all = listOf(Entry(r.atMillis, r.score, r.verdict, r.deep, auto, r.appCount)) + list().filter { it.at != r.atMillis }
        val a = JSONArray()
        all.take(MAX).forEach {
            a.put(JSONObject().put("at", it.at).put("score", it.score).put("verdict", it.verdict).put("deep", it.deep).put("auto", it.auto).put("apps", it.apps))
        }
        Prefs.put(KEY, a.toString())
    }
}
