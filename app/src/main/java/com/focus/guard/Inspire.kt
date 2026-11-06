package com.focus.guard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Content shown on the block screen: a Quran verse, a hadith or a story (chosen in Settings).
 * Verses come from api.alquran.cloud and hadiths from the free hadith-api dataset on jsDelivr;
 * stories are bundled. If the network is down a story is shown instead.
 */
object Inspire {
    const val ALL = "all"; const val QURAN = "quran"; const val HADITH = "hadith"; const val STORY = "story"; const val OFF = "off"

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "FocusGuard")
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (_: Exception) { null }

    class Ayah(val text: String, val surah: String, val inSurah: Int, val number: Int)

    /** Verse by its position in the whole Quran (1..6236). */
    suspend fun ayah(n: Int): Ayah? = withContext(Dispatchers.IO) {
        val body = get("https://api.alquran.cloud/v1/ayah/$n/quran-uthmani") ?: return@withContext null
        try {
            val d = JSONObject(body).getJSONObject("data")
            Ayah(d.getString("text"), d.getJSONObject("surah").getString("name"), d.getInt("numberInSurah"), d.getInt("number"))
        } catch (_: Exception) { null }
    }

    /** Hadith number [n] of an-Nawawi's Forty (Arabic text). */
    suspend fun hadith(n: Int): String? = withContext(Dispatchers.IO) {
        val body = get("https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-nawawi/$n.json") ?: return@withContext null
        try {
            JSONObject(body).getJSONArray("hadiths").getJSONObject(0).getString("text").trim().ifEmpty { null }
        } catch (_: Exception) { null }
    }

    fun arDigits(n: Int) = n.toString().map { '٠' + (it - '0') }.joinToString("")
    fun ayahMark(n: Int) = "﴿${arDigits(n)}﴾"
}

@Composable fun InspireCard(mode: String) {
    if (mode == Inspire.OFF) return
    val scope = rememberCoroutineScope()
    val parts = remember { mutableStateListOf<String>() }
    var kind by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var qNext by remember { mutableIntStateOf(0) }
    var lastSurah by remember { mutableStateOf("") }
    var story by remember { mutableStateOf<Story?>(null) }
    var sPos by remember { mutableIntStateOf(0) }

    fun showStory() {
        val st = Stories.ALL.random()
        story = st; sPos = 1; kind = Inspire.STORY; source = "${st.title} · ${st.source}"
        parts.clear(); parts.add(st.pages[0]); more = st.pages.size > 1
    }

    LaunchedEffect(Unit) {
        val k = if (mode == Inspire.ALL) listOf(Inspire.QURAN, Inspire.HADITH, Inspire.STORY).random() else mode
        var done = false
        when (k) {
            Inspire.QURAN -> {
                val (s, a) = QuranIndex.PICKS.random()
                Inspire.ayah(QuranIndex.global(s, a))?.let {
                    kind = Inspire.QURAN; source = "${it.surah} · ${it.inSurah}"; lastSurah = it.surah
                    parts.clear(); parts.add("${it.text} ${Inspire.ayahMark(it.inSurah)}")
                    qNext = it.number + 1; more = qNext <= QuranIndex.TOTAL; done = true
                }
            }
            Inspire.HADITH -> {
                val n = QuranIndex.HADITH_PICKS.random()
                Inspire.hadith(n)?.let {
                    kind = Inspire.HADITH; source = "الأربعون النووية · $n"
                    parts.clear(); parts.add(it); more = false; done = true
                }
            }
        }
        if (!done) showStory()
        loading = false
    }

    Spacer(Modifier.height(16.dp))
    if (loading) { Sub(t("loading")); return }
    Title(t(when (kind) { Inspire.QURAN -> "iQuran"; Inspire.HADITH -> "iHadith"; else -> "iStory" }))
    Sub(source)
    Spacer(Modifier.height(10.dp))
    parts.forEach { p ->
        Text(p, fontSize = if (kind == Inspire.STORY) 17.sp else 21.sp, lineHeight = if (kind == Inspire.STORY) 30.sp else 36.sp,
            fontWeight = FontWeight.Medium, color = LC.current.tx, textAlign = TextAlign.Start,
            style = TextStyle(textDirection = TextDirection.Rtl), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
    }
    if (more) {
        var busy by remember { mutableStateOf(false) }
        PrimaryBtn(if (busy) t("loading") else t("contRead")) {
            if (busy) return@PrimaryBtn
            if (kind == Inspire.STORY) {
                val st = story ?: return@PrimaryBtn
                parts.add(st.pages[sPos]); sPos++; more = sPos < st.pages.size
            } else {
                busy = true
                scope.launch {
                    var added = 0
                    while (added < 5 && qNext <= QuranIndex.TOTAL) {
                        val a = Inspire.ayah(qNext) ?: break
                        val head = if (a.surah != lastSurah) "— ${a.surah} —\n" else ""
                        parts.add("$head${a.text} ${Inspire.ayahMark(a.inSurah)}")
                        lastSurah = a.surah; qNext = a.number + 1; added++
                    }
                    more = qNext <= QuranIndex.TOTAL
                    busy = false
                }
            }
        }
    }
}
