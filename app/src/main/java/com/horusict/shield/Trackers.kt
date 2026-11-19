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
    data class Seen(val addr: String, val kind: Kind, val rssi: Int, val best: Int, val first: Long, val last: Long, val hits: Int)

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
                val now = System.currentTimeMillis()
                App.scope.launch {
                    val i = seen.indexOfFirst { it.addr == addr }
                    if (i < 0) seen.add(Seen(addr, k, result.rssi, result.rssi, now, now, 1))
                    else { val s = seen[i]; seen[i] = s.copy(rssi = result.rssi, best = maxOf(s.best, result.rssi), last = now, hits = s.hits + 1) }
                }
            }
            override fun onScanFailed(errorCode: Int) { App.scope.launch { problem = "fail"; scanning = false } }
        }
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (e: Exception) { problem = "fail"; return }
        cb = callback; scanning = true; startedAt = System.currentTimeMillis()
        timer?.cancel()
        timer = App.scope.launch { delay(3 * 60_000L); stop(ctx) }
    }

    @SuppressLint("MissingPermission")
    fun stop(ctx: Context) {
        timer?.cancel(); timer = null
        val c = cb ?: run { scanning = false; return }
        try { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner?.stopScan(c) } catch (e: Exception) { }
        cb = null; scanning = false
    }

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
