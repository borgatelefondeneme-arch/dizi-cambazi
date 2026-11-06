package com.dizicambazi.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dizicambazi.app.data.Episode
import com.dizicambazi.app.data.Repo
import com.dizicambazi.app.data.Scraper
import com.dizicambazi.app.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle

sealed class Route {
    data class Tab(val name: String) : Route()
    data class Ep(val id: String, val autoplay: Boolean = false) : Route()
    data class Ser(val key: String) : Route()
    data object Search : Route()
    data object Notices : Route()
    data object Settings : Route()
    data object About : Route()
    data object Calendar : Route()
}

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        deepLink.value = intent.getStringExtra("episode")
        setContent {
            val mode by Repo.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            DcTheme(dark) { AppRoot(deepLink) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); deepLink.value = intent.getStringExtra("episode")
    }
}

/** Plays inside the app when possible; the screen falls back to the site on its own when it can't. */
fun playExternalFallback(ctx: Context, e: Episode) {
    val site = e.siteSource
    if (site != null) WebPlayerActivity.start(ctx, site.url, "${e.seriesName} · ${e.num}. bölüm")
    else e.sources.firstOrNull()?.let { WebPlayerActivity.openExternal(ctx, it.url) }
}

@Composable
fun AppRoot(deepLink: MutableState<String?>) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val onboardingDone by Repo.onboardingDone.collectAsStateWithLifecycle()
    if (!onboardingDone) { OnboardingScreen(); return }
    val stack = remember { mutableStateListOf<Route>(Route.Tab("home")) }
    val top = stack.last()
    fun push(r: Route) { stack.add(r) }
    val play: (Episode) -> Unit = { e ->
        Repo.markOpened(e.id)
        if (e.playsInApp) push(Route.Ep(e.id, autoplay = true))
        else { Repo.setWatched(e.id, true); playExternalFallback(ctx, e) }
    }
    val open: (Episode) -> Unit = { Repo.markOpened(it.id); push(Route.Ep(it.id)) }
    LaunchedEffect(deepLink.value) { deepLink.value?.let { push(Route.Ep(it)); deepLink.value = null } }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    // Play Store dışı güncelleme uyarısı
    var update by remember { mutableStateOf<com.dizicambazi.app.data.UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val cur = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt() }.getOrDefault(0)
        val info = withContext(Dispatchers.IO) { com.dizicambazi.app.data.UpdateCheck.fetch() }
        if (info != null && info.versionCode > cur) update = info
    }
    update?.let { up ->
        AlertDialog(onDismissRequest = { update = null },
            title = { Text("Yeni sürüm var: ${up.versionName}") },
            text = { Text((if (up.notes.isNotEmpty()) up.notes + "\n\n" else "") + "İndirip üstüne kurabilirsin; verilerin korunur.") },
            confirmButton = { TextButton({ WebPlayerActivity.openExternal(ctx, up.url); update = null }) { Text("İndir") } },
            dismissButton = { TextButton({ update = null }) { Text("Daha sonra") } })
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (System.currentTimeMillis() - Repo.lastRefresh.value > 60 * 60 * 1000L) {
            Repo.refresh()
        }
    }
    val tab = (stack.lastOrNull { it is Route.Tab } as? Route.Tab)?.name ?: "home"

    Scaffold(containerColor = C.bg, bottomBar = {
        if (top is Route.Tab) NavigationBar(containerColor = C.surface, tonalElevation = 0.dp) {
            listOf(Triple("home", "Ana sayfa", Icons.Outlined.Home), Triple("week", "Bu hafta", Icons.Outlined.CalendarMonth),
                Triple("follow", "Takip", Icons.Outlined.NotificationsNone), Triple("likes", "Beğeniler", Icons.Outlined.FavoriteBorder),
                Triple("all", "Diziler", Icons.Outlined.GridView), Triple("nostalji", "Nostalji", Icons.Outlined.Theaters)).forEach { (k, l, ic) ->
                NavigationBarItem(selected = tab == k, onClick = { stack.clear(); stack.add(Route.Tab(k)) }, icon = { Icon(ic, l) },
                    label = { Text(l, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = C.coral,
                        selectedTextColor = C.coral, indicatorColor = C.surface2, unselectedIconColor = C.muted, unselectedTextColor = C.muted))
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (top) {
                is Route.Tab -> TabScreen(top.name, ::push, open, play) { scope.launch { Repo.refresh() } }
                is Route.Ep -> EpisodeScreen(top.id, top.autoplay, back = { stack.removeAt(stack.lastIndex) }, push = ::push)
                is Route.Ser -> SeriesScreen(top.key, back = { stack.removeAt(stack.lastIndex) }, open, play)
                Route.Search -> SearchScreen(back = { stack.removeAt(stack.lastIndex) }, push = ::push)
                Route.Notices -> NoticesScreen(back = { stack.removeAt(stack.lastIndex) }, open)
                Route.Settings -> SettingsScreen(back = { stack.removeAt(stack.lastIndex) }, push = ::push)
                Route.About -> AboutScreen(back = { stack.removeAt(stack.lastIndex) })
                Route.Calendar -> CalendarScreen(back = { stack.removeAt(stack.lastIndex) }, push = ::push)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScreen(tab: String, push: (Route) -> Unit, open: (Episode) -> Unit, play: (Episode) -> Unit, refresh: () -> Unit) {
    val series by Repo.series.collectAsStateWithLifecycle(); val eps by Repo.episodes.collectAsStateWithLifecycle()
    val follows by Repo.follows.collectAsStateWithLifecycle(); val likes by Repo.likes.collectAsStateWithLifecycle()
    val watched by Repo.watched.collectAsStateWithLifecycle(); val refreshing by Repo.refreshing.collectAsStateWithLifecycle()
    val notices by Repo.notices.collectAsStateWithLifecycle(); val seen by Repo.lastSeenNotices.collectAsStateWithLifecycle()
    val last by Repo.lastRefresh.collectAsStateWithLifecycle(); val lastOpened by Repo.lastOpened.collectAsStateWithLifecycle()
    var channel by remember(tab) { mutableStateOf("Tümü") }; var day by remember { mutableStateOf("all") }
    var onlyMine by rememberSaveable { mutableStateOf(true) }
    val allEps = eps.values.toList()
    // On home & week, show only the series the user follows (unless they switch to "Tüm kanallar").
    val mineOn = onlyMine && follows.isNotEmpty()
    val all = if (mineOn) allEps.filter { it.series in follows } else allEps
    val mySeries = if (mineOn) series.filter { it.key in follows } else series
    val unread = notices.count { it.at > seen }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Header(unread, push) }
            when (tab) {
                "home" -> {
                    if (follows.isNotEmpty()) item { MineToggle(onlyMine) { onlyMine = it } }
                    if (follows.isEmpty()) item { DiscoverCard(push) }
                    // Devam et — last opened episode that is online and not finished-watching
                    val cont = lastOpened?.let { eps[it] }?.takeIf { it.online }
                    if (cont != null && cont.id !in watched) item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).clickable { play(cont) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 7f)) {
                                Thumb(cont.thumb, Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(C.scrim))
                                Badge("DEVAM ET", C.coral, Modifier.padding(12.dp))
                                Box(Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape).background(Color(0x66FFFFFF)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                                    Text(cont.seriesName, style = T.title.copy(color = Color.White, fontSize = 20.sp))
                                    Text("${cont.num}. Bölüm · kaldığın yerden", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    // Yaklaşan bölümler — predicted next air for followed series
                    val upcoming = series.filter { it.key in follows && !it.ended }.mapNotNull { s -> nextAir(s)?.let { s to it } }
                        .sortedBy { it.second }.take(10)
                    if (upcoming.isNotEmpty()) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Yaklaşan bölümler", style = T.h2); Spacer(Modifier.weight(1f))
                            Text("Takvim", color = C.coral, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { push(Route.Calendar) }) } }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(upcoming, key = { it.first.key }) { (s, d) ->
                                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).background(C.surface).clickable { push(Route.Ser(s.key)) }) {
                                        Thumb(Repo.thumbOf(s.key), Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                        Column(Modifier.padding(10.dp)) {
                                            Text(s.name, style = T.title.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(whenLabel(d) + " · " + s.time, style = T.meta.copy(color = C.coral, fontWeight = FontWeight.SemiBold))
                                            ChannelTag(s.channel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val sorted = mySeries.sortedWith(compareByDescending<com.dizicambazi.app.data.Series> { it.key in follows }
                        .thenByDescending { Repo.episodesOf(it.key).firstOrNull { e -> e.online }?.airDate ?: "" })
                    if (sorted.isNotEmpty()) item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(sorted, key = { it.key }) { s ->
                                val lastE = Repo.episodesOf(s.key).firstOrNull { it.online }
                                val fr = lastE != null && isFresh(lastE, watched)
                                Column(Modifier.width(74.dp).clickable { push(Route.Ser(s.key)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(70.dp).clip(CircleShape).background(if (fr) C.ring else androidx.compose.ui.graphics.SolidColor(C.line)).padding(3.dp)) {
                                        Thumb(Repo.thumbOf(s.key), Modifier.fillMaxSize().clip(CircleShape).background(C.bg).padding(2.dp).clip(CircleShape))
                                    }
                                    Text(s.name, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = C.ink, maxLines = 2,
                                        textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                                }
                            }
                        }
                    }
                    val wd = Scraper.DAYS[today().dayOfWeek.value - 1]; val weekday = today().dayOfWeek.value <= 5
                    val tonight = mySeries.filter { !it.ended && (it.day == wd || (it.day == "Hafta içi her gün" && weekday)) }.sortedBy { it.time }
                    if (tonight.isNotEmpty()) {
                        item { Text("Bu akşam ekranda", style = T.h2) }
                        item {
                            val ctx = LocalContext.current
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(tonight, key = { it.key }) { s ->
                                    val l = Repo.episodesOf(s.key); val todayEp = l.firstOrNull { it.airDate == today().toString() }
                                    Box(Modifier.width(300.dp).aspectRatio(16f / 11f).clip(RoundedCornerShape(18.dp)).clickable {
                                        if (todayEp != null && todayEp.online) play(todayEp) else WebPlayerActivity.start(ctx, LIVE[s.channel] ?: s.page, "${s.channel} canlı yayın")
                                    }) {
                                        Thumb(Repo.thumbOf(s.key), Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(C.scrim))
                                        Badge(if (todayEp != null && todayEp.online) "İNTERNETTE" else "SAAT ${s.time}", mod = Modifier.padding(12.dp))
                                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                                            Text(s.name, style = T.title.copy(color = Color.White, fontSize = 21.sp))
                                            Row(verticalAlignment = Alignment.CenterVertically) { ChannelTag(s.channel, Color.White); Spacer(Modifier.width(8.dp))
                                                Text("${(todayEp?.num ?: l.firstOrNull()?.num?.plus(1))?.let { "$it. Bölüm · " } ?: ""}${s.time}", color = Color.White, fontSize = 13.sp) }
                                            Text(if (todayEp != null && todayEp.online) "▶ Bölümü izle" else "▶ Canlı izle", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val fresh = all.filter { it.online && daysAgo(it.airDate) in 0..2 }.sortedByDescending { it.airDate }
                    item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Yeni düşenler", style = T.h2); Spacer(Modifier.weight(1f))
                        Text("Tümü", color = C.coral, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { push(Route.Tab("week")) }) } }
                    if (fresh.isEmpty()) item { Empty("Son iki günde yeni bölüm yok", "Bu haftanın bölümleri \"Bu hafta\" sekmesinde.") }
                    items(fresh, key = { "f" + it.id }) { EpisodeCard(it, watched, follows, likes, open, play) }
                    val wk = all.filter { it.online && daysAgo(it.airDate) in 0..6 }; val sn = wk.count { it.id in watched }
                    if (wk.isNotEmpty()) item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.grad).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { sn.toFloat() / wk.size }, color = Color.White, trackColor = Color(0x55FFFFFF), strokeWidth = 6.dp, modifier = Modifier.size(62.dp))
                                Text("$sn", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Bu hafta ${wk.size} yeni bölüm", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text(if (sn > 0) "$sn tanesini izledin. ${wk.size - sn} bölüm seni bekliyor." else "Henüz hiçbirini izlemedin. Kaçırdıklarına göz at.", color = Color.White, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Kaçırdıklarıma bak", Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { push(Route.Tab("week")) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp), color = Color(0xFFC2243A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    val pend = all.filter { !it.online }
                    if (pend.isNotEmpty()) { item { Text("Yakında internette", style = T.h2) }; items(pend, key = { "p" + it.id }) { EpisodeRow(it, watched, follows, open, play) } }
                }
                "week" -> {
                    item { Text("Bu hafta yayınlananlar", style = T.h2); Text("Son 7 günde televizyonda yayınlanan bölümler. Aşağı çekerek yenile.", style = T.meta) }
                    if (follows.isNotEmpty()) item { MineToggle(onlyMine) { onlyMine = it } }
                    val days = (6 downTo 0).map { today().minusDays(it.toLong()) }
                    val wk = all.filter { daysAgo(it.airDate) in 0..6 }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { DayChip("Hafta", "7", "${wk.size} bölüm", day == "all") { day = "all" } }
                            items(days) { d -> val n = wk.count { it.airDate == d.toString() }
                                DayChip(if (d == today()) "Bugün" else d.dayOfWeek.getDisplayName(JTextStyle.SHORT, TR), "${d.dayOfMonth}", if (n > 0) "$n bölüm" else "—", day == d.toString(), n > 0) { day = d.toString() } }
                        }
                    }
                    val base = if (day == "all") wk else wk.filter { it.airDate == day }
                    item { ChannelChips(base.map { it.channel }, channel) { channel = it } }
                    val list = base.filter { channel == "Tümü" || it.channel == channel }.sortedWith(compareByDescending<Episode> { it.airDate }.thenBy { it.id in watched })
                    if (list.isEmpty()) item { Empty("Bölüm yok", "Başka bir gün ya da kanal seç.") }
                    list.groupBy { it.airDate }.forEach { (d, l) ->
                        item(key = "h$d") { Text("${rel(d)}  ·  ${LocalDate.parse(d).dayOfMonth} ${LocalDate.parse(d).month.getDisplayName(JTextStyle.FULL, TR)}", style = T.h2.copy(fontSize = 16.sp)) }
                        items(l, key = { "w" + it.id }) { EpisodeRow(it, watched, follows, open, play) }
                    }
                    val prevs = list.filter { it.online }.mapNotNull { e -> Repo.episodesOf(e.series).firstOrNull { it.num < e.num && it.online } }
                        .filter { it.id !in watched && it !in list }.distinct()
                    if (prevs.isNotEmpty()) {
                        item { Text("Önceki bölümler", style = T.h2); Text("Bu haftanın bölümlerinden önce gelenler. Kaçırdığın varsa buradan izle.", style = T.meta) }
                        items(prevs, key = { "pv" + it.id }) { EpisodeRow(it, watched, follows, open, play) }
                    }
                }
                "follow" -> {
                    item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Takip ettiklerin", style = T.h2); Spacer(Modifier.weight(1f))
                        if (follows.isNotEmpty()) Text("Takvim", color = C.coral, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { push(Route.Calendar) }) } }
                    if (follows.isEmpty()) item { Empty("Henüz takip ettiğin dizi yok", "Bir dizinin zil simgesine dokun. Yeni bölümü internete düşünce sana bildirim gelir.", "Dizileri keşfet") { push(Route.Tab("all")) } }
                    else {
                        val list = all.filter { it.series in follows }.sortedByDescending { it.airDate }
                        item { Text("${follows.size} dizi · ${list.count { it.online && it.id !in watched }} izlenmemiş bölüm", style = T.meta) }
                        items(list, key = { "t" + it.id }) { EpisodeCard(it, watched, follows, likes, open, play) }
                    }
                }
                "likes", "all" -> {
                    val src = if (tab == "likes") series.filter { it.key in likes } else series
                    item { Text(if (tab == "likes") "Beğendiklerin" else "Tüm diziler", style = T.h2)
                        if (tab == "all") Text("${series.size} dizi · ${series.map { it.channel }.distinct().size} kanal · aradığın yoksa üstteki büyüteçle ekle", style = T.meta) }
                    if (tab == "all") item { ChannelChips(series.map { it.channel }, channel) { channel = it } }
                    val list = src.filter { channel == "Tümü" || it.channel == channel }
                        .sortedByDescending { Repo.episodesOf(it.key).firstOrNull { e -> e.online }?.airDate ?: "" }
                    if (list.isEmpty()) item { Empty("Beğendiğin dizi yok", "Kalp simgesine dokunarak sevdiğin dizileri burada ayrı tutabilirsin.", "Dizileri keşfet") { push(Route.Tab("all")) } }
                    items(list.chunked(2), key = { r -> r.joinToString { it.key } }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { s -> Poster(s, likes, follows, watched, Modifier.weight(1f)) { push(Route.Ser(s.key)) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                "nostalji" -> {
                    item { Text("Nostalji", style = T.h2); Text("Final yapmış klasik diziler. Kartına dokununca tüm bölümleri resmi YouTube kanalında açılır.", style = T.meta) }
                    items(CLASSICS.chunked(2), key = { r -> r.joinToString { it.name } }) { row ->
                        val ctx = LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f).aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp)).clickable { WebPlayerActivity.openExternal(ctx, c.url) }) {
                                    Thumb(c.thumb, Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(C.scrim))
                                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                        Text(c.name, style = T.title.copy(color = Color.White, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        ChannelTag(c.channel, Color.White)
                                        Text("Tüm bölümler · YouTube", color = Color(0xE6FFFFFF), fontSize = 11.sp)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Text(if (last > 0) "Son güncelleme: " + java.text.SimpleDateFormat("d MMMM HH:mm", TR).format(java.util.Date(last)) + " · yenilemek için aşağı çek" else "Yenilemek için aşağı çek",
                    style = T.meta.copy(fontSize = 12.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

val LIVE = mapOf("atv" to "https://www.atv.com.tr/canli-yayin", "Show TV" to "https://www.showtv.com.tr/canli-yayin",
    "NOW" to "https://www.nowtv.com.tr/canli-yayin", "Star TV" to "https://www.startv.com.tr/canli-yayin",
    "Kanal 7" to "https://www.kanal7.com/canli-izle", "Kanal D" to "https://www.kanald.com.tr/canli-yayin",
    "TRT 1" to "https://www.tabii.com/tr/watch/live/trt1")

@Composable
fun Header(unread: Int, push: (Route) -> Unit) {
    val h = java.time.LocalTime.now(ZoneId.of("Europe/Istanbul")).hour
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(when { h < 6 -> "İyi geceler"; h < 12 -> "Günaydın"; h < 18 -> "İyi günler"; else -> "İyi akşamlar" }, style = T.meta)
            Row { Text("Dizi ", style = T.brand, color = C.ink); Text("Cambazı", style = T.brand, color = C.coral) }
        }
        HeadBtn(Icons.Filled.Search, "Dizi ara") { push(Route.Search) }
        Spacer(Modifier.width(8.dp))
        Box { HeadBtn(Icons.Outlined.NotificationsNone, "Bildirimler") { push(Route.Notices) }
            if (unread > 0) Text(if (unread > 9) "9+" else "$unread", Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).clip(RoundedCornerShape(50)).background(C.coral)
                .padding(horizontal = 6.dp, vertical = 1.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        HeadBtn(Icons.Outlined.Settings, "Ayarlar") { push(Route.Settings) }
    }
}

@Composable
fun HeadBtn(icon: ImageVector, desc: String, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(C.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = C.ink) }

@Composable
fun DayChip(top: String, mid: String, bot: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).background(if (on) C.grad else androidx.compose.ui.graphics.SolidColor(C.surface))
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val col = if (on) Color.White else if (enabled) C.ink else C.line
        Text(top.uppercase(TR), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else C.muted)
        Text(mid, fontSize = 22.sp, fontWeight = FontWeight.Black, color = col)
        Text(bot, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else C.coral)
    }
}

@Composable
fun ChannelChips(chans: List<String>, sel: String, onSel: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Tümü") + chans.distinct()) { c -> Pill(c, c == sel) { onSel(c) } } }
}

/** Toggle between "only what I follow" and the whole catalog on the home & week feeds. */
@Composable
fun MineToggle(onlyMine: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Takip ettiklerim", onlyMine) { onChange(true) }
        Pill("Tüm kanallar", !onlyMine) { onChange(false) }
    }
}

@Composable
fun DiscoverCard(push: (Route) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.grad).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("İzlediğin dizileri seç", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Text("Takip ettiğin diziler burada toplanır, yeni bölümleri düşünce haber verilir. Şu an hiç dizi takip etmiyorsun.", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dizilere göz at", Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { push(Route.Tab("all")) }.padding(horizontal = 16.dp, vertical = 9.dp), color = Color(0xFFC2243A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Dizi ara", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { push(Route.Search) }.padding(horizontal = 16.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun TopBack(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = C.ink) }
        Text(title, style = T.h2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EpisodeScreen(id: String, autoplay: Boolean, back: () -> Unit, push: (Route) -> Unit) {
    val ctx = LocalContext.current
    val eps by Repo.episodes.collectAsStateWithLifecycle(); val watched by Repo.watched.collectAsStateWithLifecycle()
    val follows by Repo.follows.collectAsStateWithLifecycle(); val likes by Repo.likes.collectAsStateWithLifecycle()
    val comments by Repo.comments.collectAsStateWithLifecycle()
    val e = eps[id] ?: run { Empty("Bölüm bulunamadı", "Liste yenilenmiş olabilir.", "Geri dön", back); return }
    var playing by remember(id) { mutableStateOf(autoplay && e.playsInApp) }
    LaunchedEffect(playing) { if (playing) Repo.setWatched(e.id, true) }
    var text by remember(id) { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                val yt = e.youtubeId?.takeIf { e.playsInApp }
                if (playing && yt != null) InlineYouTube(yt, Modifier.fillMaxSize()) {
                    playing = false
                    android.widget.Toast.makeText(ctx, "Bu bölüm uygulamada oynatılamadı, kanalın sitesinde açıyorum", android.widget.Toast.LENGTH_LONG).show()
                    playExternalFallback(ctx, e.copy(sources = e.sources.filter { it.youtubeId == null }.ifEmpty { e.sources }))
                } else {
                    Thumb(e.thumb, Modifier.fillMaxSize(), dim = !e.online)
                    if (e.online) Box(Modifier.align(Alignment.Center).size(70.dp).clip(CircleShape).background(Color(0x66FFFFFF)).clickable {
                        if (yt != null) playing = true else { Repo.setWatched(e.id, true); playExternalFallback(ctx, e) }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Oynat", tint = Color.White, modifier = Modifier.size(42.dp)) }
                }
                if (!playing) IconButton(back, Modifier.padding(8.dp).clip(CircleShape).background(Color(0x88000000))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (playing) Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = C.ink) }; Text("Uygulama içinde oynatılıyor", style = T.meta) }
                Text(e.seriesName, style = T.h2.copy(fontSize = 26.sp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${e.num}. Bölüm", fontWeight = FontWeight.Bold, color = C.ink); Spacer(Modifier.width(10.dp)); ChannelTag(e.channel)
                    Spacer(Modifier.width(10.dp)); Text(rel(e.airDate) + if (e.duration.isNotEmpty()) " · ${e.duration}" else "", style = T.meta) }
                if (!e.online) Text("Bu bölüm televizyonda yayınlandı ama kanal henüz internete yüklemedi. Yüklendiğinde bildirim gelir.", style = T.meta)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.playsInApp && !playing) GradButton("Uygulamada izle", big = true) { playing = true }
                    else if (!e.playsInApp) GradButton("İzle", big = true) { Repo.setWatched(e.id, true); playExternalFallback(ctx, e) }
                }
                if (e.online) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.siteSource?.let { s -> Pill("${s.label}'da izle") { Repo.setWatched(e.id, true); WebPlayerActivity.start(ctx, s.url, "${e.seriesName} · ${e.num}. bölüm") } }
                    Pill("Tarayıcıda aç") { WebPlayerActivity.openExternal(ctx, (e.siteSource ?: e.sources.first()).url) }
                    if (e.youtubeId != null && !e.playsInApp) Pill("YouTube") { WebPlayerActivity.openExternal(ctx, e.sources.first { it.youtubeId != null }.url) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (e.id in watched) "✓ İzlendi" else "İzledim", e.id in watched) { Repo.setWatched(e.id, e.id !in watched) }
                    Pill(if (e.series in follows) "✓ Takipte" else "+ Takip et", e.series in follows) { Repo.toggleFollow(e.series) }
                    Pill(if (e.series in likes) "♥ Beğendin" else "♡ Beğen", e.series in likes) { Repo.toggleLike(e.series) }
                }
                if (e.desc.isNotEmpty()) Text(e.desc, style = T.meta.copy(fontSize = 14.sp))
                val others = Repo.episodesOf(e.series).filter { it.id != e.id }
                if (others.isNotEmpty()) {
                    Text("Diğer bölümler", style = T.meta.copy(fontWeight = FontWeight.SemiBold))
                    others.forEach { o -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface2).clickable { push(Route.Ep(o.id)) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(o.thumb, Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp))); Spacer(Modifier.width(10.dp))
                        Column { Text("${o.num}. Bölüm" + if (o.id in watched) " · İzlendi" else "", fontWeight = FontWeight.Bold, color = C.ink, fontSize = 14.sp)
                            Text(if (o.online) rel(o.airDate) else "Yüklenmesi bekleniyor", style = T.meta) } } }
                }
                val mine = comments.filter { it.episode == e.id }
                Text("Yorumlar  ${mine.size}", style = T.title)
                if (mine.isEmpty()) Text("Bu bölüme henüz yorum yazmadın.", style = T.meta)
                mine.forEach { c -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface2).padding(12.dp)) {
                    Row { Text("Sen", fontWeight = FontWeight.Bold, color = C.ink, fontSize = 12.sp); Spacer(Modifier.weight(1f))
                        Text(java.text.SimpleDateFormat("d MMM HH:mm", TR).format(java.util.Date(c.at)) + "  · Sil", style = T.meta.copy(fontSize = 12.sp), modifier = Modifier.clickable { Repo.deleteComment(c.id) }) }
                    Text(c.text, style = T.body) } }
                OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth(), placeholder = { Text("Bu bölüm hakkında ne düşünüyorsun?") }, minLines = 3)
                Box(Modifier.align(Alignment.End)) { Pill("Gönder", true) { if (text.isNotBlank()) { Repo.addComment(e.id, text); text = "" } } }
            }
        }
    }
}

@Composable
fun SeriesScreen(key: String, back: () -> Unit, open: (Episode) -> Unit, play: (Episode) -> Unit) {
    val series by Repo.series.collectAsStateWithLifecycle(); val follows by Repo.follows.collectAsStateWithLifecycle()
    val likes by Repo.likes.collectAsStateWithLifecycle(); val watched by Repo.watched.collectAsStateWithLifecycle()
    Repo.episodes.collectAsStateWithLifecycle().value
    val s = series.firstOrNull { it.key == key } ?: run { back(); return }
    val ctx = LocalContext.current
    var expanded by remember(key) { mutableStateOf(false) }
    var loadingInfo by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (s.infoAt == 0L || System.currentTimeMillis() - s.infoAt > 30L * 86_400_000) {
            loadingInfo = true
            val info = withContext(Dispatchers.IO) { runCatching { Scraper.fetchInfo(s.name) }.getOrNull() }
            if (info != null) Repo.updateSeriesInfo(key, info.summary, info.poster, info.url)
            else Repo.updateSeriesInfo(key, s.summary, s.poster, s.wikiUrl) // stamp infoAt so we don't refetch constantly
            loadingInfo = false
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { Thumb(Repo.thumbOf(key), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(C.scrim))
            IconButton(back, Modifier.padding(8.dp).clip(CircleShape).background(Color(0x88000000))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color.White) } } }
        item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.name, style = T.h2.copy(fontSize = 28.sp))
            Row(verticalAlignment = Alignment.CenterVertically) { ChannelTag(s.channel); Spacer(Modifier.width(10.dp)); Text(if (s.ended) "Final yaptı" else listOf(s.day, s.time).filter { it.isNotEmpty() }.joinToString(" "), style = T.meta) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (key in follows) "✓ Takipte" else "+ Takip et", key in follows) { Repo.toggleFollow(key) }
                Pill(if (key in likes) "♥ Beğendin" else "♡ Beğen", key in likes) { Repo.toggleLike(key) }
                if (s.page.isNotEmpty()) Pill("Tüm bölümler") { WebPlayerActivity.start(ctx, s.page, s.name) }
            }
            if (s.note.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.surface2).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = C.amber, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(s.note, style = T.meta.copy(fontSize = 13.sp))
            }
            // Rich info from Wikipedia (best-effort)
            if (s.summary.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.poster.isNotEmpty()) Thumb(s.poster, Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(s.summary, style = T.meta.copy(fontSize = 14.sp, lineHeight = 20.sp), maxLines = if (expanded) 40 else 5, overflow = TextOverflow.Ellipsis)
                        Text(if (expanded) "Daha az" else "Devamını oku", color = C.coral, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp))
                    }
                }
                if (s.wikiUrl.isNotEmpty()) Text("Bilgi kaynağı: Vikipedi", style = T.meta.copy(fontSize = 12.sp), modifier = Modifier.clickable { WebPlayerActivity.openExternal(ctx, s.wikiUrl) })
            } else if (loadingInfo) Text("Dizi bilgisi yükleniyor…", style = T.meta)
            if (s.userAdded) Text("Listeden çıkar", color = C.coral, fontSize = 13.sp, modifier = Modifier.clickable { Repo.removeSeries(key); back() })
        } }
        item { Text("Bölümler", style = T.h2.copy(fontSize = 18.sp), modifier = Modifier.padding(horizontal = 16.dp)) }
        items(Repo.episodesOf(key), key = { it.id }) { Box(Modifier.padding(horizontal = 16.dp)) { EpisodeRow(it, watched, follows, open, play) } }
    }
}

