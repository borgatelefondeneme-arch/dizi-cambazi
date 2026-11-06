package com.dizicambazi.app

import com.dizicambazi.app.data.Episode
import com.dizicambazi.app.data.Scraper
import com.dizicambazi.app.data.Series
import com.dizicambazi.app.data.Src
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Not a real unit test — a catalogue builder. Enumerates candidate currently-airing Turkish series,
 * scrapes each with the app's own Scraper, keeps the ones with a recent episode, and writes
 * app/src/main/assets/seed.json. Run: ./gradlew testDebugUnitTest --tests "*SeedBuilder"
 */
class SeedBuilder {
    private val DAYS = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val ended = setOf("Sevdam Karadeniz")
    // Authoritative prime-time broadcast day/time. Series not listed keep an empty day (so they never
    // show up under a wrong "Bu akşam"), but still appear in feeds, search and follow.
    private val known = mapOf(
        "Altı Üstü İstanbul" to ("Pazartesi" to "20:00"), "Güneşin Doğduğu Yer" to ("Perşembe" to "20:00"),
        "Kuruluş Orhan" to ("Cuma" to "20:00"), "Sevdan Bir Ateş" to ("Çarşamba" to "20:00"),
        "Kızılcık Şerbeti" to ("Cuma" to "20:00"), "Muhtemel Aşk" to ("Perşembe" to "20:00"),
        "Uzak Şehir" to ("Pazartesi" to "20:00"), "Haysiyet" to ("Salı" to "20:00"),
        "Güller ve Günahlar" to ("Cumartesi" to "20:00"), "Daha 17" to ("Pazar" to "20:00"),
        "Teşkilat" to ("Pazar" to "20:00"), "Gönül Dağı" to ("Cumartesi" to "20:00"),
        "Evlilik Güzeldir" to ("Pazartesi" to "20:00"), "Kod Adı Kırlangıç" to ("Cumartesi" to "22:45"),
        "Mehmed: Fetihler Sultanı" to ("Salı" to "20:00"), "Taşacak Bu Deniz" to ("Cuma" to "20:00"),
        "Anne Yarısı" to ("Salı" to "20:00"), "Halef: Köklerin Çağrısı" to ("Perşembe" to "20:00"),
        "Ömür Usta" to ("Pazar" to "20:00"), "Yeraltı" to ("Çarşamba" to "20:00"),
        "Sevdam Karadeniz" to ("Pazartesi" to "20:00"), "Sevdiğim Sensin" to ("Perşembe" to "20:00"),
        "Gelin" to ("Hafta içi her gün" to "19:00"),
    )

    private fun slug(name: String) = Scraper.ascii(name).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun cand(name: String, channel: String, src: Src): Series {
        val k = known[name]
        return Series(slug(name), name, channel, k?.first ?: "", k?.second ?: "20:00", page(src), src, ended = name in ended)
    }

    private fun page(src: Src) = when (src.type) {
        "atv" -> "https://www.atv.com.tr/${src.slug}/bolumler"
        "kanald" -> "https://www.kanald.com.tr/${src.slug}/bolumler"
        "trt1" -> "https://www.trt1.com.tr/diziler/${src.slug}"
        "now" -> "https://www.nowtv.com.tr/${src.slug}/izle"
        "show" -> "https://www.showtv.com.tr/dizi/tanitim/${src.slug}/${src.id}"
        "kanal7" -> src.page
        else -> "https://www.youtube.com/results?search_query=${src.slug}"
    }

