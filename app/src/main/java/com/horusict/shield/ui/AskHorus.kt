/*
 * Horus Shield
 * Copyright (c) 2026 Horus ICT (horusict.com). All rights reserved.
 * Proprietary and confidential. Unauthorized copying, modification or distribution is prohibited.
 */
package com.horusict.shield.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horusict.shield.App
import com.horusict.shield.L
import com.horusict.shield.R
import com.horusict.shield.Screen
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Ask Horus": an offline assistant. Answers come from a curated guide shipped with the app
 * (assets/askhorus.json, the same guide as on horusict.com). Nothing typed here leaves the phone.
 */
object AskKB {
    class Item(val id: String, val hide: Boolean, val opt: List<String>, val q: Map<String, String>, val a: Map<String, List<String>>, val listy: Boolean, val words: Set<String>, val title: Set<String>)

    var items: List<Item> = emptyList(); private set
    var starters: List<String> = emptyList(); private set
    private var byId: Map<String, Item> = emptyMap()
    private var df: Map<String, Int> = emptyMap()

    fun load(ctx: Context) {
        if (items.isNotEmpty()) return
        try {
            val o = JSONObject(ctx.assets.open("askhorus.json").bufferedReader().use { it.readText() })
            starters = o.getJSONArray("starters").let { a -> List(a.length()) { a.getString(it) } }
            val arr = o.getJSONArray("items")
            items = List(arr.length()) { i ->
                val it = arr.getJSONObject(i)
                val q = it.getJSONObject("q"); val a = it.getJSONObject("a")
                val qm = listOf("en", "ar", "eg").associateWith { l -> q.optString(l, q.optString("ar")) }
                var listy = false
                val am = listOf("en", "ar", "eg").associateWith { l ->
                    when (val v = a.opt(l) ?: a.opt("ar")) {
                        is JSONArray -> { listy = true; List(v.length()) { k -> v.getString(k) } }
                        else -> listOf(v.toString())
                    }
                }
                val opt = it.optJSONArray("opt")?.let { x -> List(x.length()) { k -> x.getString(k) } } ?: emptyList()
                val words = toks(it.optString("k") + " " + qm.values.joinToString(" ")).map(::stem).toSet()
                val title = toks(qm.values.joinToString(" ")).map(::stem).toSet()
                Item(it.getString("id"), it.optInt("hide") == 1, opt, qm, am, listy, words, title)
            }
            byId = items.associateBy { it.id }
            val d = HashMap<String, Int>()
            items.filter { !it.hide }.forEach { it.words.forEach { w -> d[w] = (d[w] ?: 0) + 1 } }
            df = d
        } catch (e: Exception) { }
    }

    fun get(id: String) = byId[id]

    private val stop = setOf("the","a","an","is","my","i","to","it","do","how","what","of","and","in","on","me","can","for","with",
        "في","من","على","عن","ان","او","و","هل","انا","ده","دي","هو","هي","ما","اللي","الى","الي","لو","مع","كده","عايز","عاوز",
        "app","apps","horus","shield","برنامج","البرنامج","تطبيق","التطبيق","حورس")