@Composable
fun SearchScreen(back: () -> Unit, push: (Route) -> Unit) {
    val series by Repo.series.collectAsStateWithLifecycle(); val follows by Repo.follows.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<Scraper.Candidate>?>(null) }
    val scope = rememberCoroutineScope()
    val fq = Scraper.ascii(q.trim())
    val hits = if (fq.isEmpty()) emptyList() else series.filter { Scraper.ascii(it.name).contains(fq) || Scraper.ascii(it.channel).contains(fq) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBack("Dizi ara", back) }
        item { OutlinedTextField(q, { q = it; found = null }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Dizi adı yaz, ör. Kuruluş Orhan") },
            leadingIcon = { Icon(Icons.Filled.Search, null) }) }
        if (fq.isEmpty()) item { Text("Listedeki ${series.size} dizi arasında ara. Aradığın yoksa internette arayıp listene ekleyebilirsin.", style = T.meta) }
        if (hits.isNotEmpty()) { item { Text("Listende", style = T.meta.copy(fontWeight = FontWeight.SemiBold)) }
            items(hits, key = { it.key }) { s -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).clickable { push(Route.Ser(s.key)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(Repo.thumbOf(s.key), Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(s.name, style = T.title.copy(fontSize = 16.sp)); ChannelTag(s.channel); Text("${s.day} ${s.time}", style = T.meta) }
                Pill(if (s.key in follows) "✓" else "+ Takip", s.key in follows) { Repo.toggleFollow(s.key) } } } }
        if (q.trim().length >= 2 && series.none { Scraper.ascii(it.name) == fq }) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("“${q.trim()}” internette aransın mı?", style = T.title.copy(fontSize = 17.sp))
                Text("Kanalların sitelerinde (atv, Show TV, Kanal D, NOW, TRT 1, Kanal 7) ve resmi YouTube kanallarında ararım. Bulursam bölümleriyle birlikte listene eklersin.", style = T.meta)
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Aranıyor…", style = T.meta) }
                else GradButton("İnternette ara") { busy = true; scope.launch { found = withContext(Dispatchers.IO) { runCatching { Scraper.discover(q) }.getOrDefault(emptyList()) }; busy = false } }
            }
        }
        found?.let { list ->
            if (list.isEmpty()) item { Empty("Bulunamadı", "Bu isimle yayında olan bir dizi bulamadım. Adını farklı yazmayı dene (ör. tam adı).") }
            list.forEach { c -> item(key = "c" + c.series.key) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.preview.firstOrNull()?.let { Thumb(it.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) }
                    Text(c.series.name, style = T.title); Row { ChannelTag(c.series.channel); Spacer(Modifier.width(8.dp)); Text(listOf(c.series.day, "${c.preview.size} bölüm bulundu").filter { it.isNotEmpty() }.joinToString(" · "), style = T.meta) }
                    c.preview.forEach { Text("• ${it.num}. Bölüm · ${rel(it.airDate)}", style = T.meta) }
                    GradButton("Listeye ekle ve takip et") { Repo.addSeries(c); push(Route.Ser(c.series.key)) }
                } } }
        }
    }
}

