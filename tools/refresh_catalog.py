#!/usr/bin/env python3
"""
Dizi Cambazı katalog yenileyici.
Kanalların resmi siteleri ve resmi YouTube kanallarından güncel dizi bölümlerini çeker,
app/src/main/assets/seed.json ve docs/index.html dosyalarını günceller.
Bağımlılık yok (yalnızca Python standart kütüphanesi). GitHub Actions'ta çalışır.
"""
import json, re, os, sys, html as H, urllib.parse, urllib.request, datetime as dt
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TZ = dt.timezone(dt.timedelta(hours=3))
NOW = dt.datetime.now(TZ)
TODAY = NOW.date()
DAYS = ["Pazartesi","Salı","Çarşamba","Perşembe","Cuma","Cumartesi","Pazar"]
SRC_LABEL = {"atv":"atv.com.tr","show":"showtv.com.tr","kanald":"kanald.com.tr","now":"nowtv.com.tr","trt1":"trt1.com.tr","kanal7":"izle7.com"}
MON = {"ocak":1,"subat":2,"mart":3,"nisan":4,"mayis":5,"haziran":6,"temmuz":7,"agustos":8,"eylul":9,"ekim":10,"kasim":11,"aralik":12}
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"

def get(url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "tr-TR,tr;q=0.9", "Cookie": "CONSENT=YES+1"})
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.read().decode("utf-8", "ignore")
    except Exception:
        return ""

def low(s): return s.replace("İ","i").replace("I","ı").lower()
def norm(s): return re.sub(r"[^\w ]","",low(s)).strip()
def ascii_(s):
    s=low(s).replace("ı","i").replace("ş","s").replace("ğ","g").replace("ü","u").replace("ö","o").replace("ç","c")
    return re.sub(r"[^a-z0-9 ]"," ",s).strip()
def slug(name): return re.sub(r"[^a-z0-9]+","-",ascii_(name)).strip("-")

