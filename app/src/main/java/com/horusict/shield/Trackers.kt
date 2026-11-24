/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Finds Bluetooth item trackers near the phone (AirTag and other Find My devices, Samsung SmartTag,
 * Tile, Google Find My Device trackers, and trackers that follow the unwanted-tracker standard).
 * Listens only: it never connects to a tracker and keeps nothing after the screen closes.
 */
object Trackers {
    enum class Kind { APPLE, SAMSUNG, TILE, GOOGLE, DULT }
    /** [apple]: device category from Apple's Find My frame (see [appleCategory]); null when the frame does not carry one. */
    data class Seen(val addr: String, val kind: Kind, val rssi: Int, val best: Int, val first: Long, val last: Long, val hits: Int, val apple: Int? = null)

    val seen = mutableStateListOf<Seen>()
    var scanning by mutableStateOf(false)
    /** "nobt", "off", "location", "perm", "fail" or null */
    var problem by mutableStateOf<String?>(null)
    var startedAt by mutableStateOf(0L)
    private var cb: ScanCallback? = null
    private var timer: Job? = null

    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermissions(ctx: Context) = permissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    private fun uuid16(x: Int): ParcelUuid = ParcelUuid.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", x))
    private val SAMSUNG = listOf(uuid16(0xFD5A), uuid16(0xFD59))
    private val TILE = listOf(uuid16(0xFEED), uuid16(0xFEEC))
    private val DULT = uuid16(0xFCB2)
    private val EDDYSTONE = uuid16(0xFEAA)

    /**
     * Apple's Find My "offline finding" frame (company 0x004C): type 0x12, length 0x19, then 25 bytes whose
     * first byte is a status byte. Researchers observed (status >> 4) & 3 as: 0 Apple device, 1 AirTag,
     * 2 other Find My accessory, 3 AirPods. Only that exact frame carries it; anything else stays unknown.
     * It is a hint from observed behaviour, not a guarantee.
     */
    fun appleCategory(d: ByteArray?): Int? {
        if (d == null || d.size < 2 + 25 || d[0] != 0x12.toByte() || d[1] != 0x19.toByte()) return null
        return (d[2].toInt() shr 4) and 0x03
    }

    fun classify(r: ScanResult): Kind? {
        val rec = r.scanRecord ?: return null
        // Apple Find My network "offline finding" advertisement: company 0x004C, type 0x12
        rec.getManufacturerSpecificData(0x004C)?.let { if (it.isNotEmpty() && it[0] == 0x12.toByte()) return Kind.APPLE }
        val ids = (rec.serviceUuids ?: emptyList()) + (rec.serviceData?.keys ?: emptySet())
        if (ids.any { it in SAMSUNG }) return Kind.SAMSUNG
        if (ids.any { it in TILE }) return Kind.TILE
        if (DULT in ids) return Kind.DULT
        // Google Find My Device network trackers use Eddystone frame 0x40 / 0x41
        rec.getServiceData(EDDYSTONE)?.let { if (it.isNotEmpty() && (it[0] == 0x40.toByte() || it[0] == 0x41.toByte())) return Kind.GOOGLE }
        return null
    }

