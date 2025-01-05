package some.random.novelextensions.annaarchive

import ani.dantotsu.parsers.Book
import ani.dantotsu.parsers.NovelInterface
import ani.dantotsu.parsers.ShowResponse
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/* testing
fun main(){
    val anna = AnnaArchive()
    val client = Requests()
    //val search = anna.search("Alya Sometimes Hides Her feelings")
    // launch a separate coroutine to perform the search
    var book: Book? = null
    CoroutineScope(Dispatchers.IO).launch {
        //search = anna.search("Alya Sometimes Hides Her feelings", client)
        book = anna.loadBook("https://annas-archive.org/md5/ad3abaa3b9bf331dd40ec523d6a2cce1", mapOf(), client)
    }
    // wait for the coroutine to finish
    Thread.sleep(8000)
    // print the results
    book?.let{
        println("search: $it")
    }
}*/

@Suppress("unused")
class AnnaArchive() : NovelInterface {

    val name = "Anna's Archive"
    val saveName = "anna"
    val hostUrl = "https://annas-archive.org"
    val volumeRegex = Regex("vol\\.? (\\d+(\\.\\d+)?)|volume (\\d+(\\.\\d+)?)", RegexOption.IGNORE_CASE)
    val defaultImage = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"



    private fun parseShowResponse(it: Element?): ShowResponse? {
        //logger("parseShowResponse called with element: ${it?.text()}")
        it ?: return null
        if (!it.select("div[class~=lg:text-xs]").text().contains("epub", ignoreCase = true)) {
            return null
        }
        //logger("parseShowResponse called with element: ${it.text()}")
        val name = it.selectFirst("h3")?.text() ?: ""
        var img = it.selectFirst("img")?.attr("src") ?: ""
        if(img=="") img = defaultImage
        val extra = mapOf(
            "0" to it.select("div.italic").text(),
            "1" to it.select("div[class~=max-lg:text-xs]").text(),
            "2" to it.select("div[class~=lg:text-xs]").text(),
        )
        return ShowResponse(name, "$hostUrl${it.attr("href")}", img, extra = extra)
    }

    override suspend fun search(query: String, client: Requests): List<ShowResponse> {
        val q = query.substringAfter("!$").replace("-", " ") // (minus) - does not display records containing the words after
        val vols1 = client.get("$hostUrl/search?ext=epub&q=$q")
            .document.getElementsByAttributeValueContaining("class", "h-[125]")
        //logger("Novel search: $query, $q, ${vols1.size}")
        val vols = vols1
            .mapNotNull { div ->
                val a = div.selectFirst("a") ?: Jsoup.parse(div.data())
                parseShowResponse(a.selectFirst("a"))
            }
        //logger("Novel search: $query, $q, ${vols.size}")
        return if(query.startsWith("!$")) vols.sortByVolume(q) else vols
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?, client: Requests): Book {
        return client.get(link).document.selectFirst("main")!!.let {
            val name = it.selectFirst("div.text-3xl")!!.text().substringBefore("\uD83D\uDD0D")
            var img = it.selectFirst("img")?.attr("src") ?: ""
            if(img=="") img = defaultImage
            val description = it.selectFirst("div.js-md5-top-box-description")?.text()
            val links = it.select("a.js-download-link")
                .filter { element ->
                    !element.text().contains("Fast") &&
                            !element.attr("href").contains("onion") &&
                            !element.attr("href").contains("/datasets") &&
                            !element.attr("href").contains("1lib") &&
                            !element.attr("href").contains("slow_download")
                }.reversed() //libgen urls are faster
                .flatMap { a ->
                    LinkExtractor(a.attr("href"), client).extractLink() ?: emptyList()
                }
            //logger("Novel search: $name, $img, $description, $links")
            Book(name, img, description, links)
        }
    }
    class LinkExtractor(private val url: String, private val client: Requests) {
        suspend fun extractLink(): List<String>? {
            return when {
                isLibgenUrl(url) || isLibraryLolUrl(url) -> LibgenExtractor(url)
                isSlowDownload(url) -> {
                    try {
                        val response = client.get("https://annas-archive.org$url")
                        val links = response.document.select("a")?.mapNotNull { it.attr("href") }
                        //logger("Novel search extr3: $links")
                        links?.takeWhile { !it.contains("localhost") }
                    } catch (e: Exception) {
                        //logger("Error in isSlowDownload: ${e.message}")
                        null // or handle the exception as needed
                    }
                }

                else -> listOf(url)
            }
        }

        private fun isLibgenUrl(url: String): Boolean {
            val a = url.contains("libgen")
            //logger("Novel search isLibgenUrl: $url, $a")
            return a
        }

        private fun isLibraryLolUrl(url: String): Boolean {
            val a = url.contains("library.lol")
            //logger("Novel search isLibraryLolUrl: $url, $a")
            return a
        }

        private fun isSlowDownload(url: String): Boolean {
            val a = url.contains("slow_download")
            //logger("Novel search isSlowDownload: $url, $a")
            return a
        }

        private suspend fun LibgenExtractor(url: String): List<String>? {
            return try {
                when {
                    url.contains("ads.php") -> {
                        val response = client.get(url)
                        val links = response.document.select("table#main").first()?.getElementsByAttribute("href")?.first()?.attr("href")
                        //logger("Novel search extr: $links")
                        //if substring starts with /ads.php then add the url before it
                        if (links?.startsWith("/ads.php") == true || links?.startsWith("get.php") == true) listOf(url.substringBefore("ads.php") + links)
                        else listOf(links ?: "")
                    }
                    else -> {
                        val response = client.get(url)
                        val links = response.document.selectFirst("div#download")?.select("a")?.mapNotNull { it.attr("href") }
                        //logger("Novel search extr2: $links")
                        links?.takeWhile { !it.contains("localhost") }
                    }
                }
            } catch (e: Exception) {
                //logger("Error during Libgen extraction: ${e.message}")
                null // or handle the exception as needed
            }
        }


    }

    fun List<ShowResponse>.sortByVolume(query:String) : List<ShowResponse> {
        val sorted = groupBy { res ->
            val match = volumeRegex.find(res.name)?.groupValues
                ?.firstOrNull { it.isNotEmpty() }
                ?.substringAfter(" ")
                ?.toDoubleOrNull() ?: Double.MAX_VALUE
            match
        }.toSortedMap().values

        val volumes = sorted.map { showList ->
            val nonDefaultCoverShows = showList.filter { it.coverUrl.url != defaultImage }
            val bestShow = nonDefaultCoverShows.firstOrNull { it.name.contains(query) }
                ?: nonDefaultCoverShows.firstOrNull()
                ?: showList.first()
            bestShow
        }
        val remainingShows = sorted.flatten() - volumes.toSet()

        return volumes + remainingShows
    }

}

fun logger(msg: String) {
    println(msg)
}