@Composable
fun NoticesScreen(back: () -> Unit, open: (Episode) -> Unit) {
    val notices by Repo.notices.collectAsStateWithLifecycle(); val eps by Repo.episodes.collectAsStateWithLifecycle()
    val seen = remember { Repo.lastSeenNotices.value }
    LaunchedEffect(Unit) { Repo.markNoticesSeen() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopBack("Bildirimler", back) }
        item { Text("Uygulama her 2 saatte bir kanalları kontrol eder. Takip ettiğin bir dizinin yeni bölümü internete düşünce telefonuna bildirim gelir.", style = T.meta) }
        if (notices.isEmpty()) item { Empty("Henüz bildirim yok", "Takip ettiğin dizilerin yeni bölümleri burada listelenir.") }
        items(notices.reversed(), key = { it.episodeId + it.at }) { n -> val e = eps[n.episodeId]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (n.at > seen) C.surface2 else C.surface).clickable { e?.let(open) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e != null) Thumb(e.thumb, Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp))); Spacer(Modifier.width(10.dp))
                Column { Text(n.title, fontWeight = FontWeight.Bold, color = C.ink, fontSize = 14.sp)
                    Text(java.text.SimpleDateFormat("d MMMM HH:mm", TR).format(java.util.Date(n.at)), style = T.meta) } } }
    }
}

