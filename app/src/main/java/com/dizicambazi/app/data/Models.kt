package com.dizicambazi.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Src(val type: String, val slug: String = "", val id: String = "", val page: String = "") {
    fun toJson() = JSONObject().put("type", type).put("slug", slug).put("id", id).put("page", page)
    companion object {
        fun from(o: JSONObject?) = Src(o?.optString("type", "youtube") ?: "youtube", o?.optString("slug") ?: "",
            o?.optString("id") ?: "", o?.optString("page") ?: "")
    }
}

data class Series(
    val key: String, val name: String, val channel: String, val day: String, val time: String,
    val page: String, val src: Src, val ended: Boolean = false, val userAdded: Boolean = false,
    // v2 — rich info, filled best-effort from public sources (Wikipedia). infoAt = when last fetched.
    val summary: String = "", val poster: String = "", val wikiUrl: String = "", val infoAt: Long = 0,
) {
    fun toJson() = JSONObject().put("key", key).put("name", name).put("channel", channel).put("day", day)
        .put("time", time).put("page", page).put("src", src.toJson()).put("ended", ended).put("userAdded", userAdded)
        .put("summary", summary).put("poster", poster).put("wikiUrl", wikiUrl).put("infoAt", infoAt)
    companion object {
        fun from(o: JSONObject) = Series(o.getString("key"), o.getString("name"), o.optString("channel"),
            o.optString("day"), o.optString("time", "20:00"), o.optString("page"), Src.from(o.optJSONObject("src")),
            o.optBoolean("ended"), o.optBoolean("userAdded"),
            o.optString("summary"), o.optString("poster"), o.optString("wikiUrl"), o.optLong("infoAt"))
    }
}

data class Source(val label: String, val url: String) {
    val youtubeId: String? get() = Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
}

data class Episode(
    val id: String, val series: String, val seriesName: String, val channel: String, val num: Int,
    val airDate: String, val duration: String, val desc: String, val sources: List<Source>,
    val status: String, val thumb: String, val addedAt: Long, val ytEmbed: Boolean = true,
) {
    val online get() = status != "pending"
    val youtubeId get() = sources.firstNotNullOfOrNull { it.youtubeId }
    val siteSource get() = sources.firstOrNull { it.youtubeId == null }
    /** true when the YouTube video can play inside the app */
    val playsInApp get() = youtubeId != null && ytEmbed
    fun toJson(): JSONObject {
        val arr = JSONArray(); sources.forEach { arr.put(JSONObject().put("label", it.label).put("url", it.url)) }
        return JSONObject().put("id", id).put("series", series).put("seriesName", seriesName).put("channel", channel)
            .put("num", num).put("airDate", airDate).put("duration", duration).put("desc", desc).put("sources", arr)
            .put("status", status).put("thumb", thumb).put("addedAt", addedAt).put("ytEmbed", ytEmbed)
    }
    companion object {
        fun from(o: JSONObject): Episode {
            val a = o.optJSONArray("sources") ?: JSONArray()
            val src = (0 until a.length()).map { a.getJSONObject(it).let { s -> Source(s.optString("label"), s.optString("url")) } }
            return Episode(o.getString("id"), o.getString("series"), o.optString("seriesName"), o.optString("channel"),
                o.optInt("num"), o.optString("airDate"), o.optString("duration"), o.optString("desc"), src,
                o.optString("status", "online"), o.optString("thumb"), o.optLong("addedAt"), o.optBoolean("ytEmbed", true))
        }
    }
}

data class Comment(val id: String, val episode: String, val text: String, val at: Long)

data class Notice(val episodeId: String, val title: String, val at: Long)
