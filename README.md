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

## Web sürümü (tarayıcıda, kurulum yok)
Hesapsız, herkese açık web sürümü — telefon ya da bilgisayar tarayıcısında açılır:
- https://raw.githack.com/borgatelefondeneme-arch/dizi-cambazi/main/docs/index.html

Bu sürüm yerleşik bir dizi listesiyle gelir; takip/beğeni/izledim seçimleri tarayıcında saklanır. Video kanalın sitesinde ya da YouTube'da açılır. (Uygulama içi oynatma ve otomatik güncelleme yalnızca Android uygulamasında.)

## Kurulum
- **Hazır APK (sürüm 2.1):** [DiziCambazi-v3.apk dosyasını indir](https://github.com/borgatelefondeneme-arch/dizi-cambazi/raw/main/apk/DiziCambazi-v3.apk), sonra telefonda aç. Yan yüklenen uygulamalar kendiliğinden güncellenmez; yeni sürümü indirip üstüne kurarsın (veriler korunur).
- Eski sürüm (1.0) arşiv olarak durur: [DiziCambazi.apk](https://github.com/borgatelefondeneme-arch/dizi-cambazi/raw/main/apk/DiziCambazi.apk).
- **Kaynaktan:** Projeyi Android Studio'da aç (File → Open) ve çalıştır. Gerekenler: Android SDK 35, JDK 17 ya da üstü.

## Yapı
- `app/src/main/java/com/dizicambazi/app/data/Scraper.kt`: Kanal sitelerinden ve YouTube'dan bölümleri okur.
- `data/Repo.kt`: Yerel kayıt, yenileme ve bildirim listesi.
- `work/RefreshWorker.kt`: Arka planda kontrol ve bildirim.
- `ui/`: Compose ekranları, uygulama içi YouTube oynatıcı (`YouTubePlayer.kt`) ve site oynatıcı (`WebPlayerActivity.kt`).
- `app/src/main/assets/seed.json`: İlk açılışta gösterilen başlangıç listesi.

## Otomatik güncelleme
`.github/workflows/refresh.yml`, her gün `tools/refresh_catalog.py` betiğini çalıştırır: kanal siteleri ve resmi YouTube kanallarından güncel bölümleri çekip `app/src/main/assets/seed.json` ve `docs/index.html` dosyalarını tazeler, değişiklik varsa otomatik commit'ler. Böylece web sürümü de kendiliğinden güncel kalır. (Actions sekmesinden elle de çalıştırılabilir.)

## Gizlilik ve yasal
Uygulama hiçbir kişisel veri toplamaz; her şey telefonda saklanır. Bölüm dosyası barındırmaz, yalnızca kanalların kendi sitelerindeki ve resmi YouTube kanallarındaki yayınlara bağlantı verir. Tüm hakları ilgili kanallara aittir.

- Gizlilik politikası: [PRIVACY.md](PRIVACY.md)
- Google Play yükleme notları: [STORE.md](STORE.md)
