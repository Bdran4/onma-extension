package eu.kanade.tachiyomi.extension.ar.onma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class Onma : HttpSource() {

    override val name = "Onma"
    override val baseUrl = "https://www.onma.me"
    override val lang = "ar"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun Response.toDoc(): Document =
        Jsoup.parse(body.string(), request.url.toString())

    private fun relUrl(absolute: String): String =
        "/" + absolute.trim().substringAfter("://").substringAfter("/", "")

    private fun coverOf(slug: String) =
        "$baseUrl/uploads/manga/$slug/cover/cover_250x350.jpg"

    // ---------- قوائم المانجا ----------
    private fun parseList(doc: Document, page: Int): MangasPage {
        val order = LinkedHashSet<String>()
        val titles = HashMap<String, String>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href")
            if (!href.startsWith(baseUrl)) continue
            val path = relUrl(href)
            if (!MANGA_RE.matches(path)) continue
            order.add(path)
            val text = a.text().trim()
                .ifEmpty { a.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
            if (text.isNotEmpty() && !titles.containsKey(path)) titles[path] = text
        }
        val mangas = order.map { p ->
            val slug = p.removePrefix("/manga/").trimEnd('/')
            SManga.create().apply {
                url = p
                title = titles[p] ?: slug
                thumbnail_url = coverOf(slug)
            }
        }
        val hasNext = mangas.isNotEmpty() &&
            doc.select("a[href]").any { it.attr("href").contains("page=${page + 1}") }
        return MangasPage(mangas, hasNext)
    }

    private fun pageOf(response: Response): Int =
        response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga-list?sortBy=views&asc=false&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage =
        parseList(response.toDoc(), pageOf(response))

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest-release?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseList(response.toDoc(), pageOf(response))

    // ---------- البحث ----------
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search?query=$q", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        if (body.contains("\"suggestions\"")) {
            val mangas = VALUE_DATA_RE.findAll(body).map { m ->
                val slug = unescapeJson(m.groupValues[2])
                SManga.create().apply {
                    url = "/manga/$slug"
                    title = unescapeJson(m.groupValues[1])
                    thumbnail_url = coverOf(slug)
                }
            }.toList()
            return MangasPage(mangas, false)
        }
        return parseList(Jsoup.parse(body, response.request.url.toString()), 1)
    }

    private fun unescapeJson(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                if (n == 'u' && i + 6 <= s.length) {
                    val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                sb.append(if (n == 'n') '\n' else n)
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // ---------- تفاصيل المانجا ----------
    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.toDoc()
        val cover = doc.selectFirst("img[src*=/cover/]")
        return SManga.create().apply {
            title = cover?.attr("alt")?.trim().orEmpty()
                .ifEmpty { doc.title().substringAfter("مانجا ").substringBefore(" مترجم").trim() }
            thumbnail_url = cover?.absUrl("src")
            author = doc.selectFirst("a[href*=/manga-list/author/]")?.text()?.trim()
            genre = doc.select("a[href*=/manga-list/category/]")
                .joinToString(", ") { it.text().trim() }
            val heads = doc.select("h1,h2,h3,h4,h5,h6")
            description = heads.firstOrNull { it.text().contains("نبذة") }
                ?.nextElementSibling()?.text()?.trim()
                ?: doc.selectFirst("meta[name=description]")?.attr("content")
            val st = heads.firstOrNull { it.text().trim().startsWith("الحالة") }
                ?.text().orEmpty()
            status = when {
                st.contains("مكتمل") -> SManga.COMPLETED
                st.contains("مستمر") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    // ---------- الفصول ----------
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.toDoc()
        val seen = HashSet<String>()
        val out = ArrayList<SChapter>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href")
            if (!href.startsWith(baseUrl)) continue
            val path = relUrl(href)
            if (!CHAPTER_RE.matches(path)) continue
            if (!seen.add(path)) continue
            val li = a.parents().firstOrNull { it.tagName() == "li" }
            out.add(
                SChapter.create().apply {
                    url = path
                    name = a.text().trim().ifEmpty { path.substringAfterLast('/') }
                    date_upload = parseDate(li?.text().orEmpty())
                },
            )
        }
        return out
    }

    private fun parseDate(s: String): Long {
        val m = DATE_RE.find(s) ?: return 0L
        return runCatching {
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                .parse(m.value.replace(".", ""))?.time
        }.getOrNull() ?: 0L
    }

    // ---------- صور الفصل ----------
    override fun pageListParse(response: Response): List<Page> {
        val doc = response.toDoc()
        val imgs = doc.select("img").filter { img ->
            PAGE_ALT_RE.containsMatchIn(img.attr("alt")) ||
                img.attr("src").contains("/chapters/") ||
                img.attr("data-src").contains("/chapters/")
        }
        val urls = imgs
            .map { img -> img.absUrl("data-src").ifEmpty { img.absUrl("src") } }
            .filter { it.contains("/chapters/") }
        val pagination = doc.select("li")
            .mapNotNull { it.text().trim().toIntOrNull() }
            .maxOrNull() ?: 0
        val count = maxOf(imgs.size, pagination)

        val distinct = urls.distinct()
        val result = if (distinct.isNotEmpty() && distinct.size >= count) {
            distinct
        } else {
            val m = distinct.firstOrNull()?.let { NUM_RE.find(it) }
            if (m != null && count > 0) {
                val w = m.groupValues[2].length
                (1..count).map { m.groupValues[1] + it.toString().padStart(w, '0') + m.groupValues[3] }
            } else {
                distinct
            }
        }
        return result.mapIndexed { i, u -> Page(i, "", u) }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used.")

    companion object {
        private val MANGA_RE = Regex("^/manga/[^/]+/?$")
        private val CHAPTER_RE = Regex("^/manga/[^/]+/[^/]+$")
        private val DATE_RE = Regex("\\d{1,2} [A-Za-z]{3}\\.? \\d{4}")
        private val PAGE_ALT_RE = Regex("Page\\s*\\d+")
        private val NUM_RE = Regex("^(.*/)(\\d+)(\\.\\w+)(\\?.*)?$")
        private val VALUE_DATA_RE = Regex(
            "\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*\"data\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
        )
    }
}