def isodur(p):
    m=re.match(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?",p or "")
    if not m or not any(m.groups()): return ""
    h,mi,s=[int(x or 0) for x in m.groups()]; t=h*3600+mi*60+s
    return f"{t//3600}:{t%3600//60:02d}:{t%60:02d}"
def secs(l):
    s=0
    for x in l.split(":"): s=s*60+int(x)
    return s
def ago(s):
    m=re.match(r"(\d+)\s*(dk|sa|gün|hf|ay|yıl|minute|hour|day|week|month|year)",s or "")
    if not m: return None
    n=int(m.group(1)); u=m.group(2)
    d={"dk":dt.timedelta(minutes=n),"minute":dt.timedelta(minutes=n),"sa":dt.timedelta(hours=n),"hour":dt.timedelta(hours=n),
       "gün":dt.timedelta(days=n),"day":dt.timedelta(days=n),"hf":dt.timedelta(weeks=n),"week":dt.timedelta(weeks=n),
       "ay":dt.timedelta(days=30*n),"month":dt.timedelta(days=30*n),"yıl":dt.timedelta(days=365*n),"year":dt.timedelta(days=365*n)}[u]
    return NOW-d
def snap(d,wd): return d if wd is None else d-dt.timedelta(days=(d.weekday()-wd)%7)

def meta(s):
    def m(p):
        r=re.search(p,s); return H.unescape(r.group(1)) if r else None
    img=m(r'"thumbnailUrl"\s*:\s*\[?\s*"([^"]+)"')
    og=m(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"')
    if not img or "logo" in (img or ""): img = og if (og and "logo" not in og) else None
    return dict(img=img, desc=m(r'<meta[^>]+(?:property="og:description"|name="description")[^>]+content="([^"]*)"'),
                dur=isodur(m(r'"duration"\s*:\s*"([^"]+)"')), date=m(r'"(?:datePublished|uploadDate)"\s*:\s*"([^"]+)"'),
                yt=m(r'youtube\.com/(?:embed/|watch\?v=)([A-Za-z0-9_-]{11})'))

def site_episodes(src):
    t=src["type"]; sl=re.escape(src["slug"])
    if t=="atv": lu,rx,b=f'https://www.atv.com.tr/{src["slug"]}/bolumler', rf'href="(/{sl}/(\d+)-bolum/izle)"','https://www.atv.com.tr'
    elif t=="show": lu,rx,b=f'https://www.showtv.com.tr/dizi/tanitim/{src["slug"]}/{src.get("id","")}', rf'href="(/dizi/tum_bolumler/{sl}-sezon-\d+-bolum-(\d+)-izle/\d+)"','https://www.showtv.com.tr'
    elif t=="kanald": lu,rx,b=f'https://www.kanald.com.tr/{src["slug"]}/bolumler?orderby=StartDate%20desc,EpisodeNumber%20desc', rf'href="(/{sl}/bolumler/{sl}-(\d+)-bolum)"','https://www.kanald.com.tr'
    elif t=="now": lu,rx,b=f'https://www.nowtv.com.tr/{src["slug"]}/izle', rf'href="(https://www.nowtv.com.tr/{sl}/bolum/(\d+))"',''
    elif t=="trt1": lu,rx,b=f'https://www.trt1.com.tr/diziler/{src["slug"]}', rf'href="(/diziler/{sl}/bolum/(\d+)-bolum)"','https://www.trt1.com.tr'
    elif t=="kanal7": lu,rx,b=src.get("page",""), rf'(https://www.izle7.com/kanal7/izle-\d+-{sl}-(\d+)-bolum-(?!fragmani)[a-z0-9-]+\.html)',''
    else: return {}
    s=get(lu); E={}
    for href,n in re.findall(rx,s): E[int(n)]=b+H.unescape(href)
    return E

def yt_search(q):
    u="https://www.youtube.com/results?search_query="+urllib.parse.quote(q+" bölüm")+"&sp=CAISBBABGAI%253D&hl=tr&gl=TR"
    s=get(u); m=re.search(r"var ytInitialData = (\{.*?\});</script>",s)
    if not m: return []
    out=[]
    def walk(o):
        if isinstance(o,dict):
            if "videoRenderer" in o:
                v=o["videoRenderer"]
                try: t=v["title"]["runs"][0]["text"]
                except Exception: t=""
                out.append(dict(id=v.get("videoId"), title=t, len=(v.get("lengthText") or {}).get("simpleText"),
                                ago=(v.get("publishedTimeText") or {}).get("simpleText"),
                                ch=(v.get("ownerText") or {}).get("runs",[{}])[0].get("text","")))
                return
            for x in o.values(): walk(x)
        elif isinstance(o,list):
            for x in o: walk(x)
    try: walk(json.loads(m.group(1)))
    except Exception: pass
    return out

def series_episodes(s):
    wd = DAYS.index(s["day"]) if s.get("day") in DAYS else None
    t = s["src"]["type"]; E={}
    site = site_episodes(s["src"])
    for n in sorted(site)[-3:]:
        p=meta(get(site[n])); d={"site":site[n],"img":p["img"],"desc":p["desc"],"dur":p["dur"],"yt":p["yt"],"air":None}
        if p["date"] and t in ("show","kanald","trt1"):
            try:
                x=dt.datetime.fromisoformat(p["date"].replace("Z","+00:00")).astimezone(TZ)
                if t=="kanald" and x.hour<6: x-=dt.timedelta(days=1)
                if t=="trt1" and x.hour<12: x-=dt.timedelta(days=1)
                d["air"]=snap(x.date(),wd)
            except Exception: pass
        if t=="kanal7":
            mm=re.search(r"-(\d+)-(ocak|subat|mart|nisan|mayis|haziran|temmuz|agustos|eylul|ekim|kasim|aralik)-",site[n])
            if mm: d["air"]=dt.date(TODAY.year,MON[mm.group(2)],int(mm.group(1)))
        E[n]=d
    base=norm(s["name"].split(":")[0]); full=re.compile(r"(^|\s)"+re.escape(norm(s["name"]))+r"\s+\d+")
    for v in yt_search(s["name"]):
        ln=v.get("len")
        if not ln or not (3000<secs(ln)<4*3600): continue
        if re.search(r"review|analy|anal[iİ]z|kısa|tüm bölüm|tek part|fragman",low(v["title"])): continue
        if base not in norm(v.get("ch","")): continue
        if not full.search(norm(v["title"])): continue
        mm=re.search(r"(\d+)\.?\s*[Bb]ölüm",v["title"])
        if not mm: continue
        n=int(mm.group(1)); a=ago(v.get("ago"))
        if not a or (NOW-a).days>40: continue
        d=E.setdefault(n,{"site":None,"img":None,"desc":None,"dur":"","yt":None,"air":None})
        if not d.get("yt"):
            d["yt"]=v["id"]; d["dur"]=d["dur"] or ln
            yd=a.date()-(dt.timedelta(days=1) if a.hour<6 else dt.timedelta(0))
            if not d["air"]: d["air"]=snap(yd,wd)
        if not d["dur"]: d["dur"]=ln
    nums=sorted(E,reverse=True)[:3]
    for n in nums:
        if not E[n]["air"]:
            ref=[m for m in nums if E[m]["air"]]
            E[n]["air"]=(E[min(ref,key=lambda m:abs(m-n))]["air"]+dt.timedelta(days=7*(n-min(ref,key=lambda m:abs(m-n))))) if (ref and wd is not None) else TODAY
    for i in range(1,len(nums)):
        a=E[nums[i-1]]["air"]; b=E[nums[i]]["air"]
        if wd is not None and (a-b).days<6: E[nums[i]]["air"]=a-dt.timedelta(days=7*(nums[i-1]-nums[i]))
    for n in nums:
        if E[n]["air"]>TODAY: E[n]["air"]=snap(TODAY,wd)
    out=[]
    for n in nums:
        d=E[n]; srcs=[]
        if d.get("yt"): srcs.append({"label":"YouTube","url":"https://www.youtube.com/watch?v="+d["yt"]})
        if d.get("site"): srcs.append({"label":SRC_LABEL.get(t,"Kanal sitesi"),"url":d["site"]})
        thumb = ("https://i.ytimg.com/vi/%s/hqdefault.jpg"%d["yt"]) if d.get("yt") else (d.get("img") or "")
        out.append(dict(id=f'{s["key"]}-{n}', series=s["key"], seriesName=s["name"], channel=s["channel"], num=n,
                        airDate=d["air"].isoformat(), duration=d.get("dur") or "", desc=(d.get("desc") or "")[:220],
                        sources=srcs, status="online", thumb=thumb, addedAt=d["air"].isoformat()+"T21:00:00+03:00"))
    return out

KNOWN = {
 "Altı Üstü İstanbul":("Pazartesi","20:00"),"Güneşin Doğduğu Yer":("Perşembe","20:00"),"Kuruluş Orhan":("Cuma","20:00"),
 "Sevdan Bir Ateş":("Çarşamba","20:00"),"Kızılcık Şerbeti":("Cuma","20:00"),"Muhtemel Aşk":("Perşembe","20:00"),
 "Uzak Şehir":("Pazartesi","20:00"),"Haysiyet":("Salı","20:00"),"Güller ve Günahlar":("Cumartesi","20:00"),"Daha 17":("Pazar","20:00"),
 "Teşkilat":("Pazar","20:00"),"Gönül Dağı":("Cumartesi","20:00"),"Evlilik Güzeldir":("Pazartesi","20:00"),
 "Kod Adı Kırlangıç":("Cumartesi","22:45"),"Mehmed: Fetihler Sultanı":("Salı","20:00"),"Taşacak Bu Deniz":("Cuma","20:00"),
 "Anne Yarısı":("Salı","20:00"),"Halef: Köklerin Çağrısı":("Perşembe","20:00"),"Ömür Usta":("Pazar","20:00"),
 "Yeraltı":("Çarşamba","20:00"),"Sevdam Karadeniz":("Pazartesi","20:00"),"Sevdiğim Sensin":("Perşembe","20:00"),
 "Gelin":("Hafta içi her gün","19:00"),
}
ENDED = {"Sevdam Karadeniz"}

def page(src):
    t=src["type"]
    return {"atv":f'https://www.atv.com.tr/{src["slug"]}/bolumler',"kanald":f'https://www.kanald.com.tr/{src["slug"]}/bolumler',
            "trt1":f'https://www.trt1.com.tr/diziler/{src["slug"]}',"now":f'https://www.nowtv.com.tr/{src["slug"]}/izle',
            "show":f'https://www.showtv.com.tr/dizi/tanitim/{src["slug"]}/{src.get("id","")}',"kanal7":src.get("page","")
            }.get(t, "https://www.youtube.com/results?search_query="+urllib.parse.quote(src["slug"]))

def cand(name, channel, src):
    k=KNOWN.get(name)
    return dict(key=slug(name), name=name, channel=channel, day=(k[0] if k else ""), time=(k[1] if k else "20:00"),
                page=page(src), src=src, ended=(name in ENDED))

def candidates():
    L=[]
    for n,s in [("Altı Üstü İstanbul","alti-ustu-istanbul"),("Güneşin Doğduğu Yer","gunesin-dogdugu-yer"),("Kuruluş Orhan","kurulus-orhan")]:
        L.append(cand(n,"atv",{"type":"atv","slug":s}))
    for n,s,i in [("Sevdan Bir Ateş","sevdan-bir-ates","3087"),("Kızılcık Şerbeti","kizilcik-serbeti","2875"),("Muhtemel Aşk","muhtemel-ask","3072"),
                  ("Cevher","cevher","3113"),("Menekşe","menekse","3111"),("Bahar","bahar","2941"),("Deha","deha","2975"),
                  ("Delikanlı","delikanli","3061"),("Veliaht","veliaht","3003"),("Siyah Kalp","siyah-kalp","2974")]:
        L.append(cand(n,"Show TV",{"type":"show","slug":s,"id":i}))
    for n,s in [("Uzak Şehir","uzak-sehir"),("Haysiyet","haysiyet"),("Güller ve Günahlar","guller-ve-gunahlar"),("Daha 17","daha-17"),
                ("Eşref Rüya","esref-ruya"),("Susuz Yaz","susuz-yaz"),("İnci Taneleri","inci-taneleri"),("Güneşin Kızları","gunesin-kizlari")]:
        L.append(cand(n,"Kanal D",{"type":"kanald","slug":s}))
    for n,s in [("Teşkilat","teskilat"),("Gönül Dağı","gonul-dagi"),("Evlilik Güzeldir","evlilik-guzeldir"),("Kod Adı Kırlangıç","kod-adi-kirlangic"),
                ("Mehmed: Fetihler Sultanı","mehmed-fetihler-sultani"),("Taşacak Bu Deniz","tasacak-bu-deniz"),("Sumud","sumud"),
                ("Vefa Sultan","vefa-sultan"),("Kudüs Fatihi Selahaddin Eyyubi","kudus-fatihi-selahaddin-eyyubi")]:
        L.append(cand(n,"TRT 1",{"type":"trt1","slug":s}))
    for n,s in [("Anne Yarısı","Anne-Yarisi"),("Halef: Köklerin Çağrısı","Halef-Koklerin-Cagrisi"),("Ömür Usta","Omur-Usta"),
                ("Yeraltı","Yeralti"),("Sevdam Karadeniz","Sevdam-Karadeniz"),("Kıskanmak","Kiskanmak"),("Ben Leman","Ben-Leman"),
                ("Sahtekârlar","Sahtekarlar"),("Sakıncalı","Sakincali"),("Leyla: Hayat, Aşk, Adalet","Leyla-Hayat-Ask-Adalet"),
                ("Başka Bir Sen","Baska-Bir-Sen"),("Ben Onun Annesiyim","Ben-Onun-Annesiyim")]:
        L.append(cand(n,"NOW",{"type":"now","slug":s}))
    for n,s in [("Gelin","gelin"),("Kalbim Sana Emanet","kalbim-sana-emanet"),("Bilmezler Ki Kalbimdesin","bilmezler-ki-kalbimdesin")]:
        L.append(cand(n,"Kanal 7",{"type":"kanal7","slug":s,"page":f"https://www.kanal7.com/dizi/dizi-{s}"}))
    L.append(cand("Sevdiğim Sensin","Star TV",{"type":"youtube","slug":"sevdigim-sensin"}))
    return L

def guess_day(eps):
    days=[dt.date.fromisoformat(e["airDate"]).weekday() for e in eps]
    if len(set(days))>=3: return "Hafta içi her gün"
    if not days: return ""
    top=max(set(days),key=days.count)
    return DAYS[top]

def main():
    cands=candidates()
    def work(s):
        try: return s, series_episodes(s)
        except Exception as e: return s, []
    results=[]
    with ThreadPoolExecutor(6) as ex:
        results=list(ex.map(work,cands))
    series=[]; episodes=[]
    for s,eps in sorted(results,key=lambda x:x[0]["name"].lower()):
        dates=[dt.date.fromisoformat(e["airDate"]) for e in eps]
        latest=max(dates) if dates else None
        fresh = latest is not None and (TODAY-latest).days<=30
        if len(eps)<2 or not fresh:
            print("DROP", s["name"], "eps=%d"%len(eps), "latest=%s"%latest); continue
        day = s["day"] if s["day"] else (guess_day(eps) if guess_day(eps)=="Hafta içi her gün" else "")
        series.append({"key":s["key"],"name":s["name"],"channel":s["channel"],"day":day,"time":s["time"],"page":s["page"],"src":s["src"],"ended":s["ended"]})
        episodes.extend(eps)
        print("KEEP", s["name"], "[%s]"%s["channel"], "day='%s'"%day, [e["num"] for e in eps])
    if len(series)<12:
        print("FATAL: too few series (%d); refusing to overwrite."%len(series)); sys.exit(1)
    # seed.json (Android)
    seed={"series":series,"episodes":episodes,"follows":[],"likes":[],"watched":[],"comments":[],"notices":[]}
    with open(os.path.join(ROOT,"app/src/main/assets/seed.json"),"w",encoding="utf-8") as f:
        json.dump(seed,f,ensure_ascii=False)
    # docs/index.html (web) from template
    DATA={"series":series,"episodes":episodes,"updatedAt":TODAY.isoformat()+"T00:00:00+03:00"}
    tpl=open(os.path.join(ROOT,"tools/web_template.html"),encoding="utf-8").read()
    out=tpl.replace("__DIZI_CAMBAZI_DATA__", json.dumps(DATA,ensure_ascii=False))
    with open(os.path.join(ROOT,"docs/index.html"),"w",encoding="utf-8") as f:
        f.write(out)
    print("=== %d series, %d episodes written ==="%(len(series),len(episodes)))

if __name__=="__main__":
    main()
