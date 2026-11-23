/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Link check. Never opens the page: it follows redirects with header-only
 * requests, checks the names against the signed threat intel, looks for brand
 * impersonation of Egyptian banks and wallets, and asks RDAP how old the domain is.
 * What leaves the phone: a header request to the link itself and the domain name to rdap.org.
 */
object UrlCheck {
    /** money: 0 = not a bank or wallet, 1 = bank/wallet (look-alike spellings are checked), 2 = also a unique name, so the same name on another domain is a fake */
    private class Brand(val n: String, val rx: Regex, val ok: List<String>, val money: Int = 0)
    private fun b(n: String, rx: String, vararg ok: String) = Brand(n, Regex(rx, RegexOption.IGNORE_CASE), ok.toList())
    private fun m(money: Int, n: String, rx: String, vararg ok: String) = Brand(n, Regex(rx, RegexOption.IGNORE_CASE), ok.toList(), money)

    private val brands = listOf(
        m(2, "البنك الأهلي المصري / NBE", "(^|[.\\-])nbe([.\\-]|$)|alahly|ahly-?bank|nbe-?egypt", "nbe.com.eg", "alahlynet.com.eg"),
        m(2, "بنك مصر / Banque Misr", "banque-?misr|bm-?online", "banquemisr.com"),
        m(2, "CIB", "(^|[.\\-])cib([.\\-]|$)|cibeg|cib-?egypt", "cibeg.com"),
        m(1, "QNB", "(^|[.\\-])qnb", "qnb.com.eg", "qnbalahli.com", "qnb.com"),
        m(2, "AAIB", "(^|[.\\-])aaib([.\\-]|$)", "aaib.com"),
        m(2, "بنك القاهرة / Banque du Caire", "banque-?du-?caire|bdc-?(online|mobile|egypt)", "bdc.com.eg", "banqueducaire.com"),
        m(2, "AlexBank", "alex-?bank", "alexbank.com"),
        m(2, "InstaPay", "insta-?pay", "instapay.eg", "instapay.com.eg"),
        m(1, "Vodafone", "vodafone|vf-?cash", "vodafone.com.eg", "vodafone.eg", "vodafone.com"),
        m(1, "Orange", "orange-?(cash|money|egypt|eg)([.\\-]|$)", "orange.eg", "orange.com"),
        m(1, "e& / Etisalat", "etisalat|eand-?(egypt|money|cash)", "etisalat.eg", "eand.com.eg", "eand.com"),
        m(1, "WE / Telecom Egypt", "telecom-?egypt|te-?data|we-?pay", "te.eg", "telecomegypt.com"),
        m(2, "Fawry", "fawry", "fawry.com", "myfawry.com", "fawrypay.com"),
        m(1, "valU", "(^|[.\\-])valu([.\\-]|$)", "valu.com.eg"),
        m(2, "البريد المصري / Egypt Post", "egypt-?post|egpost", "egyptpost.gov.eg", "egyptpost.org", "egyptpost.com.eg"),
        m(2, "Meeza", "meeza", "meezadigital.com.eg", "ebc.com.eg"),
        b("PayPal", "paypa[l1]", "paypal.com", "paypal.me"),
        b("Facebook", "facebook|faceb00k|fb-?login", "facebook.com", "fb.com", "meta.com", "fb.me", "fb.watch"),
        b("WhatsApp", "whats-?app", "whatsapp.com", "whatsapp.net", "wa.me"),
        b("Microsoft", "microsoft|office-?365|outlook-?(login|web)|onedrive", "microsoft.com", "live.com", "office.com", "microsoftonline.com", "outlook.com", "office365.com", "sharepoint.com", "windows.net", "azure.com", "msn.com"),
        b("Apple", "apple-?id|icloud", "apple.com", "icloud.com"),
        b("Netflix", "netf[l1]ix", "netflix.com"),
        b("DHL / Aramex / Bosta", "(^|[.\\-])dhl([.\\-]|$)|aramex|(^|[.\\-])bosta([.\\-]|$)", "dhl.com", "dhl.de", "aramex.com", "bosta.co"),
    )
    private val shorteners = listOf("bit.ly", "tinyurl.com", "t.co", "cutt.ly", "is.gd", "rb.gy", "shorturl.at", "goo.su", "ow.ly", "buff.ly", "t.ly", "tiny.cc", "rebrand.ly", "s.id", "v.gd", "lnkd.in", "goo.gl", "linktr.ee", "clck.ru", "surl.li", "urlz.fr")
    private val freeHosts = listOf("web.app", "firebaseapp.com", "pages.dev", "workers.dev", "github.io", "netlify.app", "vercel.app", "blogspot.com", "weebly.com", "wixsite.com", "000webhostapp.com", "glitch.me", "r2.dev", "ngrok-free.app", "ngrok.io", "duckdns.org", "trycloudflare.com", "herokuapp.com", "repl.co", "replit.app", "godaddysites.com", "webflow.io", "square.site", "sites.google.com", "forms.gle", "my.canva.site")
    private val sharedHosts = listOf("github.com", "raw.githubusercontent.com", "githubusercontent.com", "drive.google.com", "docs.google.com", "googleusercontent.com", "dropbox.com", "dropboxusercontent.com", "discord.com", "discordapp.com", "mediafire.com", "onedrive.live.com", "1drv.ms", "mega.nz", "gitlab.com", "bitbucket.org", "pastebin.com", "archive.org", "t.me", "amazonaws.com", "blob.core.windows.net", "sharepoint.com")
    private val badTlds = setOf("zip", "mov", "top", "xyz", "tk", "ml", "ga", "cf", "gq", "click", "rest", "cam", "icu", "cfd", "sbs", "buzz", "quest", "monster", "cyou", "bond", "lol", "support", "live")
    private val sld = setOf("com", "co", "net", "org", "gov", "edu", "ac", "sci", "eun", "mil", "info", "name")
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Pulls the first link out of shared text (a WhatsApp message, an SMS...). */
    fun extract(text: String): String? {
        Regex("https?://[^\\s<>\"'«»]+", RegexOption.IGNORE_CASE).find(text)?.let { return it.value.trimEnd('.', ',', ')', '!', '?', '؟', '،') }
        Regex("(?<![@\\w])(?:www\\.)?[a-z0-9][a-z0-9\\-]*(?:\\.[a-z0-9\\-]+)*\\.[a-z]{2,}(?:/[^\\s]*)?", RegexOption.IGNORE_CASE).find(text)?.let { return it.value.trimEnd('.', ',', ')') }
        return null
    }

