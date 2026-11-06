package com.dizicambazi.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Fetches episodes straight from channel websites and official YouTube channels, on the phone. */
object Scraper {
    private val TZ: ZoneId = ZoneId.of("Europe/Istanbul")
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true).build()
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
    val DAYS = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val SRC_LABEL = mapOf("atv" to "atv.com.tr", "show" to "showtv.com.tr", "kanald" to "kanald.com.tr",
        "now" to "nowtv.com.tr", "trt1" to "trt1.com.tr", "kanal7" to "izle7.com")
    private val MON = mapOf("ocak" to 1, "subat" to 2, "mart" to 3, "nisan" to 4, "mayis" to 5, "haziran" to 6,
        "temmuz" to 7, "agustos" to 8, "eylul" to 9, "ekim" to 10, "kasim" to 11, "aralik" to 12)
    private val CHANNEL_OF = mapOf("atv" to "atv", "show" to "Show TV", "kanald" to "Kanal D", "now" to "NOW",
        "trt1" to "TRT 1", "kanal7" to "Kanal 7")

    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"
    fun get(url: String): String = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", if (url.contains("youtube.com")) DESKTOP_UA else UA).header("Accept-Language", "tr-TR,tr").build())
            .execute().use { if (it.isSuccessful) it.body?.string() ?: "" else "" }
    } catch (e: Exception) { "" }

    fun lowTr(s: String) = s.replace("İ", "i").replace("I", "ı").lowercase(Locale("tr"))
    fun norm(s: String) = lowTr(s).replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
    fun ascii(s: String) = Normalizer.normalize(lowTr(s).replace("ı", "i"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")

    private fun unesc(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace("&ouml;", "ö").replace("&uuml;", "ü").replace("&Ccedil;", "Ç").replace("&ccedil;", "ç")
        .replace("&Ouml;", "Ö").replace("&Uuml;", "Ü").replace("&hellip;", "…").replace("&rsquo;", "’")

    private fun snap(d: LocalDate, wd: Int?): LocalDate =
        if (wd == null) d else d.minusDays(((d.dayOfWeek.value - 1 - wd + 7) % 7).toLong())

    private fun isoDur(p: String?): String {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").find(p ?: "") ?: return ""
        val (h, mi, s) = m.destructured
        if (h.isEmpty() && mi.isEmpty() && s.isEmpty()) return ""
        val t = (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        return "%d:%02d:%02d".format(t / 3600, t % 3600 / 60, t % 60)
    }
    private fun secs(l: String) = l.split(":").fold(0) { a, x -> a * 60 + (x.toIntOrNull() ?: 0) }

    private fun ago(s: String?): LocalDateTime? {
        val m = Regex("(\\d+)\\s*(dk|sa|gün|hf|ay|yıl|minute|hour|day|week|month|year)").find(s ?: "") ?: return null
        val n = m.groupValues[1].toLong(); val now = LocalDateTime.now(TZ)
        return when (m.groupValues[2]) {
            "dk", "minute" -> now.minusMinutes(n); "sa", "hour" -> now.minusHours(n); "gün", "day" -> now.minusDays(n)
            "hf", "week" -> now.minusWeeks(n); "ay", "month" -> now.minusDays(30 * n); else -> now.minusDays(365 * n)
        }
    }

    private data class Page(val img: String?, val desc: String?, val dur: String, val date: String?, val yt: String?)
    private fun meta(s: String): Page {
        fun m(p: String) = Regex(p).find(s)?.groupValues?.get(1)?.let { unesc(it) }
        var img = m("\"thumbnailUrl\"\\s*:\\s*\\[?\\s*\"([^\"]+)\"")
        val og = m("<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\"")
        if (img == null || img.contains("logo")) img = if (og != null && !og.contains("logo")) og else null
        return Page(img, m("<meta[^>]+(?:property=\"og:description\"|name=\"description\")[^>]+content=\"([^\"]*)\""),
            isoDur(m("\"duration\"\\s*:\\s*\"([^\"]+)\"")), m("\"(?:datePublished|uploadDate)\"\\s*:\\s*\"([^\"]+)\""),
            m("youtube\\.com/(?:embed/|watch\\?v=)([A-Za-z0-9_-]{11})"))
    }

    /** episode number -> page url, read from the channel's own website */
    fun siteEpisodes(src: Src): Map<Int, String> {
        val sl = Regex.escape(src.slug)
        val (lu, rx, base) = when (src.type) {
            "atv" -> Triple("https://www.atv.com.tr/${src.slug}/bolumler", "href=\"(/$sl/(\\d+)-bolum/izle)\"", "https://www.atv.com.tr")
            "show" -> Triple("https://www.showtv.com.tr/dizi/tanitim/${src.slug}/${src.id}", "href=\"(/dizi/tum_bolumler/$sl-sezon-\\d+-bolum-(\\d+)-izle/\\d+)\"", "https://www.showtv.com.tr")
            "kanald" -> Triple("https://www.kanald.com.tr/${src.slug}/bolumler?orderby=StartDate%20desc,EpisodeNumber%20desc", "href=\"(/$sl/bolumler/$sl-(\\d+)-bolum)\"", "https://www.kanald.com.tr")
            "now" -> Triple("https://www.nowtv.com.tr/${src.slug}/izle", "href=\"(https://www.nowtv.com.tr/$sl/bolum/(\\d+))\"", "")
            "trt1" -> Triple("https://www.trt1.com.tr/diziler/${src.slug}", "href=\"(/diziler/$sl/bolum/(\\d+)-bolum)\"", "https://www.trt1.com.tr")
            "kanal7" -> Triple(src.page.ifEmpty { "https://www.kanal7.com/dizi/dizi-${src.slug}" }, "(https://www.izle7.com/kanal7/izle-\\d+-$sl-(\\d+)-bolum-(?!fragmani)[a-z0-9-]+\\.html)", "")
            else -> return emptyMap()
        }
        val s = get(lu); val out = HashMap<Int, String>()
        Regex(rx).findAll(s).forEach { out[it.groupValues[2].toInt()] = base + unesc(it.groupValues[1]) }
        return out
    }

    data class YtVideo(val id: String, val title: String, val len: String?, val ago: String?, val channel: String)
    fun ytSearch(q: String): List<YtVideo> {
        val s = get("https://www.youtube.com/results?search_query=" + URLEncoder.encode("$q bölüm", "UTF-8") + "&sp=CAISBBABGAI%253D&hl=tr&gl=TR")
        val m = Regex("var ytInitialData = (\\{.*?\\});</script>", RegexOption.DOT_MATCHES_ALL).find(s) ?: return emptyList()
        val out = ArrayList<YtVideo>()
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    val v = o.optJSONObject("videoRenderer")
                    if (v != null) {
                        val title = v.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        out.add(YtVideo(v.optString("videoId"), title, v.optJSONObject("lengthText")?.optString("simpleText"),
                            v.optJSONObject("publishedTimeText")?.optString("simpleText"),
                            v.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""))
                        return
                    }
                    o.keys().forEach { walk(o.opt(it)) }
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        try { walk(JSONObject(m.groupValues[1])) } catch (_: Exception) {}
        return out
    }

    /** YouTube answers 401 on oEmbed when the owner disabled playback in other apps. */
    fun embeddable(id: String): Boolean = try {
        client.newCall(Request.Builder().url("https://www.youtube.com/oembed?format=json&url=https://www.youtube.com/watch?v=$id").header("User-Agent", DESKTOP_UA).build())
            .execute().use { it.code != 401 && it.code != 403 }
    } catch (e: Exception) { true }

    private class Raw(var site: String? = null, var img: String? = null, var desc: String? = null, var dur: String = "",
                      var air: LocalDate? = null, var yt: String? = null)

    /** Latest (up to 3) episodes of one series, merged from website + YouTube. */
    fun seriesEpisodes(s: Series): List<Episode> {
        val wd = DAYS.indexOf(s.day).takeIf { it >= 0 }
        val t = s.src.type; val e = HashMap<Int, Raw>()
        val site = siteEpisodes(s.src)
        val top = site.keys.sortedDescending().take(3)
        for (n in top) {
            val r = Raw(site = site[n]); e[n] = r
            val p = meta(get(site[n]!!))
            r.img = p.img; r.desc = p.desc; r.dur = p.dur; r.yt = p.yt
            if (p.date != null && t in listOf("show", "kanald", "trt1")) try {
                var x = OffsetDateTime.parse(p.date)
                    .atZoneSameInstant(TZ).toLocalDateTime()
                if (t == "kanald" && x.hour < 6) x = x.minusDays(1)
                if (t == "trt1" && x.hour < 12) x = x.minusDays(1)
                r.air = snap(x.toLocalDate(), wd)
            } catch (_: Exception) {}
            if (t == "kanal7") Regex("-(\\d+)-(ocak|subat|mart|nisan|mayis|haziran|temmuz|agustos|eylul|ekim|kasim|aralik)-").find(site[n]!!)?.let {
                r.air = LocalDate.of(LocalDate.now(TZ).year, MON[it.groupValues[2]]!!, it.groupValues[1].toInt())
            }
        }
        val base = norm(s.name.substringBefore(":")); val full = Regex("(^|\\s)" + Regex.escape(norm(s.name)) + "\\s+\\d+")
        for (v in ytSearch(s.name)) {
            val len = v.len ?: continue; if (secs(len) !in 3001 until 4 * 3600) continue
            if (Regex("review|analy|anal[iİ]z|kısa|tüm bölüm|tek part|fragman").containsMatchIn(lowTr(v.title))) continue
            if (!norm(v.channel).contains(base)) continue
            if (!full.containsMatchIn(norm(v.title))) continue
            val n = Regex("(\\d+)\\.?\\s*[Bb]ölüm").find(v.title)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val a = ago(v.ago) ?: continue
            if (java.time.Duration.between(a, LocalDateTime.now(TZ)).toDays() > 40) continue
            val r = e.getOrPut(n) { Raw() }
            if (r.yt == null) {
                r.yt = v.id; if (r.dur.isEmpty()) r.dur = len
                if (r.air == null) r.air = snap(if (a.hour < 6) a.toLocalDate().minusDays(1) else a.toLocalDate(), wd)
            }
        }
        val nums = e.keys.sortedDescending().take(3)
        for (n in nums) if (e[n]!!.air == null) {
            val ref = nums.filter { e[it]!!.air != null }
            e[n]!!.air = if (ref.isNotEmpty() && wd != null) { val r = ref.minBy { kotlin.math.abs(it - n) }; e[r]!!.air!!.plusDays(7L * (n - r)) } else LocalDate.now(TZ)
        }
        val today = LocalDate.now(TZ)
        for (n in nums) if (e[n]!!.air!!.isAfter(today)) e[n]!!.air = snap(today, wd)
        for (i in 1 until nums.size) {
            val a = e[nums[i - 1]]!!.air!!; val b = e[nums[i]]!!.air!!
            if (wd != null && java.time.temporal.ChronoUnit.DAYS.between(b, a) < 6) e[nums[i]]!!.air = a.minusDays(7L * (nums[i - 1] - nums[i]))
        }
        return nums.map { n ->
            val r = e[n]!!; val src = ArrayList<Source>()
            r.yt?.let { src.add(Source("YouTube", "https://www.youtube.com/watch?v=$it")) }
            r.site?.let { src.add(Source(SRC_LABEL[t] ?: "Kanal sitesi", it)) }
            Episode("${s.key}-$n", s.key, s.name, s.channel, n, r.air.toString(), r.dur, (r.desc ?: "").take(220), src, "online",
                r.yt?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: r.img ?: "", System.currentTimeMillis(),
                ytEmbed = r.yt?.let { embeddable(it) } ?: true)
        }
    }

    fun slug(name: String) = ascii(name).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun titleSlug(name: String) = ascii(name).replace(Regex("[^a-z0-9]+"), " ").trim().split(" ")
        .joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }

    data class Candidate(val series: Series, val preview: List<Episode>)

    /** Look a series up by name on the channel sites and YouTube. */
    fun discover(name: String): List<Candidate> {
        val clean = name.trim(); if (clean.length < 2) return emptyList()
        val sl = slug(clean); val res = ArrayList<Candidate>()
        val tries = ArrayList<Src>()
        tries += Src("atv", sl); tries += Src("kanald", sl); tries += Src("trt1", sl); tries += Src("now", titleSlug(clean))
        Regex("/dizi/tanitim/(" + Regex.escape(sl) + ")/(\\d+)").find(get("https://www.showtv.com.tr/diziler"))?.let { tries += Src("show", it.groupValues[1], it.groupValues[2]) }
        tries += Src("kanal7", sl, page = "https://www.kanal7.com/dizi/dizi-$sl")
        for (src in tries) {
            val eps = siteEpisodes(src); if (eps.isEmpty()) continue
            val ch = CHANNEL_OF[src.type] ?: ""
            val page = when (src.type) {
                "atv" -> "https://www.atv.com.tr/$sl/bolumler"; "kanald" -> "https://www.kanald.com.tr/$sl/bolumler"
                "trt1" -> "https://www.trt1.com.tr/diziler/$sl"; "now" -> "https://www.nowtv.com.tr/${src.slug}/izle"
                "show" -> "https://www.showtv.com.tr/dizi/tanitim/${src.slug}/${src.id}"; else -> src.page
            }
            val s0 = Series("u-${src.type}-$sl", clean, ch, "", "20:00", page, src, userAdded = true)
            val prev = seriesEpisodes(s0)
            res += Candidate(s0.copy(day = guessDay(prev)), prev); break
        }
        if (res.isEmpty()) {
            val base = norm(clean)
            val vids = ytSearch(clean).filter { norm(it.channel).contains(base.substringBefore(" ")) && norm(it.title).contains(base) }
            if (vids.isNotEmpty()) {
                val chText = vids.first().channel
                val ch = listOf("Show TV", "Star TV", "Kanal D", "NOW", "TRT 1", "atv", "Kanal 7", "TV8").firstOrNull { lowTr(chText).contains(lowTr(it).replace(" ", "")) || lowTr(chText).contains(lowTr(it)) } ?: "YouTube"
                val s0 = Series("u-yt-$sl", clean, ch, "", "20:00", "https://www.youtube.com/results?search_query=" + URLEncoder.encode(clean, "UTF-8"), Src("youtube", sl), userAdded = true)
                val prev = seriesEpisodes(s0)
                if (prev.isNotEmpty()) res += Candidate(s0.copy(day = guessDay(prev)), prev)
            }
        }
        return res
    }

    private fun guessDay(eps: List<Episode>): String {
        val days = eps.mapNotNull { runCatching { LocalDate.parse(it.airDate).dayOfWeek }.getOrNull() }
        val top = days.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return ""
        return DAYS[top.value - 1]
    }
    @Suppress("unused") private fun dummy(d: DayOfWeek) = d

    data class Info(val summary: String, val poster: String, val url: String)

    /** Best-effort synopsis + poster from Turkish Wikipedia's public REST API. Legal to read; may return null. */
    fun fetchInfo(name: String): Info? {
        val title = wikiSearch(name) ?: return null
        val s = get("https://tr.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(" ", "_"), "UTF-8"))
        if (s.isBlank()) return null
        return try {
            val o = JSONObject(s)
            if (o.optString("type") == "disambiguation") return null
            val extract = o.optString("extract")
            if (extract.isBlank()) return null
            Info(extract, o.optJSONObject("thumbnail")?.optString("source") ?: o.optJSONObject("originalimage")?.optString("source") ?: "",
                o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page") ?: "https://tr.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(" ", "_"), "UTF-8"))
        } catch (e: Exception) { null }
    }

    /** Finds the Wikipedia article title that best matches a TV series name. */
    private fun wikiSearch(name: String): String? {
        val s = get("https://tr.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=6&srsearch=" +
            URLEncoder.encode("$name dizi", "UTF-8"))
        return try {
            val hits = JSONObject(s).optJSONObject("query")?.optJSONArray("search") ?: return null
            val want = norm(name)
            var fallback: String? = null
            for (i in 0 until hits.length()) {
                val t = hits.getJSONObject(i).optString("title"); val nt = norm(t)
                if (nt == want || nt == "$want dizi" || nt.startsWith("$want ")) return t
                if (fallback == null && nt.contains(want)) fallback = t
            }
            fallback ?: if (hits.length() > 0) hits.getJSONObject(0).optString("title") else null
        } catch (e: Exception) { null }
    }
}
