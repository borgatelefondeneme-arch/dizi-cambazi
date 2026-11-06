# Google Play yükleme notları

Uygulama Play Store'a yüklemeye hazırdır. Gerekenler ve mevcut durum:

- **Paket adı:** com.dizicambazi.app
- **Sürüm:** 2.0 (versionCode 2)
- **Gizlilik politikası URL'si (zorunlu):** https://github.com/borgatelefondeneme-arch/dizi-cambazi/blob/main/PRIVACY.md
- **Veri güvenliği formu:** "Veri toplanmıyor / paylaşılmıyor" olarak doldurulur (uygulama sunucuya veri göndermez).
- **İçerik derecelendirmesi:** Genel; uygulama içerik barındırmaz, yalnızca resmi kaynaklara bağlantı verir.
- **İmzalı sürüm:** Play için imzalı bir `release` AAB gerekir. Android Studio → Build → Generate Signed Bundle/APK ile üretilebilir. (Depodaki hazır dosya `debug` imzalıdır; yan yüklemeye uygundur, Play'e yüklenecek sürüm imzalanmalıdır.)
- **Gereken görseller:** uygulama ikonu (uygulamada var), en az 2 ekran görüntüsü, 1024×500 tanıtım görseli. Bunlar Play Console'da eklenir.

## Yasal konum
Uygulama yalnızca kanalların resmi web siteleri ve resmi YouTube kanallarındaki yayınlara bağlantı verir; hiçbir içerik barındırmaz veya kopyalamaz. Bu, haber/rehber niteliğinde bir "derleyici" (aggregator) yaklaşımıdır. Yine de bir kanal, içeriğine bağlantı verilmesini istemezse ilgili dizi listeden çıkarılabilir.
