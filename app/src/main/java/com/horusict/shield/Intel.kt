/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.time.LocalDate

data class Stalker(val name: String, val type: String, val packages: List<String>, val certs: List<String>)

/**
 * Bloom filter built by horus-intel (build_intel.py bloom()): SHA-256 of the item, h1 = bytes 0-7, h2 = bytes 8-15
 * (big-endian, unsigned), bit j = (h1 mod m + i * (h2 mod m)) mod m for i in 0 until k; byte j/8, mask 1 << (j % 8).
 * "false" is always right; "true" is wrong about 1 time in 10,000.
 */
class Bloom(val m: Long, val k: Int, private val bits: ByteArray) {
    fun has(item: String): Boolean {
        if (m <= 0 || bits.isEmpty()) return false
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(item.toByteArray(Charsets.UTF_8))
        val bb = java.nio.ByteBuffer.wrap(h)
        val a = java.lang.Long.remainderUnsigned(bb.getLong(0), m)
        val b = java.lang.Long.remainderUnsigned(bb.getLong(8), m)
        for (i in 0 until k) {
            val j = (a + i * b) % m
            if ((bits[(j ushr 3).toInt()].toInt() and (1 shl (j and 7).toInt())) == 0) return false
        }
        return true
    }
    companion object {
        fun from(o: JSONObject?): Bloom? = try {
            if (o == null || !o.has("bits")) null
            else Bloom(o.getLong("m"), o.getInt("k"), android.util.Base64.decode(o.getString("bits"), android.util.Base64.DEFAULT))
        } catch (e: Exception) { null }
    }
}
data class Kev(val cve: String, val dateAdded: LocalDate?, val vendor: String, val product: String)

class IntelData(
    val generatedAt: String,
    val source: String,
    val stalkerware: List<Stalker>,
    val c2Domains: Map<String, String>,
    val c2Ips: Map<String, String>,
    val finance: List<Pair<String, Regex>>,
    val kev: List<Kev>,
    val malwareUrls: Set<String>,
    val malwareHashes: Set<String> = emptySet(),
    /** Active phishing domains (Phishing.Database). */
    val phishing: Bloom? = null,
    /** The most visited domains (Tranco top 100,000). Only used to call a site "well known". */
    val popular: Bloom? = null,
    /** Scam links and numbers reported by users and confirmed by the Horus team. */
    val communityDomains: Set<String> = emptySet(),
    val communityPhones: Set<String> = emptySet(),
) {
    val byPackage: Map<String, Stalker> = buildMap { stalkerware.forEach { s -> s.packages.forEach { put(it, s) } } }
    val byCert: Map<String, Stalker> = buildMap { stalkerware.forEach { s -> s.certs.forEach { put(it.uppercase(), s) } } }
    val date: String get() = generatedAt.take(10)
}

/**
 * Threat intel. A snapshot ships inside the APK; newer packs are downloaded from
 * the Horus intel release and only accepted when the RSA-SHA256 signature matches
 * the Horus public key below (the same key the Windows program trusts).
 */
object Intel {
    private const val BASE = "https://github.com/medhatsaif010/horus-intel/releases/download/intel/"
    private const val MODULUS = "m7EzVTWSees5FaYnwPvpC0eAz0Rebm/zrxKy1IEVxqgYkXE+CYn59P6Oa+cjvRuwVTIgpReazDskMU9eaHIwuhP1LcPVR74LRkMOMyF1zUWKVDaB6aY4vRFfem4cWxYABecMbx5ox6jO7W/wx25WRwArKJxNfrbUctf18DAwxCbJcHOpuhC6ytAv4cwzq4F7cobkaaLDEcxT2Ot9dkpa1FUSXv31KRO4IdPYTo/zG2J+cOGjaGOS+6EIOGnx4wYtLj8cznil90bqwsrN4bmIB1YhgapUC46JBH2dyw3fo8vH8yhXM3Xi/Lyyh0OzVf8YfjPS/0sBptuO6teUh42NG8iyakTxjs5uB2lontMi2jJy44rM3tq9bIXhPeGOC7TgcYMezPnnJUaRg7FofhgAov9/4BYwpVFBB1RcLz7IJSnKg/j8tP+VuBZaadLDNKfVl8u+4nVyDCxL49DVtEzGguPk7I8Lyo422isHx3aUnZOeHWlDv0/3STNtZT+wDh8f"
    private const val EXPONENT = "AQAB"
    private const val CACHE = "intel-verified.json"

    @Volatile private var cached: IntelData? = null

    fun get(ctx: Context): IntelData {
        cached?.let { return it }
        val loaded = loadCache(ctx) ?: loadBundled(ctx)
        cached = loaded
        return loaded
    }

    private fun loadCache(ctx: Context): IntelData? = try {
        val f = File(ctx.filesDir, CACHE)
        if (f.exists()) parseSubset(JSONObject(f.readText()), "downloaded") else null
    } catch (e: Exception) { null }