    fun norm(s: String): String = s.lowercase()
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace(Regex("[إأآٱ]"), "ا").replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace(Regex("[^a-z0-9\\u0621-\\u064A\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun toks(s: String) = norm(s).split(" ").filter { it.length > 1 && it !in stop }

    private fun stem(w0: String): String {
        var w = w0
        if (w.isNotEmpty() && w[0] in 'ء'..'ي') {
            w = w.replace(Regex("^(وال|بال|كال|فال|لل|ال)"), "").replace(Regex("^(و|ف|ب)(?=..)"), "")
            w = w.replace(Regex("(ات|ين|ون|ها|هم|ني|ك)$"), "")
        } else w = w.replace(Regex("(ing|ed|es|s)$"), "")
        return w
    }

    private fun idf(w: String): Double { val n = items.count { !it.hide }.toDouble(); return Math.log(1 + n / (df[w] ?: 1)) }

    fun search(text: String): List<Item> {
        val qt = toks(text).map(::stem).filter { it.isNotEmpty() }
        if (qt.isEmpty()) return emptyList()
        return items.filter { !it.hide }.map { e ->
            var s = 0.0
            for (q in qt) {
                var best = 0.0
                for (w in e.words) {
                    var m = when {
                        w == q -> 3.0
                        q.length > 3 && w.length > 3 && (w.startsWith(q) || q.startsWith(w)) -> 2.0
                        q.length > 3 && w.length > 3 && (w.contains(q) || q.contains(w)) -> 1.0
                        else -> 0.0
                    }
                    if (m > 0) { m *= idf(w); if (w in e.title) m *= 1.3 }
                    if (m > best) best = m
                }
                s += best
            }
            e to s
        }.filter { it.second >= 3.2 }.sortedByDescending { it.second }.map { it.first }
    }

    /** The guide topic that explains a report finding, by its key. */
    fun topicFor(key: String): String? {
        val k = key.substringBefore(':')
        return when {
            k == "stalkerware" || k == "c2-conn" || k == "uploads" || k == "hidden-app" || k == "spoofed-installer" -> "spyware-found"
            k == "fake-finance" || k == "banker-pattern" -> "fake-bank-app"
            k == "accessibility" -> "accessibility"
            k == "device-admin" || k == "device-owner" -> "device-admin"
            k == "notification-access" -> "notif-access"
            k == "no-lock" -> "no-lock"
            k == "android-patch" || k == "android-kev" -> "old-update"
            k == "user-ca" || k == "proxy" -> "cert-proxy"
            k == "dns-hijack" -> "wifi"
            k == "adb-wifi" -> "dev-options"
            k == "sideloaded-apps" || k == "install-unknown-apps" || k == "debug-signed" || k == "fake-system" -> "sideloaded"
            k == "known-malware" || k == "preloaded-malware" -> "remove-app"
            k.startsWith("root") || k == "rooted" || k == "hooking" -> "root"
            else -> null
        }
    }
}

sealed class AskMsg {
    class User(val text: String) : AskMsg()
    class Bot(val id: String?, val text: String? = null) : AskMsg()
    class Chips(val ids: List<String>, val label: Boolean) : AskMsg()
}

object Ask {
    val log = mutableStateListOf<AskMsg>()
    var back by mutableStateOf(Screen.TOOLS)

    fun open(from: Screen, topic: String? = null) {
        AskKB.load(App.ctx)
        back = from
        if (log.isEmpty()) greet()
        if (topic != null) AskKB.get(topic)?.let { pick(it.id) }
        App.screen = Screen.ASK
    }
    fun greet() { log.clear(); log.add(AskMsg.Bot(null)); log.add(AskMsg.Chips(AskKB.starters, false)) }
    fun pick(id: String) {
        val it = AskKB.get(id) ?: return
        log.add(AskMsg.User(it.q[L.lang] ?: it.q.getValue("en"))); answer(it, emptyList())
    }
    private fun answer(it: AskKB.Item, extra: List<String>) {
        log.add(AskMsg.Bot(it.id))
        val more = (it.opt + extra.filter { e -> e !in it.opt }).take(4)
        if (more.isNotEmpty()) log.add(AskMsg.Chips(more, it.id != "spyware-found"))
    }
    fun ask(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        log.add(AskMsg.User(t))
        val r = AskKB.search(t)
        if (r.isNotEmpty()) answer(r[0], r.drop(1).take(2).map { it.id })
        else { log.add(AskMsg.Bot(null, "none")); log.add(AskMsg.Chips(listOf("sus-link", "read-report", "install", "contact"), false)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen() {
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(Ask.log.size) {
        // keep the newest question at the top of the view, so the answer reads from its start
        val i = Ask.log.indexOfLast { it is AskMsg.User }
        if (i >= 0) list.animateScrollToItem(i) else if (Ask.log.isNotEmpty()) list.scrollToItem(0)
    }
    Backdrop {
        TopBar(L.t("اسأل حورس", "اسأل حورس", "Ask Horus"), { App.screen = Ask.back }) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { Ask.greet() }, contentAlignment = Alignment.Center) {
                Text(L.t("جديد", "جديد", "New"), color = C.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(Ask.log) { _, m ->
                when (m) {
                    is AskMsg.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(m.text, color = C.GoldLight, fontSize = 15.sp,
                            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(C.Gold.copy(alpha = 0.14f)).border(1.dp, C.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                    is AskMsg.Bot -> {
                        val item = m.id?.let { AskKB.get(it) }
                        Column(Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(14.dp)).background(C.Surface).border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when {
                                item == null && m.text == "none" -> Text(L.t("ليس لديّ إجابة عن هذا بعد. قد تفيدك هذه المواضيع:", "معنديش إجابة على ده لسه. المواضيع دي ممكن تفيدك:", "I don't have an answer for that yet. These topics may help:"), color = C.Text, fontSize = 15.sp)
                                item == null -> Text(L.t("أهلًا. اسألني عن Horus Shield أو عن حماية نفسك على الإنترنت، أو اختر موضوعًا.", "أهلًا. اسألني عن Horus Shield أو إزاي تحمي نفسك على النت، أو اختار موضوع.", "Hi. Ask me about Horus Shield or about staying safe online, or pick a topic."), color = C.Text, fontSize = 15.sp)
                                else -> {
                                    val lines = item.a[L.lang] ?: item.a.getValue("en")
                                    if (item.listy) lines.forEachIndexed { n, s ->
                                        Row { Text("${n + 1}.", color = C.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp)); Text(s, color = C.Text, fontSize = 15.sp, lineHeight = 23.sp) }
                                    } else Text(lines.first(), color = C.Text, fontSize = 15.sp, lineHeight = 23.sp)
                                }
                            }
                        }
                    }
                    is AskMsg.Chips -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.label) Text(L.t("مواضيع ذات صلة", "مواضيع ليها علاقة", "Related"), color = C.Muted, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            m.ids.mapNotNull { AskKB.get(it) }.forEach { c ->
                                Text(c.q[L.lang] ?: c.q.getValue("en"), color = C.GoldLight, fontSize = 14.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(19.dp)).border(1.dp, C.Gold, RoundedCornerShape(19.dp)).clickable { Ask.pick(c.id) }.heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 9.dp))
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(C.Bar)) {
            Hairline()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 200) text = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                    placeholder = { Text(L.t("اكتب سؤالك", "اكتب سؤالك", "Type your question"), color = C.Dim) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { Ask.ask(text); text = "" }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line, cursorColor = C.Gold, focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface, focusedTextColor = C.Text, unfocusedTextColor = C.Text),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(C.goldBrush).clickable { Ask.ask(text); text = "" }, contentAlignment = Alignment.Center) {
                    Ico(if (L.ar) R.drawable.ic_chev_left else R.drawable.ic_chev_right, C.OnGold, 22.dp)
                }
            }
            Text(L.t("الإجابات من دليل Horus ICT ولا يخرج ما تكتبه من هاتفك. لا تكتب هنا كلمات مرور أو رموزًا.", "الإجابات من دليل Horus ICT واللي بتكتبه مش بيخرج من موبايلك. متكتبش هنا باسورد أو أكواد.", "Answers come from Horus ICT's guide and nothing you type leaves your phone. Never type passwords or codes here."),
                color = C.Dim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
        }
    }
}
