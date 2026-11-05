package com.dizicambazi.app

import com.dizicambazi.app.data.Scraper
import com.dizicambazi.app.data.Series
import com.dizicambazi.app.data.Src
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperTest {
    private fun show(s: Series) = Scraper.seriesEpisodes(s).also { l -> println("== ${s.name}"); l.forEach { println("  ${it.num} ${it.airDate} ${it.duration} ${it.sources.map { s -> s.label }} ${it.thumb.take(60)}") } }
    @Test fun sites() {
        assertTrue(show(Series("aui", "Altı Üstü İstanbul", "atv", "Pazartesi", "20:00", "", Src("atv", "alti-ustu-istanbul"))).isNotEmpty())
        assertTrue(show(Series("sba", "Sevdan Bir Ateş", "Show TV", "Çarşamba", "20:00", "", Src("show", "sevdan-bir-ates", "3087"))).isNotEmpty())
        assertTrue(show(Series("us", "Uzak Şehir", "Kanal D", "Pazartesi", "20:00", "", Src("kanald", "uzak-sehir"))).isNotEmpty())
        assertTrue(show(Series("eg", "Evlilik Güzeldir", "TRT 1", "Pazartesi", "20:00", "", Src("trt1", "evlilik-guzeldir"))).isNotEmpty())
        assertTrue(show(Series("ay", "Anne Yarısı", "NOW", "Salı", "20:00", "", Src("now", "Anne-Yarisi"))).isNotEmpty())
        assertTrue(show(Series("gel", "Gelin", "Kanal 7", "Hafta içi her gün", "19:00", "", Src("kanal7", "gelin", page = "https://www.kanal7.com/dizi/dizi-gelin"))).isNotEmpty())
        assertTrue(show(Series("ss", "Sevdiğim Sensin", "Star TV", "Perşembe", "20:00", "", Src("youtube"))).isNotEmpty())
    }
    @Test fun discover() {
        for (q in listOf("Kuruluş Orhan", "Bahar", "Yalı Çapkını")) {
            val c = Scraper.discover(q)
            println("## $q -> " + c.joinToString { "${it.series.name} [${it.series.channel}] ${it.series.src} ${it.series.day} eps=${it.preview.map { e -> e.num }}" })
        }
    }
}