    private fun loadBundled(ctx: Context): IntelData = try {
        val s = ctx.assets.open("intel-snapshot.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        parseSubset(JSONObject(s), "bundled")
    } catch (e: Exception) {
        IntelData("", "none", emptyList(), emptyMap(), emptyMap(), emptyList(), emptyList(), emptySet())
    }

    fun ageDays(i: IntelData): Int? = try {
        val d = LocalDate.parse(i.generatedAt.take(10))
        java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.now()).toInt()
    } catch (e: Exception) { null }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) { val v = a.optString(i, ""); if (v.isNotEmpty()) out.add(v) }
        return out
    }

    private fun parseSubset(o: JSONObject, source: String): IntelData {
        val st = ArrayList<Stalker>()
        o.optJSONArray("stalkerware")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                st.add(Stalker(x.optString("name", "stalkerware"), x.optString("type", ""), strings(x.optJSONArray("packages")), strings(x.optJSONArray("certificates"))))
            }
        }
        val doms = HashMap<String, String>(); val ips = HashMap<String, String>()
        o.optJSONObject("c2")?.let { c ->
            c.optJSONArray("domains")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { doms[it.optString("d").lowercase()] = it.optString("tag", "malware") } }
            c.optJSONArray("ips")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { ips[it.optString("ip")] = it.optString("tag", "malware") } }
        }
        val fin = ArrayList<Pair<String, Regex>>()
        o.optJSONArray("finance_eg")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                try { fin.add(x.optString("brand") to Regex(x.optString("rx"), RegexOption.IGNORE_CASE)) } catch (e: Exception) { }
            }
        }
        val kev = ArrayList<Kev>()
        o.optJSONArray("android_kev")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val d = try { LocalDate.parse(x.optString("dateAdded").take(10)) } catch (e: Exception) { null }
                kev.add(Kev(x.optString("cve"), d, x.optString("vendor"), x.optString("product")))
            }
        }
        val urls = HashSet<String>()
        o.optJSONArray("malware_urls")?.let { a -> for (i in 0 until a.length()) urls.add(a.optString(i).lowercase()) }
        val hashes = HashSet<String>()
        o.optJSONArray("malware_sha256")?.let { a -> for (i in 0 until a.length()) hashes.add(a.optString(i).lowercase()) }
        val cd = HashSet<String>(); val cp = HashSet<String>()
        o.optJSONObject("community")?.let { c ->
            c.optJSONArray("domains")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("d")?.lowercase()?.takeIf { it.contains('.') }?.let { cd.add(it) } }
            c.optJSONArray("phones")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.optString("p")?.takeIf { it.isNotEmpty() }?.let { cp.add(it) } }
        }
        return IntelData(o.optString("generatedAt", ""), source, st, doms, ips, fin, kev, urls, hashes, Bloom.from(o.optJSONObject("phishing")), Bloom.from(o.optJSONObject("popular")), cd, cp)
    }

    /** Full signed pack -> the small subset the phone needs. */
    private fun toSubset(full: JSONObject): JSONObject {
        val o = JSONObject()
        o.put("generatedAt", full.optString("generatedAt"))
        val st = JSONArray()
        full.optJSONArray("stalkerware")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                st.put(JSONObject().put("name", x.optString("name")).put("type", x.optString("type"))
                    .put("packages", x.optJSONArray("packages") ?: JSONArray()).put("certificates", x.optJSONArray("certificates") ?: JSONArray()))
            }
        }
        o.put("stalkerware", st)
        o.put("c2", full.optJSONObject("c2") ?: JSONObject())
        o.put("finance_eg", full.optJSONArray("finance_eg") ?: JSONArray())
        o.put("android_kev", full.optJSONObject("kev")?.optJSONArray("android") ?: JSONArray())
        o.put("malware_urls", full.optJSONObject("malware")?.optJSONArray("urls") ?: JSONArray())
        o.put("malware_sha256", full.optJSONObject("malware")?.optJSONArray("sha256") ?: JSONArray())
        full.optJSONObject("phishing")?.let { o.put("phishing", it) }
        full.optJSONObject("popular")?.let { o.put("popular", it) }
        full.optJSONObject("community")?.let { o.put("community", it) }
        return o
    }

    internal fun download(url: String, max: Int): ByteArray {
        var cur = URL(url)
        repeat(5) {
            val c = cur.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("User-Agent", "HorusShield")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw Exception("redirect without location")
                c.disconnect(); cur = URL(cur, loc)
                if (cur.protocol != "https") throw Exception("insecure redirect")
                return@repeat
            }
            if (code != 200) { c.disconnect(); throw Exception("HTTP $code") }
            c.inputStream.use { ins ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16384)
                while (true) {
                    val n = ins.read(buf); if (n < 0) break
                    out.write(buf, 0, n); if (out.size() > max) throw Exception("too large")
                }
                return out.toByteArray()
            }
        }
        throw Exception("too many redirects")
    }

    private fun verify(data: ByteArray, sigB64: String): Boolean = try {
        val spec = RSAPublicKeySpec(BigInteger(1, Base64.decode(MODULUS, Base64.DEFAULT)), BigInteger(1, Base64.decode(EXPONENT, Base64.DEFAULT)))
        val key = KeyFactory.getInstance("RSA").generatePublic(spec)
        val s = Signature.getInstance("SHA256withRSA")
        s.initVerify(key); s.update(data)
        s.verify(Base64.decode(sigB64.trim(), Base64.DEFAULT))
    } catch (e: Exception) { false }

    /** Blocking: call from a background thread. Returns an error kind or null on success. */
    fun update(ctx: Context): String? {
        return try {
            val data = download(BASE + "horus-intel.json", 40_000_000)
            val sig = String(download(BASE + "horus-intel.sig", 10_000), Charsets.UTF_8)
            if (!verify(data, sig)) return "badsig"
            var text = String(data, Charsets.UTF_8)
            if (text.startsWith("﻿")) text = text.substring(1)
            val sub = toSubset(JSONObject(text))
            val current = get(ctx)
            if (sub.optString("generatedAt") < current.generatedAt) return null
            File(ctx.filesDir, CACHE).writeText(sub.toString())
            cached = parseSubset(sub, "downloaded")
            Prefs.putLong("intelChecked", System.currentTimeMillis())
            null
        } catch (e: Exception) { "net" }
    }
}
