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
    /**
     * [apple]: device category from Apple's Find My frame (see [appleCategory]); null when the frame does not carry one.
     * [id]: stays the same when the tracker changes its Bluetooth address (see [pickStitch]); [aka] keeps its old addresses.
     * [smooth]: the signal after smoothing (see [smoothNext]); [sep]: true when an Apple tracker is away from its owner.
     */
    data class Seen(val addr: String, val kind: Kind, val rssi: Int, val best: Int, val first: Long, val last: Long, val hits: Int, val apple: Int? = null,
                    val id: Int = 0, val smooth: Double = rssi.toDouble(), val sep: Boolean? = null, val aka: List<String> = emptyList())

    val seen = mutableStateListOf<Seen>()
    var scanning by mutableStateOf(false)
    /** "nobt", "off", "location", "perm", "fail" or null */
    var problem by mutableStateOf<String?>(null)
    var startedAt by mutableStateOf(0L)
    private var cb: ScanCallback? = null
    private val windows = HashMap<Int, ArrayDeque<Int>>()
    private var nextId = 1
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

    /**
     * Whether an Apple Find My device is away from its owner. Near its owner it sends a short frame
     * (type 0x12, length 0x02); away from its owner it sends the long frame with its key (length 0x19).
     * A tracker that is away from its owner and stays with you is the case that matters.
     */
    fun appleSeparated(d: ByteArray?): Boolean? {
        if (d == null || d.size < 2 || d[0] != 0x12.toByte()) return null
        return when (d[1]) { 0x19.toByte() -> true; 0x02.toByte() -> false; else -> null }
    }

    // ------------------------------------------------------------------ steadier readings
    /** Median of the last readings (drops single jumps), then eased toward it so the dot does not jump around. */
    fun smoothNext(prev: Double?, window: List<Int>): Double {
        if (window.isEmpty()) return prev ?: -100.0
        val med = window.sorted()[window.size / 2].toDouble()
        return if (prev == null) med else prev + 0.4 * (med - prev)
    }

    private fun push(id: Int, rssi: Int): List<Int> {
        val w = windows.getOrPut(id) { ArrayDeque() }
        w.addLast(rssi); while (w.size > 7) w.removeFirst()
        return w.toList()
    }

    /** Signal strength at about 1 metre for each kind of tracker (rough values measured on common models). */
    fun ref(kind: Kind, apple: Int?): Int = when (kind) {
        Kind.APPLE -> if (apple == 1) -59 else -62
        Kind.SAMSUNG -> -63
        Kind.TILE -> -66
        Kind.GOOGLE -> -62
        Kind.DULT -> -61
    }

    /** Rough distance in metres from the signal. Walls, bags and bodies change it, so it is only shown as a zone. */
    fun meters(ref: Int, rssi: Double): Double = Math.pow(10.0, (ref - rssi) / (10 * 2.3))
    fun meters(s: Seen): Double = meters(ref(s.kind, s.apple), s.smooth)

    /** 0 under 1 m, 1 from 1 to 3 m, 2 from 3 to 10 m, 3 farther. */
    fun zoneOf(m: Double): Int = when { m < 1.0 -> 0; m < 3.0 -> 1; m < 10.0 -> 2; else -> 3 }
    fun zone(s: Seen): Int = zoneOf(meters(s))

    /**
     * Trackers change their Bluetooth address from time to time, so one tracker can look like a new one.
     * When a new address shows up just after one of the same kind went quiet, with about the same signal,
     * treat it as the same tracker.
     */
    fun pickStitch(list: List<Seen>, kind: Kind, apple: Int?, rssi: Int, now: Long): Seen? =
        list.filter { it.kind == kind && it.hits >= 3 && (apple == null || it.apple == null || it.apple == apple) &&
            now - it.last in 3_000L..30_000L && Math.abs(rssi - it.smooth) <= 10.0 }
            .minByOrNull { Math.abs(rssi - it.smooth) }

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
        seen.clear(); windows.clear()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val k = classify(result) ?: return
                val addr = result.device?.address ?: return
                val apple = if (k == Kind.APPLE) result.scanRecord?.getManufacturerSpecificData(0x004C) else null
                val cat = appleCategory(apple)
                val sp = appleSeparated(apple)
                val rssi = result.rssi
                val now = System.currentTimeMillis()
                App.scope.launch {
                    fun upd(s: Seen, extra: Seen.() -> Seen = { this }): Seen = s.copy(rssi = rssi, smooth = smoothNext(s.smooth, push(s.id, rssi)), best = maxOf(s.best, rssi),
                        last = now, hits = s.hits + 1, apple = cat ?: s.apple, sep = sp ?: s.sep).extra()
                    val i = seen.indexOfFirst { it.addr == addr }
                    if (i >= 0) { seen[i] = upd(seen[i]); return@launch }
                    val st = pickStitch(seen, k, cat, rssi, now)
                    if (st != null) { val j = seen.indexOf(st); seen[j] = upd(st) { copy(addr = addr, aka = (st.aka + st.addr).takeLast(8)) }; return@launch }
                    val id = nextId++
                    seen.add(Seen(addr, k, rssi, rssi, now, now, 1, cat, id, smoothNext(null, push(id, rssi)), sp))
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

    private fun spotsOf(m: Map<String, List<Spot>>, s: Seen) = (s.aka + s.addr).flatMap { m[it] ?: emptyList() }.sortedBy { it.t }
    /** Same as [movingWithYou], also counting the addresses the tracker used before. */
    fun movingWithYou(s: Seen): Boolean = movesWith(spotsOf(load(), s))
    /** How many different places (about 100 m) the tracker was seen at today. */
    fun placesSeen(s: Seen): Int = spotsOf(load(), s).map { it.lat to it.lon }.distinct().size

    /** Debug builds only: fills the list with sample trackers so the smoke test can show the radar. */
    fun demo() {
        val now = System.currentTimeMillis()
        seen.clear()
        seen.add(Seen("DE:MO:00:00:00:01", Kind.APPLE, -61, -58, now - 40 * 60_000L, now, 120, 1, 1, -61.0, true))
        seen.add(Seen("DE:MO:00:00:00:02", Kind.SAMSUNG, -74, -70, now - 12 * 60_000L, now, 40, null, 2, -74.0, null))
        seen.add(Seen("DE:MO:00:00:00:03", Kind.TILE, -90, -88, now - 60_000L, now, 3, null, 3, -90.0, null))
        seen.add(Seen("DE:MO:00:00:00:04", Kind.APPLE, -80, -77, now - 2 * 60_000L, now, 9, 3, 4, -80.0, false))
        val m = load()
        m["DE:MO:00:00:00:01"] = mutableListOf(Spot(now - 50 * 60_000L, 30.044, 31.235), Spot(now - 10 * 60_000L, 30.058, 31.262))
        save(m)
        startedAt = now - 75_000L
    }

    // ------------------------------------------------------------------ how worrying is it
    /**
     * 0 to 100. Being seen at different places, away from its owner, for a long time and close to you raise it;
     * a tracker that is with its owner, or AirPods or a phone, keep it low. One reading never makes it high on its own.
     */
    fun score(s: Seen, places: Int, moving: Boolean): Int {
        var x = 10.0
        if (s.sep == true) x += 25 else if (s.sep == false) x -= 10
        x += minOf((s.last - s.first) / 60_000.0, 30.0) / 30.0 * 20
        if (zone(s) <= 1) x += 10
        if (moving) x += 40 else if (places >= 2) x += 15
        if (s.apple == 3) x = minOf(x, 20.0)
        if (s.apple == 0) x = minOf(x, 40.0)
        return x.toInt().coerceIn(0, 100)
    }

    /** 2 may be following you, 1 keep an eye on it, 0 most likely passing by. */
    fun level(score: Int): Int = when { score >= 60 -> 2; score >= 30 -> 1; else -> 0 }

    fun levelName(l: Int): String = when (l) {
        2 -> L.t("قد يكون يتتبعك", "ممكن يكون بيتتبعك", "May be following you")
        1 -> L.t("راقبه", "خلّي عينك عليه", "Keep an eye on it")
        else -> L.t("غالبًا عابر", "غالبًا معدّي", "Most likely passing by")
    }

    fun zoneName(z: Int): String = when (z) {
        0 -> L.t("أقل من متر تقريبًا", "أقل من متر تقريبًا", "Under 1 m, roughly")
        1 -> L.t("من 1 إلى 3 أمتار تقريبًا", "من 1 لـ 3 متر تقريبًا", "1 to 3 m, roughly")
        2 -> L.t("من 3 إلى 10 أمتار تقريبًا", "من 3 لـ 10 متر تقريبًا", "3 to 10 m, roughly")
        else -> L.t("أبعد من 10 أمتار", "أبعد من 10 متر", "Farther than 10 m")
    }

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
