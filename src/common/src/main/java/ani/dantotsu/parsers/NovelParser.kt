package ani.dantotsu.parsers

class NovelParser {
}

data class Book(
    val name: String,
    val img: FileUrl,
    val description: String? = null,
    val links: List<FileUrl>
) {
    constructor (name: String, img: String, description: String? = null, links: List<String>) : this(
        name,
        FileUrl(img),
        description,
        links.map { FileUrl(it) }
    )
}