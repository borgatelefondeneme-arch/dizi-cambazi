package com.bolumvakti.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Single source of truth. Everything lives on the phone in one JSON file. */
object Repo {
    private lateinit var file: File
    private val lock = Mutex()

    val series = MutableStateFlow<List<Series>>(emptyList())
    val episodes = MutableStateFlow<Map<String, Episode>>(emptyMap())
    val follows = MutableStateFlow<Set<String>>(emptySet())
    val likes = MutableStateFlow<Set<String>>(emptySet())
    val watched = MutableStateFlow<Set<String>>(emptySet())
    val comments = MutableStateFlow<List<Comment>>(emptyList())
    val notices = MutableStateFlow<List<Notice>>(emptyList())
    val lastSeenNotices = MutableStateFlow(0L)
    val lastRefresh = MutableStateFlow(0L)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    @Synchronized fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "state.json")
        val txt = if (file.exists()) file.readText() else ctx.assets.open("seed.json").bufferedReader().readText()
        load(JSONObject(txt))
        if (!file.exists()) save()
    }

    private fun load(o: JSONObject) {
        fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
        fun strs(k: String) = arr(k).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        series.value = arr("series").let { a -> (0 until a.length()).map { Series.from(a.getJSONObject(it)) } }
        episodes.value = arr("episodes").let { a -> (0 until a.length()).map { Episode.from(a.getJSONObject(it)) } }.associateBy { it.id }
        follows.value = strs("follows"); likes.value = strs("likes"); watched.value = strs("watched")
        comments.value = arr("comments").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { c -> Comment(c.getString("id"), c.getString("episode"), c.getString("text"), c.getLong("at")) } } }
        notices.value = arr("notices").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { c -> Notice(c.getString("episodeId"), c.getString("title"), c.getLong("at")) } } }
        lastSeenNotices.value = o.optLong("lastSeenNotices"); lastRefresh.value = o.optLong("lastRefresh")
    }

    @Synchronized fun save() {
        val o = JSONObject()
        o.put("series", JSONArray().also { a -> series.value.forEach { a.put(it.toJson()) } })
        o.put("episodes", JSONArray().also { a -> episodes.value.values.forEach { a.put(it.toJson()) } })
        o.put("follows", JSONArray(follows.value.toList())); o.put("likes", JSONArray(likes.value.toList()))
        o.put("watched", JSONArray(watched.value.toList()))
        o.put("comments", JSONArray().also { a -> comments.value.forEach { a.put(JSONObject().put("id", it.id).put("episode", it.episode).put("text", it.text).put("at", it.at)) } })
        o.put("notices", JSONArray().also { a -> notices.value.takeLast(100).forEach { a.put(JSONObject().put("episodeId", it.episodeId).put("title", it.title).put("at", it.at)) } })
        o.put("lastSeenNotices", lastSeenNotices.value); o.put("lastRefresh", lastRefresh.value)
        val tmp = File(file.parentFile, "state.tmp"); tmp.writeText(o.toString()); tmp.renameTo(file)
    }

    fun toggleFollow(k: String) { follows.value = follows.value.toggle(k); save() }
    fun toggleLike(k: String) { likes.value = likes.value.toggle(k); save() }
    fun setWatched(id: String, on: Boolean) { watched.value = if (on) watched.value + id else watched.value - id; save() }
    fun addComment(ep: String, text: String) {
        comments.value = comments.value + Comment(System.currentTimeMillis().toString(36), ep, text.trim(), System.currentTimeMillis()); save()
    }
    fun deleteComment(id: String) { comments.value = comments.value.filterNot { it.id == id }; save() }
    fun markNoticesSeen() { lastSeenNotices.value = System.currentTimeMillis(); save() }
    private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

    fun episodesOf(key: String) = episodes.value.values.filter { it.series == key }.sortedByDescending { it.num }
    fun thumbOf(key: String) = episodesOf(key).let { l -> (l.firstOrNull { it.online } ?: l.firstOrNull())?.thumb ?: "" }

    /** Re-reads every running series. Returns episodes that were not known before (new or just uploaded). */
    suspend fun refresh(onlyFollowed: Boolean = false): List<Episode> = lock.withLock {
        _refreshing.value = true
        try {
            val targets = series.value.filter { !it.ended && (!onlyFollowed || it.key in follows.value) }
            val sem = Semaphore(4)
            val fetched = withContext(Dispatchers.IO) {
                coroutineScope { targets.map { s -> async { sem.withPermit { runCatching { Scraper.seriesEpisodes(s) }.getOrDefault(emptyList()) } } }.awaitAll() }
            }.flatten()
            val map = episodes.value.toMutableMap(); val fresh = ArrayList<Episode>()
            for (e in fetched) {
                val old = map[e.id]
                if (old == null || !old.online) {
                    map[e.id] = e.copy(addedAt = System.currentTimeMillis(), thumb = e.thumb.ifEmpty { old?.thumb ?: thumbOf(e.series) })
                    fresh += map[e.id]!!
                } else if (old.sources.size < e.sources.size) {
                    map[e.id] = old.copy(sources = e.sources, duration = old.duration.ifEmpty { e.duration }, ytEmbed = e.ytEmbed)
                }
            }
            // keep the last 6 episodes per series
            val keep = map.values.groupBy { it.series }.flatMap { (_, l) -> l.sortedByDescending { it.num }.take(6) }
            episodes.value = keep.associateBy { it.id }
            val now = System.currentTimeMillis()
            val followedNew = fresh.filter { it.series in follows.value }
            notices.value = notices.value + followedNew.map { Notice(it.id, "${it.seriesName} · ${it.num}. bölüm internete düştü", now) }
            lastRefresh.value = now; save()
            fresh
        } finally { _refreshing.value = false }
    }

    fun addSeries(c: Scraper.Candidate) {
        val s = c.series
        if (series.value.none { it.key == s.key }) series.value = series.value + s
        val now = System.currentTimeMillis()
        episodes.value = episodes.value + c.preview.associate { it.id to it.copy(addedAt = now) }
        follows.value = follows.value + s.key; save()
    }
    fun removeSeries(key: String) {
        series.value = series.value.filterNot { it.key == key }
        episodes.value = episodes.value.filterValues { it.series != key }
        follows.value = follows.value - key; likes.value = likes.value - key; save()
    }
}
