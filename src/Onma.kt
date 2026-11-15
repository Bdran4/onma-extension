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
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class Onma : HttpSource() {

    // ---------------- CONFIG ----------------
    override val name = "Onma"
    override val baseUrl = "https://www.onma.me"
    override val lang = "ar"
    override val supportsLatest = true

    private val popularPath = "/filterList?page=%d&sortBy=views&asc=false"
    private val latestPath = "/latest-release?page=%d"

    private val listSelector = "div.media"
    private val latestSelector = "div.mainpage-manga"
    private val listTitleSel = "a.chart-title, h5 a, h3 a"
    private val listLinkSel = "a"
    private val listImgSel = "img"
    private val nextPageSel = "ul.pagination a[rel=next]"

    private val detailTitleSel = "h2.widget-title, h1"
    private val detailCoverSel = "img.img-responsive"
    private val detailDescSel = "div.well p"
    private val detailGenreSel = "dd a[href*=/filterList?cat]"
    private val detailAuthorSel = "dd a[href*=author]"
    private val detailStatusSel = "span.label"

    private val chapterListSel = "ul.chapters > li:not(.btn)"
    private val chapterLinkSel = "h5 > a, a"
    private val chapterDateSel = "div.date-chapter-title-rtl, span.date"

    private val pageImgSel = "div#all img.img-responsive, div#all img"
    // ----------------------------------------

    

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun Response.toDoc(): Document =
        Jsoup.parse(body.string(), request.url.toString())

    private fun relUrl(absolute: String): String =
        "/" + absolute.trim().substringAfter("://").substringAfter("/", "")

    // ---------- Popular ----------
    override fun popularMangaRequest(page: Int): Request =
        GET(baseUrl + popularPath.format(page), headers)

    override fun popularMangaParse(response: Response): MangasPage =
        parseList(response.toDoc(), listSelector)

    // ---------- Latest ----------
    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl + latestPath.format(page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseList(response.toDoc(), latestSelector)

    // ---------- Search ----------
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/filterList?page=$page&alpha=$q&sortBy=name&asc=true", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        parseList(response.toDoc(), listSelector)

    private fun parseList(doc: Document, itemSel: String): MangasPage {
        val mangas = doc.select(itemSel).mapNotNull { el ->
            runCatching { mangaFromElement(el) }.getOrNull()
        }
        return MangasPage(mangas, doc.selectFirst(nextPageSel) != null)
    }

    private fun mangaFromElement(element: Element): SManga {
        val a = element.selectFirst(listLinkSel)!!
        val img = element.selectFirst(listImgSel)
        return SManga.create().apply {
            url = relUrl(a.absUrl("href"))
            title = element.selectFirst(listTitleSel)?.text()?.trim()
                ?: img?.attr("alt").orEmpty()
            thumbnail_url = img?.let { i ->
                i.absUrl("data-src").ifEmpty { i.absUrl("src") }
            }
        }
    }

    // ---------- Details ----------
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.toDoc()
        return SManga.create().apply {
            title = document.selectFirst(detailTitleSel)?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst(detailCoverSel)?.absUrl("src")
            description = document.selectFirst(detailDescSel)?.text()?.trim()
            genre = document.select(detailGenreSel).joinToString(", ") { it.text() }
            author = document.selectFirst(detailAuthorSel)?.text()
            status = parseStatus(document.selectFirst(detailStatusSel)?.text().orEmpty())
        }
    }

    private fun parseStatus(s: String): Int = when {
        s.contains("مستمر") || s.contains("Ongoing", true) -> SManga.ONGOING
        s.contains("مكتمل") || s.contains("Complete", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ---------- Chapters ----------
    override fun chapterListParse(response: Response): List<SChapter> =
        response.toDoc().select(chapterListSel).map { el ->
            val a = el.selectFirst(chapterLinkSel)!!
            SChapter.create().apply {
                url = relUrl(a.absUrl("href"))
                name = a.text().trim()
                date_upload = parseDate(el.selectFirst(chapterDateSel)?.text().orEmpty())
            }
        }

    private fun parseDate(s: String): Long =
        runCatching { DATE_FORMAT.parse(s.trim())?.time }.getOrNull() ?: 0L

    // ---------- Pages ----------
    override fun pageListParse(response: Response): List<Page> =
        response.toDoc().select(pageImgSel).mapIndexed { i, img ->
            val u = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            Page(i, "", u.trim())
        }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used.")

    companion object {
        private val DATE_FORMAT by lazy { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH) }
    }
}