/* ---------- v2 helpers & screens ---------- */

/** Predicted next broadcast date for a followed series, from its weekday and the latest known episode. */
fun nextAir(s: com.dizicambazi.app.data.Series): LocalDate? {
    val lastDate = Repo.episodesOf(s.key).firstOrNull { it.online }?.let { runCatching { LocalDate.parse(it.airDate) }.getOrNull() }
    if (s.day == "Hafta içi her gün") {
        var d = today(); while (d.dayOfWeek.value > 5) d = d.plusDays(1)
        if (lastDate != null && !lastDate.isBefore(today())) { d = d.plusDays(1); while (d.dayOfWeek.value > 5) d = d.plusDays(1) }
        return d
    }
    val wd = Scraper.DAYS.indexOf(s.day); if (wd < 0) return null
    val target = DayOfWeek.of(wd + 1); var d = today()
    while (d.dayOfWeek != target) d = d.plusDays(1)
    if (lastDate != null && !lastDate.isBefore(d)) d = d.plusWeeks(1)
    return d
}

fun whenLabel(d: LocalDate): String {
    val n = java.time.temporal.ChronoUnit.DAYS.between(today(), d)
    return when { n == 0L -> "Bugün"; n == 1L -> "Yarın"; n in 2..6 -> d.dayOfWeek.getDisplayName(JTextStyle.FULL, TR).replaceFirstChar { it.uppercase() }
        else -> "${d.dayOfMonth} ${d.month.getDisplayName(JTextStyle.FULL, TR)}" }
}

