# Bölüm Vakti

Türkiye'deki büyük kanalların (atv, Show TV, Star TV, Kanal D, NOW, TRT 1, Kanal 7) dizilerinin yeni bölümlerini kapak görselleriyle tek ekranda toplayan Android uygulaması.

## Özellikler
- **Yeni düşenler ve bu hafta:** Son 7 günde yayınlanan bölümler gün gün, önceki bölümleriyle birlikte.
- **Uygulama içinde izleme:** YouTube videosu başka uygulamalarda oynatılmaya izin veriyorsa bölüm uygulamanın içinde oynar. İzin vermiyorsa ya da hata çıkarsa kanalın kendi sitesi uygulama içinde açılır. O da açılmazsa telefonun tarayıcısına yönlendirilir.
- **İsimle arama:** Listede olmayan bir diziyi adıyla arayıp kanal sitelerinden ve resmi YouTube kanallarından bulup listene ekleyebilirsin.
- **Bildirim:** Uygulama arka planda her 2 saatte bir kontrol eder. Takip ettiğin dizinin yeni bölümü internete düşünce bildirim gelir.
- Takip, beğeni, "izledim" işareti ve bölüm yorumları. Hepsi telefonda saklanır.

## Kurulum
- **Hazır APK:** [Releases](../../releases/latest) sayfasından `BolumVakti.apk` dosyasını telefona indirip aç. Android bilinmeyen kaynaktan yükleme izni isteyecek.
- **Kaynaktan:** Projeyi Android Studio'da aç (File → Open) ve çalıştır. Gerekenler: Android SDK 35, JDK 17 ya da üstü.

## Yapı
- `app/src/main/java/com/bolumvakti/app/data/Scraper.kt`: Kanal sitelerinden ve YouTube'dan bölümleri okur.
- `data/Repo.kt`: Yerel kayıt, yenileme ve bildirim listesi.
- `work/RefreshWorker.kt`: Arka planda kontrol ve bildirim.
- `ui/`: Compose ekranları, uygulama içi YouTube oynatıcı (`YouTubePlayer.kt`) ve site oynatıcı (`WebPlayerActivity.kt`).
- `app/src/main/assets/seed.json`: İlk açılışta gösterilen başlangıç listesi.

## Not
Bölümlerin tüm hakları ilgili kanallara aittir. Uygulama bölüm dosyası barındırmaz; yalnızca kanalların kendi sitelerindeki ve resmi YouTube kanallarındaki yayınlara bağlantı verir.