    private fun candidates(): List<Series> {
        val l = ArrayList<Series>()
        // atv
        listOf("Altı Üstü İstanbul" to "alti-ustu-istanbul", "Güneşin Doğduğu Yer" to "gunesin-dogdugu-yer", "Kuruluş Orhan" to "kurulus-orhan")
            .forEach { (n, s) -> l += cand(n, "atv", Src("atv", s)) }
        // Show TV (needs tanitim id)
        listOf(Triple("Sevdan Bir Ateş", "sevdan-bir-ates", "3087"), Triple("Kızılcık Şerbeti", "kizilcik-serbeti", "2875"),
            Triple("Muhtemel Aşk", "muhtemel-ask", "3072"), Triple("Cevher", "cevher", "3113"), Triple("Menekşe", "menekse", "3111"),
            Triple("Bahar", "bahar", "2941"), Triple("Deha", "deha", "2975"), Triple("Delikanlı", "delikanli", "3061"),
            Triple("Veliaht", "veliaht", "3003"), Triple("Siyah Kalp", "siyah-kalp", "2974"))
            .forEach { (n, s, id) -> l += cand(n, "Show TV", Src("show", s, id)) }
        // Kanal D
        listOf("Uzak Şehir" to "uzak-sehir", "Haysiyet" to "haysiyet", "Güller ve Günahlar" to "guller-ve-gunahlar",
            "Daha 17" to "daha-17", "Eşref Rüya" to "esref-ruya", "Susuz Yaz" to "susuz-yaz", "İnci Taneleri" to "inci-taneleri",
            "Güneşin Kızları" to "gunesin-kizlari", "Karakuyu" to "karakuyu", "Hayat Şarkısı" to "hayat-sarkisi")
            .forEach { (n, s) -> l += cand(n, "Kanal D", Src("kanald", s)) }
        // TRT 1
        listOf("Teşkilat" to "teskilat", "Gönül Dağı" to "gonul-dagi", "Evlilik Güzeldir" to "evlilik-guzeldir",
            "Kod Adı Kırlangıç" to "kod-adi-kirlangic", "Mehmed: Fetihler Sultanı" to "mehmed-fetihler-sultani",
            "Taşacak Bu Deniz" to "tasacak-bu-deniz", "Sumud" to "sumud", "Vefa Sultan" to "vefa-sultan",
            "Kudüs Fatihi Selahaddin Eyyubi" to "kudus-fatihi-selahaddin-eyyubi", "Bir Zamanlar İstanbul" to "bir-zamanlar-istanbul",
            "Mehmetçik Kûtü'l Zafer" to "mehmetcik-kutlu-zafer")
            .forEach { (n, s) -> l += cand(n, "TRT 1", Src("trt1", s)) }
        // NOW
        listOf("Anne Yarısı" to "Anne-Yarisi", "Halef: Köklerin Çağrısı" to "Halef-Koklerin-Cagrisi", "Ömür Usta" to "Omur-Usta",
            "Yeraltı" to "Yeralti", "Sevdam Karadeniz" to "Sevdam-Karadeniz", "Kıskanmak" to "Kiskanmak", "Ben Leman" to "Ben-Leman",
            "Sahtekârlar" to "Sahtekarlar", "Sakıncalı" to "Sakincali", "Leyla: Hayat, Aşk, Adalet" to "Leyla-Hayat-Ask-Adalet",
            "Başka Bir Sen" to "Baska-Bir-Sen", "Ben Onun Annesiyim" to "Ben-Onun-Annesiyim")
            .forEach { (n, s) -> l += cand(n, "NOW", Src("now", s)) }
        // Kanal 7
        listOf("Gelin" to "gelin", "Kalbim Sana Emanet" to "kalbim-sana-emanet", "Bilmezler Ki Kalbimdesin" to "bilmezler-ki-kalbimdesin",
            "Esaret" to "esaret", "Gönül Sözüm" to "gonul-sozum")
            .forEach { (n, s) -> l += cand(n, "Kanal 7", Src("kanal7", s, page = "https://www.kanal7.com/dizi/dizi-$s")) }
        // Star TV (YouTube)
        l += cand("Sevdiğim Sensin", "Star TV", Src("youtube", "sevdigim-sensin"))
        return l
    }

    private fun guessDay(eps: List<Episode>): String {
        val days = eps.mapNotNull { runCatching { LocalDate.parse(it.airDate).dayOfWeek }.getOrNull() }
        if (days.toSet().size >= 3) return "Hafta içi her gün"
        val top = days.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return ""
        return DAYS[top.value - 1]
    }

    @Test fun build() {
        // Only runs when explicitly requested: ./gradlew testDebugUnitTest --tests "*SeedBuilder" -DbuildSeed=1
        org.junit.Assume.assumeTrue("set -DbuildSeed=1 to rebuild seed.json", System.getProperty("buildSeed") == "1")
        val today = LocalDate.now()
        val result = runBlocking {
            val sem = Semaphore(6)
            candidates().map { s ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val eps = runCatching { Scraper.seriesEpisodes(s) }.getOrDefault(emptyList())
                        s to eps
                    }
                }
            }.awaitAll()
        }
        val series = JSONArray(); val episodes = JSONArray(); var kept = 0; val now = System.currentTimeMillis()
        for ((s, eps) in result.sortedBy { it.first.name.lowercase() }) {
            val recent = eps.filter { it.airDate.isNotEmpty() }
            val latest = recent.mapNotNull { runCatching { LocalDate.parse(it.airDate) }.getOrNull() }.maxOrNull()
            val fresh = latest != null && ChronoUnit.DAYS.between(latest, today) <= 30
            if (eps.size < 2 || !fresh) { println("DROP ${s.name} (eps=${eps.size} latest=$latest)"); continue }
            kept++
            // keep the authoritative day; only fall back to a guess for unknown new series
            val day = if (s.day.isNotEmpty()) s.day else guessDay(eps).let { if (it == "Hafta içi her gün") it else "" }
            series.put(s.copy(day = day).toJson())
            eps.forEach { episodes.put(it.copy(addedAt = now).toJson()) }
            println("KEEP ${s.name} [${s.channel}] day='$day' eps=${eps.map { it.num }}")
        }
        val root = JSONObject()
        root.put("series", series); root.put("episodes", episodes)
        root.put("follows", JSONArray()); root.put("likes", JSONArray()); root.put("watched", JSONArray())
        root.put("comments", JSONArray()); root.put("notices", JSONArray())
        File("src/main/assets/seed.json").writeText(root.toString())
        println("=== KEPT $kept series, ${episodes.length()} episodes ===")
    }
}