@Composable
fun OnboardingScreen() {
    val series by Repo.series.collectAsStateWithLifecycle()
    val follows by Repo.follows.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 8.dp)) {
            Row { Text("Dizi ", style = T.brand, color = C.ink); Text("Cambazı", style = T.brand, color = C.coral) }
            Spacer(Modifier.height(8.dp))
            Text("İzlediğin dizileri seç. Yeni bölümleri internete düşünce haber veririm. İstediğin zaman değiştirebilirsin.", style = T.meta)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(series.sortedBy { it.name.lowercase(TR) }.chunked(2), key = { r -> r.joinToString { it.key } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s ->
                        val on = s.key in follows
                        Box(Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)).clickable { Repo.toggleFollow(s.key) }) {
                            Thumb(Repo.thumbOf(s.key), Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(C.scrim))
                            if (on) Box(Modifier.fillMaxSize().background(Color(0x553FD37A)))
                            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(if (on) C.ok else Color(0x88000000)), contentAlignment = Alignment.Center) {
                                Icon(if (on) Icons.Filled.Check else Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                Text(s.name, style = T.title.copy(color = Color.White, fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                ChannelTag(s.channel, Color.White)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.background(C.surface).padding(16.dp).padding(bottom = 8.dp)) {
            GradButton(if (follows.isEmpty()) "Şimdilik geç" else "${follows.size} dizi ile başla", Modifier.fillMaxWidth(), big = true) { Repo.finishOnboarding() }
        }
    }
}

@Composable
fun SettingsScreen(back: () -> Unit, push: (Route) -> Unit) {
    val ctx = LocalContext.current
    val mode by Repo.themeMode.collectAsStateWithLifecycle()
    val notif by Repo.notifEnabled.collectAsStateWithLifecycle()
    val qs by Repo.quietStart.collectAsStateWithLifecycle(); val qe by Repo.quietEnd.collectAsStateWithLifecycle()
    val rh by Repo.refreshHours.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBack("Ayarlar", back)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingCard("Görünüm") {
                Text("Tema", style = T.body.copy(fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Sistem", mode == "system") { Repo.setTheme("system") }
                    Pill("Koyu", mode == "dark") { Repo.setTheme("dark") }
                    Pill("Açık", mode == "light") { Repo.setTheme("light") }
                }
            }
            SettingCard("Bildirimler") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Yeni bölüm bildirimi", style = T.body.copy(fontWeight = FontWeight.SemiBold))
                        Text("Takip ettiğin dizilerin bölümü internete düşünce", style = T.meta) }
                    Switch(notif, { Repo.setNotif(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.coral))
                }
                Text("Sessiz saatler", style = T.body.copy(fontWeight = FontWeight.SemiBold))
                Text("Bu saatler arasında bildirim gönderilmez.", style = T.meta)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HourStepper("Başlangıç", qs) { Repo.setQuiet(it, if (qe < 0) 7 else qe) }
                    HourStepper("Bitiş", qe) { Repo.setQuiet(if (qs < 0) 0 else qs, it) }
                    if (qs >= 0 || qe >= 0) Text("Kapat", color = C.coral, fontSize = 13.sp, modifier = Modifier.clickable { Repo.setQuiet(-1, -1) })
                }
                Text("Arka planda kontrol sıklığı", style = T.body.copy(fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2 to "2 saat", 6 to "6 saat", 12 to "12 saat", 24 to "Günde 1").forEach { (h, l) ->
                        Pill(l, rh == h) { Repo.setRefreshHours(h); RefreshWorker.schedule(ctx, h) }
                    }
                }
            }
            SettingCard("Uygulama") {
                Row(Modifier.fillMaxWidth().clickable { push(Route.About) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hakkında ve yasal bilgiler", style = T.body, modifier = Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null, tint = C.muted)
                }
                val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                Text("Sürüm $v", style = T.meta)
            }
        }
    }
}