    private fun locationOn(ctx: Context): Boolean = try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) { true }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (scanning) return
        problem = null
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { problem = "nobt"; return }
        if (!hasPermissions(ctx)) { problem = "perm"; return }
        if (!adapter.isEnabled) { problem = "off"; return }
        if (!locationOn(ctx)) { problem = "location"; return }
        val scanner = adapter.bluetoothLeScanner ?: run { problem = "off"; return }
        seen.clear()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val k = classify(result) ?: return
                val addr = result.device?.address ?: return
                val cat = if (k == Kind.APPLE) appleCategory(result.scanRecord?.getManufacturerSpecificData(0x004C)) else null
                val now = System.currentTimeMillis()
                App.scope.launch {
                    val i = seen.indexOfFirst { it.addr == addr }
                    if (i < 0) seen.add(Seen(addr, k, result.rssi, result.rssi, now, now, 1, cat))
                    else { val s = seen[i]; seen[i] = s.copy(rssi = result.rssi, best = maxOf(s.best, result.rssi), last = now, hits = s.hits + 1, apple = cat ?: s.apple) }
                }
            }
            override fun onScanFailed(errorCode: Int) { App.scope.launch { problem = "fail"; scanning = false } }
        }
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (e: Exception) { problem = "fail"; return }
        cb = callback; scanning = true; startedAt = System.currentTimeMillis()
        here = null
        fixLocation(ctx)
        timer?.cancel()
        timer = App.scope.launch { delay(3 * 60_000L); stop(ctx) }
    }

    @SuppressLint("MissingPermission")
    fun stop(ctx: Context) {
        timer?.cancel(); timer = null
        val c = cb ?: run { scanning = false; return }
        try { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner?.stopScan(c) } catch (e: Exception) { }
        cb = null; scanning = false
        remember(ctx)
    }

    // ------------------------------------------------------------------ "moving with you"
    // One scan only shows what is near. To say a tracker may be moving with you, the same tracker must be
    // seen in scans at different places over time. For that the phone keeps, on the phone only and for
    // 24 hours, a rough place (about 100 m) for each tracker it saw. Nothing is sent anywhere.
    data class Spot(val t: Long, val lat: Double, val lon: Double)
    private const val LOG = "trackerPlaces"
    private const val KEEP = 24 * 3600_000L
    private var here: Pair<Double, Double>? = null

    @SuppressLint("MissingPermission")
    private fun fixLocation(ctx: Context) {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val recent = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .filter { System.currentTimeMillis() - it.time < 10 * 60_000L }.maxByOrNull { it.time }
            recent?.let { here = it.latitude to it.longitude }
            if (Build.VERSION.SDK_INT >= 30) {
                val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                lm.getCurrentLocation(prov, null, ctx.mainExecutor) { l -> if (l != null) here = l.latitude to l.longitude }
            }
        } catch (e: Exception) { }
    }

    private fun round3(x: Double) = Math.round(x * 1000.0) / 1000.0

    private fun load(): MutableMap<String, MutableList<Spot>> {
        val out = HashMap<String, MutableList<Spot>>()
        try {
            val o = org.json.JSONObject(Prefs.str(LOG) ?: return out)
            val cut = System.currentTimeMillis() - KEEP
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                val l = (0 until a.length()).map { val x = a.getJSONArray(it); Spot(x.getLong(0), x.getDouble(1), x.getDouble(2)) }.filter { it.t > cut }
                if (l.isNotEmpty()) out[k] = l.toMutableList()
            }
        } catch (e: Exception) { }
        return out
    }

    private fun save(m: Map<String, List<Spot>>) {
        val o = org.json.JSONObject()
        m.entries.sortedByDescending { e -> e.value.maxOf { it.t } }.take(60).forEach { (k, l) ->
            o.put(k, org.json.JSONArray().apply { l.takeLast(20).forEach { put(org.json.JSONArray().put(it.t).put(it.lat).put(it.lon)) } })
        }
        Prefs.put(LOG, if (o.length() == 0) null else o.toString())
    }

    /** After a scan: remember where each tracker that was really there (seen more than once) was seen. */
    private fun remember(ctx: Context) {
        val p = here ?: return
        val m = load()
        val now = System.currentTimeMillis()
        seen.filter { it.hits >= 2 && it.apple != 3 }.forEach { s ->
            m.getOrPut(s.addr) { mutableListOf() }.add(Spot(now, round3(p.first), round3(p.second)))
        }
        save(m)
    }

    /** Wipe the saved places (the "clear" button on the trackers screen). */
    fun forget() { Prefs.put(LOG, null) }

    private fun meters(a: Spot, b: Spot): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }

    /** Seen at two places at least 400 m apart, at least 15 minutes apart. */
    fun movesWith(spots: List<Spot>): Boolean {
        for (i in spots.indices) for (j in i + 1 until spots.size) {
            val a = spots[i]; val b = spots[j]
            if (Math.abs(b.t - a.t) >= 15 * 60_000L && meters(a, b) >= 400.0) return true
        }
        return false
    }

    fun movingWithYou(addr: String): Boolean = load()[addr]?.let { movesWith(it) } == true
    fun placesSeen(addr: String): Int = load()[addr]?.size ?: 0

    /** What to call a device on screen: the Find My category when the frame carries one, else the network. */
    fun label(s: Seen): String = if (s.kind == Kind.APPLE) when (s.apple) {
        1 -> L.t("على الأرجح AirTag", "غالبًا AirTag", "Likely AirTag")
        2 -> L.t("ملحق من شبكة Find My، نوعه غير معروف", "حاجة من شبكة Find My، نوعها مش معروف", "Find My accessory, model unknown")
        3 -> L.t("على الأرجح سماعات AirPods", "غالبًا سماعات AirPods", "Likely AirPods")
        else -> L.t("جهاز Apple، نوعه غير معروف", "جهاز Apple، نوعه مش معروف", "Apple device, model unknown")
    } else kindName(s.kind)

    fun kindName(k: Kind): String = when (k) {
        Kind.APPLE -> L.t("جهاز من شبكة Apple Find My (مثل AirTag)", "جهاز من شبكة Apple Find My (زي AirTag)", "Apple Find My device (such as an AirTag)")
        Kind.SAMSUNG -> L.t("Samsung SmartTag", "Samsung SmartTag", "Samsung SmartTag")
        Kind.TILE -> "Tile"
        Kind.GOOGLE -> L.t("جهاز تتبع من شبكة Google Find My Device", "جهاز تتبع من شبكة Google Find My Device", "Google Find My Device tracker")
        Kind.DULT -> L.t("جهاز تتبع (معيار التنبيه من أجهزة التتبع)", "جهاز تتبع (معيار التنبيه من أجهزة التتبع)", "Tracker (unwanted-tracker standard)")
    }

    fun distance(rssi: Int): String = when {
        rssi >= -55 -> L.t("قريب جدًا منك", "قريب منك جدًا", "Very close to you")
        rssi >= -70 -> L.t("قريب", "قريب", "Nearby")
        rssi >= -85 -> L.t("في المكان نفسه", "في نفس المكان", "In the same area")
        else -> L.t("بعيد", "بعيد", "Far")
    }
}
