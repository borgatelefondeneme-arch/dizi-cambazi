package com.dizicambazi.app.ui

/** Final yapmış klasik diziler; tüm bölümleri resmi YouTube kanallarında açılır. */
data class ClassicSeries(val name: String, val channel: String, val url: String, val thumb: String)

val CLASSICS = listOf(
    ClassicSeries("Diriliş: Ertuğrul", "TRT 1", "https://www.youtube.com/results?search_query=Dirili%C5%9F%3A%20Ertu%C4%9Frul%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/3yjO6yfHLcU/hqdefault.jpg"),
    ClassicSeries("Eşkıya Dünyaya Hükümdar Olmaz", "atv", "https://www.youtube.com/results?search_query=E%C5%9Fk%C4%B1ya%20D%C3%BCnyaya%20H%C3%BCk%C3%BCmdar%20Olmaz%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/0ocs4QH3Mcc/hqdefault.jpg"),
    ClassicSeries("Çukur", "Show TV", "https://www.youtube.com/results?search_query=%C3%87ukur%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/029AvqTwTcI/hqdefault.jpg"),
    ClassicSeries("İçerde", "Show TV", "https://www.youtube.com/results?search_query=%C4%B0%C3%A7erde%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/g9ye2PbZzaQ/hqdefault.jpg"),
    ClassicSeries("Karadayı", "atv", "https://www.youtube.com/results?search_query=Karaday%C4%B1%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/7yULYt6F7oM/hqdefault.jpg"),
    ClassicSeries("Kara Para Aşk", "atv", "https://www.youtube.com/results?search_query=Kara%20Para%20A%C5%9Fk%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/Qx8TgL-a7As/hqdefault.jpg"),
    ClassicSeries("Muhteşem Yüzyıl", "Star TV", "https://www.youtube.com/results?search_query=Muhte%C5%9Fem%20Y%C3%BCzy%C4%B1l%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/m-RP43JXGM0/hqdefault.jpg"),
    ClassicSeries("Leyla ile Mecnun", "TRT 1", "https://www.youtube.com/results?search_query=Leyla%20ile%20Mecnun%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/yK3dj4v3t6k/hqdefault.jpg"),
    ClassicSeries("Seksenler", "TRT 1", "https://www.youtube.com/results?search_query=Seksenler%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/aNZEqL2cm58/hqdefault.jpg"),
    ClassicSeries("Kurtlar Vadisi Pusu", "Show TV", "https://www.youtube.com/results?search_query=Kurtlar%20Vadisi%20Pusu%20b%C3%B6l%C3%BCm", "https://i.ytimg.com/vi/GX6XL8YA-4A/hqdefault.jpg"),
)