@Composable
fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(TR), style = T.meta.copy(color = C.coral, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun HourStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = T.meta)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onChange(((if (value < 0) 0 else value) + 23) % 24) }, Modifier.size(32.dp)) { Icon(Icons.Filled.Remove, "azalt", tint = C.ink) }
            Text(if (value < 0) "--" else "%02d:00".format(value), Modifier.width(52.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = C.ink)
            IconButton({ onChange(((if (value < 0) -1 else value) + 1) % 24) }, Modifier.size(32.dp)) { Icon(Icons.Filled.Add, "artır", tint = C.ink) }
        }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBack("Hakkında ve yasal", back)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row { Text("Dizi ", style = T.brand.copy(fontSize = 26.sp), color = C.ink); Text("Cambazı", style = T.brand.copy(fontSize = 26.sp), color = C.coral) }
            AboutBlock("Ne yapar?", "Türkiye'deki büyük kanalların (atv, Show TV, Star TV, Kanal D, NOW, TRT 1, Kanal 7) dizilerinin yeni bölümlerini tek ekranda toplar. Bölümleri kanalların kendi resmi sitelerinde ve resmi YouTube kanallarında bulur.")
            AboutBlock("İçerik barındırmaz", "Bu uygulama hiçbir dizi dosyası saklamaz veya yayınlamaz. Yalnızca kanalların kendi yayınlarına bağlantı verir. Bölümlerin tüm hakları ilgili kanallara ve yapımcılara aittir. İzleme, kanalın resmi oynatıcısında ya da YouTube'da gerçekleşir.")
            AboutBlock("Gizlilik", "Takip, beğeni, izleme işaretleri ve yorumların yalnızca senin telefonunda saklanır. Hiçbir veri bir sunucuya gönderilmez, hesap istenmez, reklam ve takip aracı yoktur. İnternet bağlantısı sadece kanal sitelerini, YouTube'u ve bölüm bilgisini (Vikipedi) okumak için kullanılır.")
            AboutBlock("İzinler", "• İnternet: bölümleri ve bilgileri çekmek için.\n• Bildirim: takip ettiğin dizilerin yeni bölümünü haber vermek için. İstersen ayarlardan kapatabilirsin.")
            AboutBlock("Kaynaklar", "Bölümler: ilgili kanalların resmi web siteleri ve resmi YouTube kanalları. Dizi özetleri ve afişler: Vikipedi (CC BY-SA).")
            Pill("Projeyi GitHub'da aç") { WebPlayerActivity.openExternal(ctx, "https://github.com/borgatelefondeneme-arch/dizi-cambazi") }
            val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
            Text("Sürüm $v", style = T.meta)
        }
    }
}

