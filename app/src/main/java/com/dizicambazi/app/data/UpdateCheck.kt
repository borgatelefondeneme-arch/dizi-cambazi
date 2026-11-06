package com.dizicambazi.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Play Store dışı dağıtım için basit "güncelleme var mı" kontrolü. GitHub'daki latest.json okunur. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val url: String, val notes: String)

object UpdateCheck {
    private const val URL = "https://raw.githubusercontent.com/borgatelefondeneme-arch/dizi-cambazi/main/docs/latest.json"
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

    fun fetch(): UpdateInfo? = try {
        client.newCall(Request.Builder().url(URL).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) null else {
                val o = JSONObject(r.body?.string() ?: "")
                UpdateInfo(o.optInt("versionCode"), o.optString("versionName"), o.optString("url"), o.optString("notes"))
            }
        }
    } catch (e: Exception) { null }
}