    private fun registrable(h: String): String {
        val l = h.split('.').filter { it.isNotEmpty() }
        if (l.size <= 2) return h
        val two = l[l.size - 2] + "." + l[l.size - 1]
        if (freeHosts.contains(two)) return l.takeLast(3).joinToString(".")
        if (l.last().length == 2 && l[l.size - 2] in sld) return l.takeLast(3).joinToString(".")
        return two
    }

    private fun under(h: String, list: List<String>) = list.any { h == it || h.endsWith(".$it") }

    // ---- look-alike bank and wallet sites (works on the phone only, nothing is sent) ----
    /** Letters that scam sites swap in because they look the same: Cyrillic/Greek letters, digits, rn for m. */
    private val glyphs = mapOf('а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'і' to 'i', 'ј' to 'j', 'ѕ' to 's', 'ԁ' to 'd', 'ɡ' to 'g', 'һ' to 'h', 'ո' to 'n', 'ν' to 'v', 'α' to 'a', 'ο' to 'o', 'ι' to 'i', 'κ' to 'k', 'τ' to 't', 'ı' to 'i', 'ӏ' to 'l',
        '0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b', '@' to 'a', '$' to 's')
    internal fun skeleton(label: String): String {
        val x = label.lowercase().map { glyphs[it] ?: it }.joinToString("")
        return x.replace("rn", "m").replace("vv", "w").replace("ii", "u")
    }
    /** Edit distance where swapping two neighbouring letters counts as one change. */
    internal fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val c = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + c)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
    private fun firstLabel(domain: String) = domain.substringBefore('.')

    /** A host that is spelled like an Egyptian bank or wallet site but is not one of its official domains. */
    class LookAlike(val host: String, val brand: String, val official: String)

    /**
     * Pure check on the phone: no request is made. Finds misspellings (cibegg, banquemlsr, 1nstapay),
     * swapped look-alike letters (Cyrillic а in nbe), and the official name on another domain (fawry.net).
     */
    fun lookAlike(hostIn: String): LookAlike? {
        var h = hostIn.lowercase().trimEnd('.')
        if (h.isEmpty() || ipv4.matches(h) || h.contains(':')) return null
        if (h.split('.').any { it.startsWith("xn--") }) h = try { IDN.toUnicode(h, IDN.ALLOW_UNASSIGNED).lowercase() } catch (e: Exception) { h }
        val ascii = try { IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).lowercase() } catch (e: Exception) { h }
        val money = brands.filter { it.money > 0 }
        if (money.any { br -> under(ascii, br.ok) }) return null
        val tokens = h.split('.').dropLast(1).flatMap { it.split('-', '_') }.filter { it.length >= 4 || (it.length == 3 && it.any { c -> c.code > 127 }) }.distinct()
        for (br in money) for (off in br.ok) {
            val o = firstLabel(off)
            if (o.length < 3) continue
            for (t in tokens) {
                val sk = skeleton(t)
                val exact = t == o
                if (exact) {
                    // the official name on a different domain, e.g. instapay.net or fawry.secure-login.top
                    if (br.money == 2) return LookAlike(ascii, br.n, off)
                    continue
                }
                if (o.length < 5) { if (sk == o) return LookAlike(ascii, br.n, off); continue }
                if (sk == o) return LookAlike(ascii, br.n, off)
                val dist = distance(sk, o)
                // short names: only a swapped or replaced letter (cibeq), so ordinary words like "range" do not match "orange"
                val hit = when {
                    o.length >= 10 -> dist in 1..2
                    o.length >= 7 -> dist == 1
                    else -> dist == 1 && sk.length == o.length
                }
                if (hit) return LookAlike(ascii, br.n, off)
            }
        }
        return null
    }

    /**
     * Instant check of a link the user tapped in another app, on the phone only (no request leaves the phone).
     * Returns the bank or wallet the site pretends to be, or null.
     */
    fun offline(text: String): LookAlike? {
        var u = (extract(text) ?: text).trim().take(2048)
        if (!Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://").containsMatchIn(u)) u = "https://$u"
        val host = try { val p = URI(u.replace(" ", "%20")); p.host ?: p.rawAuthority?.substringAfterLast('@')?.substringBefore(':') } catch (e: Exception) { null } ?: return null
        val h = try { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase() } catch (e: Exception) { host.lowercase() }
        // telecom names (money 1) are also real sites abroad (vodafone.de), so only the full check flags those
        brands.firstOrNull { it.money == 2 && it.rx.containsMatchIn(h) && !under(h, it.ok) }?.let { return LookAlike(h, it.n, it.ok.first()) }
        return lookAlike(h)
    }

    /**
     * True when the host is, or resolves to, this phone, the local network or another private range.
     * Link check never connects to those: a link must not be able to make the phone talk to the router
     * or other devices at home.
     */
    internal fun isLocalTarget(host: String): Boolean {
        val h = host.trim('[', ']').lowercase()
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa") || h.endsWith(".internal")) return true
        val addrs = try { java.net.InetAddress.getAllByName(h).toList() } catch (e: Exception) { return false }
        return addrs.any { a ->
            val b = a.address
            a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress ||
                (b.size == 4 && ((b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64)) ||          // 100.64.0.0/10 carrier NAT
                (b.size == 4 && (b[0].toInt() and 0xFF) == 0) ||
                (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC)                                              // fc00::/7 unique local
        }
    }

    /** Header-only request (HEAD). Never GET: the page is not opened and nothing is submitted. */
    private fun headLocation(u: URL): Pair<Int, String?>? = try {
        val c = u.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.requestMethod = "HEAD"
        c.connectTimeout = 7000; c.readTimeout = 7000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
        val code = c.responseCode
        val loc = c.getHeaderField("Location")
        c.disconnect()
        code to loc
    } catch (e: Exception) { null }

    private fun domainAge(domain: String): Int? = try {
        val c = URL("https://rdap.org/domain/$domain").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/rdap+json")
        if (c.responseCode != 200) null else {
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val ev = o.optJSONArray("events")
            var age: Int? = null
            if (ev != null) for (i in 0 until ev.length()) {
                val e = ev.optJSONObject(i) ?: continue
                if (e.optString("eventAction") == "registration") {
                    val d = LocalDate.parse(e.optString("eventDate").take(10))
                    age = ChronoUnit.DAYS.between(d, LocalDate.now()).toInt()
                }
            }
            age
        }
    } catch (e: Exception) { null }

    /** Blocking: call from a background thread. */
    fun check(input: String, intel: IntelData, online: Boolean = true): UrlResult {
        val text = input.take(4000)
        var u = (extract(text) ?: text).trim().trim('"', '\'').take(2048)
        if (u.isEmpty()) return UrlResult(false, "badurl")
        if (!Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://").containsMatchIn(u)) u = "https://$u"
        val first: URL = try {
            val p = URI(u.replace(" ", "%20"))
            val host = p.host ?: p.rawAuthority?.substringAfterLast('@')?.substringBefore(':') ?: return UrlResult(false, "badurl")
            val ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
            if (p.scheme.lowercase() !in listOf("http", "https") || ascii.isEmpty()) return UrlResult(false, "badurl")
            URL(p.scheme.lowercase(), ascii, p.port, (p.rawPath ?: "") + (p.rawQuery?.let { "?$it" } ?: ""))
        } catch (e: Exception) { return UrlResult(false, "badurl") }
        val userInfo = try { URI(u.replace(" ", "%20")).rawUserInfo } catch (e: Exception) { null }

        val signals = ArrayList<Signal>()
        fun sig(level: String, msa: String, eg: String, en: String) = signals.add(Signal(level, L.t(msa, eg, en)))

        val chain = arrayListOf(first.toString())
        var final = first
        var reach = "skipped"
        if (online) {
            reach = "ok"
            var cur = first
            for (i in 0 until 6) {
                if (isLocalTarget(cur.host)) { reach = "local"; break }
                val r = headLocation(cur)
                if (r == null) { reach = "unreachable"; break }
                val loc = r.second ?: break
                if (r.first !in 300..399) break
                val next = try { URL(cur, loc) } catch (e: Exception) { break }
                if (next.protocol !in listOf("http", "https") || chain.contains(next.toString())) break
                chain.add(next.toString()); cur = next; final = next
            }
        }

        var brandHit = false
        val seen = HashSet<String>()
        for (link in chain) {
            val x = URL(link)
            val h = x.host.lowercase().trimEnd('.')
            if (!seen.add(h)) continue
            val isIp = ipv4.matches(h) || h.contains(':')
            val reg = if (isIp) h else registrable(h)
            val tld = h.substringAfterLast('.')

            intel.c2Domains.entries.firstOrNull { h == it.key || h.endsWith("." + it.key) }?.let {
                sig("bad", "النطاق $h مسجّل كخادم تحكم لبرمجية خبيثة (${it.value}).", "الدومين $h متسجل كسيرفر تحكم لبرمجية خبيثة (${it.value}).", "The domain $h is listed as a malware command server (${it.value}).")
            }
            if (isIp) intel.c2Ips[h]?.let { sig("bad", "العنوان $h مسجّل كخادم تحكم لبرمجية خبيثة ($it).", "العنوان $h متسجل كسيرفر تحكم لبرمجية خبيثة ($it).", "The address $h is listed as a malware command server ($it).") }
            if (intel.malwareUrls.isNotEmpty()) {
                val bare = link.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").trimEnd('/').lowercase()
                if (intel.malwareUrls.contains(bare)) sig("bad", "هذا الرابط نفسه مسجّل في URLhaus كموزّع لبرمجيات خبيثة.", "اللينك ده نفسه متسجل في URLhaus إنه بيوزّع برمجيات خبيثة.", "This exact link is listed in URLhaus as distributing malware.")
                else if (!under(h, sharedHosts)) {
                    val n = intel.malwareUrls.count { it == h || it.startsWith("$h/") || it.startsWith("$h:") }
                    if (n > 0) sig("bad", "على الموقع $h عدد $n من الروابط المسجّلة في URLhaus كموزّعة لبرمجيات خبيثة.", "الموقع $h عليه $n لينك متسجل في URLhaus إنه بيوزّع برمجيات خبيثة.", "The site $h has $n links listed in URLhaus as distributing malware.")
                }
            }
            if (isIp) sig("warn", "يتجه الرابط إلى عنوان IP مباشر ($h) لا إلى اسم موقع، والمواقع الموثوقة لا تفعل ذلك.", "اللينك رايح لعنوان IP مباشر ($h) مش لاسم موقع. المواقع المحترمة مش بتعمل كده.", "The link points to a bare IP address ($h), not a site name. Legitimate sites do not do this.")
            if (h.split('.').any { it.startsWith("xn--") }) {
                val uni = try { IDN.toUnicode(h) } catch (e: Exception) { h }
                sig("warn", "يحتوي اسم الموقع على حروف غير لاتينية قد تشبه الحروف الإنجليزية: $uni ($h)", "اسم الموقع فيه حروف غير لاتينية ممكن تتشبّه بحروف إنجليزي: $uni ($h)", "The site name uses non-Latin letters that can look like English ones: $uni ($h)")
            }
            if (link == chain.first() && !userInfo.isNullOrEmpty()) sig("warn", "يحتوي الرابط على @ قبل اسم الموقع. الجزء الذي قبل @ مكتوب لخداعك، والموقع الحقيقي هو $h.", "اللينك فيه @ قبل اسم الموقع. الجزء اللي قبل @ بيتكتب علشان يخدعك، والموقع الحقيقي هو $h.", "The link has an @ before the site name. The part before @ is there to fool you; the real site is $h.")
            if (!isIp && (h.split('.').size >= 6 || h.length > 60)) sig("warn", "اسم الموقع طويل ومتفرّع على نحو غير طبيعي، والنطاق الحقيقي هو $reg.", "اسم الموقع طويل ومتفرّع بشكل مش طبيعي. الدومين الحقيقي هو $reg.", "The site name is unusually long and nested. The real domain is $reg.")
            if (tld in badTlds) sig("note", "الامتداد .$tld رخيص ويُستخدم كثيرًا في الاحتيال، لكنه وحده ليس دليلًا.", "الامتداد .$tld رخيص وبيتستخدم كتير في النصب، وده لوحده مش دليل.", "The .$tld ending is cheap and common in scams, though not proof on its own.")
            if (under(h, shorteners)) sig("note", "رابط مختصر ($h) يُخفي العنوان الحقيقي.", "لينك مختصر ($h) مخبّي العنوان الحقيقي.", "A short link ($h) hides the real address.")
            if (!isIp && under(h, freeHosts)) sig("note", "الصفحة على استضافة مجانية ($reg)، ويستطيع أي شخص إنشاء صفحة عليها في دقائق.", "الصفحة على استضافة مجانية ($reg). أي حد يقدر يعمل صفحة عليها في دقايق.", "The page is on free hosting ($reg). Anyone can set one up in minutes.")
            if (x.port != -1 && x.port != x.defaultPort) sig("note", "يستخدم الرابط منفذًا غير معتاد (${x.port}).", "اللينك على بورت غير المعتاد (${x.port}).", "The link uses an unusual port (${x.port}).")
            if (!isIp) brands.firstOrNull { it.rx.containsMatchIn(h) && !under(h, it.ok) }?.let { br ->
                brandHit = true
                sig("warn", "يحتوي الاسم على «${br.n}» لكن الموقع ليس على النطاق الرسمي الذي نعرفه (${br.ok.joinToString("، ")}). النطاق الفعلي: $reg.", "الاسم فيه «${br.n}» بس الموقع مش على الدومين الرسمي اللي نعرفه (${br.ok.joinToString("، ")}). الدومين الفعلي: $reg.",
                    "The name contains «${br.n}» but the site is not on the official domain we know (${br.ok.joinToString(", ")}). Actual domain: $reg.")
            }
            if (!isIp && !brandHit) lookAlike(h)?.let { la ->
                brandHit = true
                sig("warn", "اسم الموقع $h مكتوب ليشبه موقع «${la.brand}» الرسمي (${la.official}) لكنه ليس هو. هذا أسلوب معروف لسرقة بيانات البنوك والمحافظ.", "اسم الموقع $h متكتب علشان يتشبّه بموقع «${la.brand}» الرسمي (${la.official}) بس هو مش هو. ده أسلوب معروف لسرقة بيانات البنوك والمحافظ.",
                    "The site name $h is spelled to look like the official «${la.brand}» site (${la.official}), but it is not. This is a known trick to steal bank and wallet logins.")
            }
        }
        val fh = final.host.lowercase()
        val fIp = ipv4.matches(fh) || fh.contains(':')
        val freg = if (fIp) fh else registrable(fh)
        if (final.protocol == "http") sig("note", "الصفحة غير مشفّرة (http)، فلا تكتب فيها كلمة مرور ولا بيانات بطاقة.", "الصفحة من غير تشفير (http). متكتبش فيها باسورد ولا بيانات كارت.", "The page is not encrypted (http). Do not type a password or card details into it.")
        Regex("\\.(apk|exe|scr|msi|bat|cmd|js|vbs|hta|lnk|iso|img|jar|ps1|dmg|pkg)$", RegexOption.IGNORE_CASE).find(final.path)?.let { m ->
            val ext = m.groupValues[1].lowercase()
            if (ext == "apk") sig("warn", "ينزّل الرابط تطبيق أندرويد (APK) من خارج المتجر، والبنوك والمحافظ لا ترسل تطبيقاتها بهذه الطريقة.", "اللينك بينزّل تطبيق أندرويد (APK) من برّه المتجر. البنوك والمحافظ مش بتبعت تطبيقاتها كده.", "The link downloads an Android app (APK) from outside the store. Banks and wallets never send their apps this way.")
            else sig("warn", "ينزّل الرابط ملف برنامج (.$ext) لا صفحة.", "اللينك بينزّل ملف برنامج (.$ext) مش صفحة.", "The link downloads a program file (.$ext), not a page.")
        }
        if (chain.size > 1 && registrable(first.host.lowercase()) != freg) sig("note", "يحوّلك الرابط إلى موقع آخر: $fh", "اللينك بيحوّلك لموقع تاني: $fh", "The link redirects you to another site: $fh")
        if (chain.size >= 4) sig("warn", "يمر الرابط بـ ${chain.size - 1} تحويلات قبل أن يصل، وهذا أسلوب لإخفاء الوجهة.", "اللينك بيعدّي على ${chain.size - 1} تحويلات قبل ما يوصل، وده أسلوب لتخبية الوجهة.", "The link goes through ${chain.size - 1} redirects, a way to hide where it ends up.")
        if (reach == "local") sig("warn", "يشير الرابط إلى جهاز على شبكتك المحلية أو على الهاتف نفسه، لذا لم يتصل به Horus Shield. لا تفتحه إلا إن كنت تعرف هذا الجهاز.", "اللينك بيشاور على جهاز على شبكتك أو على الموبايل نفسه، علشان كده Horus Shield متصلش بيه. متفتحهوش غير لو عارف الجهاز ده.", "The link points to a device on your local network or to the phone itself, so Horus Shield did not connect to it. Only open it if you know that device.")
        if (reach == "unreachable") sig("note", "لم يستجب الموقع. ربما أُغلق، فصفحات الاحتيال تُغلق بسرعة.", "الموقع مردّش. ممكن يكون اتقفل، وصفحات النصب بتتقفل بسرعة.", "The site did not answer. It may be down; scam pages are taken down quickly.")

        var age: Int? = null
        if (online && !fIp && reach != "local") {
            age = domainAge(freg)
            if (age != null) {
                if (age < 30) sig("warn", "سُجّل النطاق $freg منذ $age يوم فقط، ومواقع الاحتيال عادةً حديثة جدًا.", "الدومين $freg اتسجّل من $age يوم بس. مواقع النصب بتبقى جديدة جدًا.", "The domain $freg was registered only $age days ago. Scam sites are usually brand new.")
                else if (age < 180) sig("note", "عمر النطاق $freg هو $age يوم (أقل من 6 أشهر).", "الدومين $freg عمره $age يوم (أقل من 6 شهور).", "The domain $freg is $age days old (under 6 months).")
            }
        }

        val bad = signals.count { it.level == "bad" }
        val warn = signals.count { it.level == "warn" }
        val notes = signals.count { it.level == "note" }
        var verdict = if (warn > 0) "suspicious" else "nosigns"
        if (bad > 0 || (brandHit && (warn >= 2 || notes >= 1))) verdict = "danger"
        val order = mapOf("bad" to 0, "warn" to 1, "note" to 2)
        return UrlResult(
            ok = true, url = first.toString(), finalUrl = final.toString(), domain = freg, chain = chain, ageDays = age,
            reach = reach, verdict = verdict, signals = signals.sortedBy { order[it.level] ?: 3 },
        )
    }
}