@Composable
fun AboutBlock(h: String, b: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(h, style = T.title.copy(fontSize = 17.sp))
        Text(b, style = T.meta.copy(fontSize = 14.sp, lineHeight = 20.sp))
    }
}

@Composable
fun CalendarScreen(back: () -> Unit, push: (Route) -> Unit) {
    val series by Repo.series.collectAsStateWithLifecycle()
    val follows by Repo.follows.collectAsStateWithLifecycle()
    Repo.episodes.collectAsStateWithLifecycle().value
    val items = series.filter { it.key in follows && !it.ended }.mapNotNull { s -> nextAir(s)?.let { s to it } }.sortedBy { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopBack("Takvim", back) }
        item { Text("Takip ettiğin dizilerin yaklaşan bölümleri. Tahmini yayın günleri kanalların haftalık düzenine göredir.", style = T.meta.copy(fontSize = 13.sp), modifier = Modifier.padding(horizontal = 16.dp)) }
        if (items.isEmpty()) item { Box(Modifier.padding(16.dp)) { Empty("Takvim boş", "Dizileri takip ettikçe yaklaşan bölümleri burada gün gün görürsün.", "Dizilere göz at") { push(Route.Tab("all")) } } }
        items.groupBy { it.second }.forEach { (d, list) ->
            item(key = "cal$d") { Text(whenLabel(d) + "  ·  " + "${d.dayOfMonth} ${d.month.getDisplayName(JTextStyle.FULL, TR)}", style = T.h2.copy(fontSize = 16.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(list, key = { "c" + it.first.key }) { (s, _) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(C.surface).clickable { push(Route.Ser(s.key)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(Repo.thumbOf(s.key), Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp))); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(s.name, style = T.title.copy(fontSize = 16.sp)); ChannelTag(s.channel)
                        Text("Saat ${s.time}", style = T.meta) }
                    Repo.episodesOf(s.key).firstOrNull { it.online }?.let { Text("${it.num + 1}. bölüm", style = T.meta.copy(color = C.coral, fontWeight = FontWeight.SemiBold)) }
                }
            }
        }
    }
}
