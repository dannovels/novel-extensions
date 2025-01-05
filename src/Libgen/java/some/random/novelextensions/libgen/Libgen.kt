package some.random.novelextensions.libgen

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
    val anna = Libgen()
    val client = Requests()
    //val search = anna.search("Alya Sometimes Hides Her feelings")
    // launch a separate coroutine to perform the search
    var search: Book? = null
    CoroutineScope(Dispatchers.IO).launch {
        search = anna.loadBook("https://libgen.is/fiction/FC3BF33EB6B7B25892DECF44C9A3D340", mapOf(), client)
        println(search)
    }
    // wait for the coroutine to finish
    Thread.sleep(10000)
    // print the results
    search?.let{
        println("search: $it")
    }
}
*/

@Suppress("unused")
class Libgen() : NovelInterface {

    val name = "Libgen"
    val saveName = "Libgen"
    val hostUrl = "https://libgen.is"
    val volumeRegex = Regex("vol\\.? (\\d+(\\.\\d+)?)|volume (\\d+(\\.\\d+)?)", RegexOption.IGNORE_CASE)
    val defaultImage = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"



    private fun parseShowResponse(it: Element?): ShowResponse? {
        //logger("parseShowResponse called with element: ${it?.text()}")
        it ?: return null
        var name: String? = null
        val extra = mutableMapOf<String, String>()
        var link: String? = null
        val img = defaultImage
        it.children().forEachIndexed { index, td ->
            when (index) {
                0 -> {
                    //name = td.select("a").text()
                    extra["0"] = td.select("a").text()
                }
                1 -> {
                    extra["1"] = td.text()
                }
                2 -> {
                    name = td.selectFirst("a")?.text()
                    link = td.selectFirst("a")?.attr("href")
                }
                3 -> {
                    extra["2"] = td.text() + ", "
                }
                4 -> {
                    extra["2"] = extra["2"] + td.text()
                    if (!td.text().contains("epub", ignoreCase = true)) {
                        return null
                    }
                }
            }
        }
        if(name == null || link == null) return null
        return ShowResponse(name!!, hostUrl + link!!, img, extra = extra.toMap())
    }

    override suspend fun search(query: String, client: Requests): List<ShowResponse> {
        val q = query.substringAfter("!$").replace("-", " ").replace(" ", "+") // (minus) - does not display records containing the words after
        val vols1 = client.get("$hostUrl/fiction/?q=$q")
            .document//.getElementsByAttributeValueContaining("class", "catalog")
        val trElements = vols1.select("table.catalog tr")
        //logger("Novel search: $query, $q, ${vols1.size}")
        val vols = trElements
            .mapNotNull { tr ->
                parseShowResponse(tr)
            }
        //logger("Novel search: $query, $q, ${vols.size}")
        return if(query.startsWith("!$")) vols.sortByVolume(q) else vols
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?, client: Requests): Book {
        val doc = client.get(link).document
        var img = doc.select("div.record_side").select("img").attr("src")
        if(img=="") img = defaultImage
        val trElements = doc.select("table.record tr")
            //first tr contains td with class record_title which contains the title
        val name = trElements[0].select("td.record_title").text()
            //search for the ul with class record_mirrors
        val ulElements = doc.select("ul.record_mirrors")
            //get all the links that are in the ul
        val links = ulElements.select("a").map { it.attr("href") }
            .filter { ln ->
                !ln.contains("Fast") &&
                        !ln.contains("onion") &&
                        !ln.contains("/datasets") &&
                        !ln.contains("1lib") &&
                        !ln.contains("torrent")
            }
            .flatMap { a ->
                LinkExtractor(a, client).extractLink() ?: emptyList()
            }
        val description = ""
            //logger("Novel search: $name, $img, $description, $links")
        return Book(name, img, description, links)
    }
    class LinkExtractor(private val url: String, private val client: Requests) {
        suspend fun extractLink(): List<String>? {
            return when {
                isLibgenUrl(url) || isLibraryLolUrl(url) -> LibgenExtractor(url)
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


