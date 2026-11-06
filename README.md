# Dizi Cambazı

Türkiye'deki büyük kanalların (atv, Show TV, Star TV, Kanal D, NOW, TRT 1, Kanal 7) dizilerinin yeni bölümlerini kapak görselleriyle tek ekranda toplayan Android uygulaması.

## Özellikler
- **Yeni düşenler, bu hafta ve takvim:** Son 7 günde yayınlanan bölümler gün gün; takip ettiğin dizilerin yaklaşan bölümleri için tahmini yayın takvimi.
- **Uygulama içinde izleme:** YouTube videosu başka uygulamalarda oynatılmaya izin veriyorsa bölüm uygulamanın içinde oynar. İzin vermiyorsa ya da hata çıkarsa kanalın kendi sitesi uygulama içinde açılır. O da açılmazsa telefonun tarayıcısına yönlendirilir.
- **Ana ekran widget'ı:** "Bu akşam ekranda" olan diziler telefon ana ekranında.
- **İsimle arama:** Listede olmayan bir diziyi adıyla arayıp kanal sitelerinden ve resmi YouTube kanallarından bulup listene ekleyebilirsin.
- **Bildirim:** Uygulama arka planda kanalları kontrol eder (sıklık ayarlanabilir, sessiz saatler desteklenir). Takip ettiğin dizinin yeni bölümü internete düşünce bildirim gelir.
- **Zengin dizi bilgisi:** Dizi sayfasında afiş ve konu özeti (Vikipedi).
- **Devam et, açık/koyu tema, takip, beğeni, izledim işareti ve bölüm yorumları.** Hepsi telefonda saklanır.

## Kurulum
- **Hazır APK (sürüm 2.0):** [DiziCambazi-v2.apk dosyasını indir](https://github.com/borgatelefondeneme-arch/dizi-cambazi/raw/main/apk/DiziCambazi-v2.apk), sonra telefonda aç. Android bilinmeyen kaynaktan yükleme izni isteyecek.
- Eski sürüm (1.0) arşiv olarak durur: [DiziCambazi.apk](https://github.com/borgatelefondeneme-arch/dizi-cambazi/raw/main/apk/DiziCambazi.apk).
- **Kaynaktan:** Projeyi Android Studio'da aç (File → Open) ve çalıştır. Gerekenler: Android SDK 35, JDK 17 ya da üstü.

## Yapı
- `app/src/main/java/com/dizicambazi/app/data/Scraper.kt`: Kanal sitelerinden ve YouTube'dan bölümleri okur.
- `data/Repo.kt`: Yerel kayıt, yenileme ve bildirim listesi.
- `work/RefreshWorker.kt`: Arka planda kontrol ve bildirim.
- `ui/`: Compose ekranları, uygulama içi YouTube oynatıcı (`YouTubePlayer.kt`) ve site oynatıcı (`WebPlayerActivity.kt`).
- `app/src/main/assets/seed.json`: İlk açılışta gösterilen başlangıç listesi.

## Gizlilik ve yasal
Uygulama hiçbir kişisel veri toplamaz; her şey telefonda saklanır. Bölüm dosyası barındırmaz, yalnızca kanalların kendi sitelerindeki ve resmi YouTube kanallarındaki yayınlara bağlantı verir. Tüm hakları ilgili kanallara aittir.

- Gizlilik politikası: [PRIVACY.md](PRIVACY.md)
- Google Play yükleme notları: [STORE.md](STORE.md)
