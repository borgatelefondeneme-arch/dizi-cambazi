package com.bolumvakti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bolumvakti.app.data.Episode
import com.bolumvakti.app.data.Repo
import com.bolumvakti.app.data.Series
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

val TR = Locale("tr")
fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Istanbul"))
fun daysAgo(d: String) = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(d), today()) }.getOrDefault(99)
fun rel(d: String): String {
    val n = daysAgo(d); val ld = runCatching { LocalDate.parse(d) }.getOrNull() ?: return d
    return when { n <= 0 -> "Bugün"; n == 1L -> "Dün"; n < 7 -> ld.dayOfWeek.getDisplayName(JTextStyle.FULL, TR).replaceFirstChar { it.uppercase() }
        else -> "${ld.dayOfMonth} ${ld.month.getDisplayName(JTextStyle.FULL, TR)}" }
}
fun isFresh(e: Episode, watched: Set<String>) = e.online && daysAgo(e.airDate) in 0..3 && e.id !in watched

@Composable
fun ChannelTag(c: String, color: Color = C.muted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(C.channel(c)))
        Spacer(Modifier.width(5.dp)); Text(c, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun Pill(text: String, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).then(if (on) Modifier.background(C.ink) else Modifier.border(1.5.dp, C.line, RoundedCornerShape(50)))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = if (on) C.bg else C.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GradButton(text: String, modifier: Modifier = Modifier, big: Boolean = false, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(C.grad).clickable(onClick = onClick)
        .padding(horizontal = if (big) 22.dp else 16.dp, vertical = if (big) 13.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(if (big) 22.dp else 18.dp))
        Spacer(Modifier.width(6.dp)); Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (big) 16.sp else 14.sp)
    }
}

@Composable
fun Badge(text: String, bg: Color = C.coral, mod: Modifier = Modifier) =
    Text(text, mod.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)

@Composable
fun Thumb(url: String, modifier: Modifier, dim: Boolean = false) {
    Box(modifier.background(C.surface2)) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            alpha = if (dim) 0.45f else 1f)
    }
}

@Composable
fun EpisodeCard(e: Episode, watched: Set<String>, follows: Set<String>, likes: Set<String>, open: (Episode) -> Unit, play: (Episode) -> Unit) {
    val w = e.id in watched; val pend = !e.online
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable { open(e) }) {
            Thumb(e.thumb, Modifier.fillMaxSize(), dim = w || pend)
            when { pend -> Badge("Yüklenmesi bekleniyor", Color(0x99000000), Modifier.padding(10.dp))
                w -> Badge("✓ İzlendi", Color(0x99000000), Modifier.padding(10.dp))
                isFresh(e, watched) -> Badge("YENİ", mod = Modifier.padding(10.dp)) }
            if (!pend) Box(Modifier.align(Alignment.Center).size(58.dp).clip(CircleShape).background(Color(0x55FFFFFF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
            if (e.duration.isNotEmpty()) Text(e.duration, Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(8.dp))
                .background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            Text(e.seriesName, style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${e.num}. Bölüm", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = C.ink); Spacer(Modifier.width(10.dp))
                ChannelTag(e.channel); Spacer(Modifier.width(10.dp)); Text(rel(e.airDate), style = T.meta)
            }
        }
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pend) Pill("Ayrıntılar") { open(e) } else GradButton("İzle") { play(e) }
            Spacer(Modifier.weight(1f))
            IconButton({ Repo.toggleLike(e.series) }) { Icon(if (e.series in likes) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Beğen", tint = if (e.series in likes) C.heart else C.muted) }
            IconButton({ Repo.toggleFollow(e.series) }) { Icon(if (e.series in follows) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone, "Takip et", tint = if (e.series in follows) C.coral else C.muted) }
            IconButton({ open(e) }) { Icon(Icons.Filled.ChatBubbleOutline, "Yorumlar", tint = C.muted) }
        }
    }
}

@Composable
fun EpisodeRow(e: Episode, watched: Set<String>, follows: Set<String>, open: (Episode) -> Unit, play: (Episode) -> Unit) {
    val w = e.id in watched; val pend = !e.online
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).clickable { open(e) }.padding(8.dp)) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(11.dp))) {
            Thumb(e.thumb, Modifier.fillMaxSize(), dim = w || pend)
            if (w) Badge("✓", Color(0x99000000), Modifier.padding(6.dp)) else if (isFresh(e, watched)) Badge("YENİ", mod = Modifier.padding(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.seriesName, style = T.title.copy(fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("${e.num}. Bölüm", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = C.ink); Spacer(Modifier.width(8.dp)); ChannelTag(e.channel) }
            Text(if (pend) "Yayınlandı, internete yüklenmesi bekleniyor" else rel(e.airDate) + if (e.duration.isNotEmpty()) " · ${e.duration}" else "", style = T.meta)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!pend) GradButton("İzle") { play(e) }
                Spacer(Modifier.weight(1f))
                IconButton({ Repo.toggleFollow(e.series) }) { Icon(if (e.series in follows) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone, "Takip et", tint = if (e.series in follows) C.coral else C.muted) }
            }
        }
    }
}

@Composable
fun Poster(s: Series, likes: Set<String>, follows: Set<String>, watched: Set<String>, modifier: Modifier, onClick: () -> Unit) {
    val last = Repo.episodesOf(s.key).firstOrNull { it.online }
    Box(modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Thumb(Repo.thumbOf(s.key), Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(C.scrim))
        Row(Modifier.fillMaxWidth().padding(10.dp)) {
            if (last != null && isFresh(last, watched)) Badge("YENİ")
            Spacer(Modifier.weight(1f))
            if (s.key in likes) Icon(Icons.Filled.Favorite, null, tint = C.heart) else if (s.key in follows) Icon(Icons.Filled.Notifications, null, tint = Color.White)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(s.name, style = T.title.copy(fontSize = 17.sp, color = Color.White), maxLines = 3)
            ChannelTag(s.channel, Color.White)
            Text("${s.day} ${s.time}".trim() + (last?.let { " · ${it.num}. bölüm" } ?: ""), fontSize = 12.sp, color = Color(0xE6FFFFFF))
        }
    }
}

@Composable
fun Empty(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = T.title); Spacer(Modifier.height(6.dp)); Text(text, style = T.meta)
        if (action != null) { Spacer(Modifier.height(12.dp)); Box(Modifier.clip(RoundedCornerShape(50)).background(C.grad).clickable(onClick = onAction).padding(horizontal = 18.dp, vertical = 10.dp)) { Text(action, color = Color.White, fontWeight = FontWeight.Bold) } }
    }
}
