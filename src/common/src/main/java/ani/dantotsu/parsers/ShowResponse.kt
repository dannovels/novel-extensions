package ani.dantotsu.parsers

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga
import java.io.Serializable

data class ShowResponse(
    val name: String,
    val link: String,
    val coverUrl: FileUrl,

    //would be Useful for custom search, ig
    val otherNames: List<String> = listOf(),

    //Total number of Episodes/Chapters in the show.
    val total: Int? = null,

    //In case you want to sent some extra data
    val extra : Map<String,String>?=null,

    //SAnime object from Aniyomi
    val sAnime: SAnime? = null,

    //SManga object from Aniyomi
    val sManga: SManga? = null
) : Serializable {
    constructor(name: String, link: String, coverUrl: String, otherNames: List<String> = listOf(), total: Int? = null, extra: Map<String, String>?=null)
            : this(name, link, FileUrl(coverUrl), otherNames, total, extra)

    constructor(name: String, link: String, coverUrl: String, otherNames: List<String> = listOf(), total: Int? = null)
            : this(name, link, FileUrl(coverUrl), otherNames, total)

    constructor(name: String, link: String, coverUrl: String, otherNames: List<String> = listOf())
            : this(name, link, FileUrl(coverUrl), otherNames)

    constructor(name: String, link: String, coverUrl: String)
            : this(name, link, FileUrl(coverUrl))

    constructor(name: String, link: String, coverUrl: String, sAnime: SAnime)
            : this(name, link, FileUrl(coverUrl), sAnime = sAnime)

    constructor(name: String, link: String, coverUrl: String, sManga: SManga)
            : this(name, link, FileUrl(coverUrl), sManga = sManga)
}


data class FileUrl(
    val url: String,
    val headers: Map<String, String> = mapOf()
) : Serializable {
    companion object {
        operator fun get(url: String?, headers: Map<String, String> = mapOf()): FileUrl? {
            return FileUrl(url ?: return null, headers)
        }
    }
}
