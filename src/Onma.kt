package eu.kanade.tachiyomi.extension.ar.onma

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Onma : ParsedHttpSource() {

    // ---------------- CONFIG ----------------
    override val name = "Onma"
    override val baseUrl = "https://www.onma.me"
    override val lang = "ar"
    override val supportsLatest = true

    private val popularPath = "/filterList?page=%d&sortBy=views&asc=false"
    private val latestPath = "/latest-release?page=%d"

    private val listSelector = "div.media"
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

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET(baseUrl + popularPath.format(page), headers)

    override fun popularMangaSelector() = listSelector
    override fun popularMangaNextPageSelector() = nextPageSel
    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl + latestPath.format(page), headers)

    override fun latestUpdatesSelector() = "div.mainpage-manga"
    override fun latestUpdatesNextPageSelector() = nextPageSel
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(baseUrl + "/filterList?page=$page&alpha=$query&sortBy=name&asc=true", headers)

    override fun searchMangaSelector() = listSelector
    override fun searchMangaNextPageSelector() = nextPageSel
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val a = element.selectFirst(listLinkSel)!!
        setUrlWithoutDomain(a.attr("href"))
        title = element.selectFirst(listTitleSel)?.text()?.trim()
            ?: element.selectFirst(listImgSel)?.attr("alt").orEmpty()
        thumbnail_url = element.selectFirst(listImgSel)?.let { img ->
            img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(detailTitleSel)!!.text().trim()
        thumbnail_url = document.selectFirst(detailCoverSel)?.absUrl("src")
        description = document.selectFirst(detailDescSel)?.text()?.trim()
        genre = document.select(detailGenreSel).joinToString(", ") { it.text() }
        author = document.selectFirst(detailAuthorSel)?.text()
        status = parseStatus(document.selectFirst(detailStatusSel)?.text().orEmpty())
        initialized = true
    }

    private fun parseStatus(s: String): Int = when {
        s.contains("مستمر") || s.contains("Ongoing", true) -> SManga.ONGOING
        s.contains("مكتمل") || s.contains("Complete", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = chapterListSel

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.selectFirst(chapterLinkSel)!!
        setUrlWithoutDomain(a.attr("href"))
        name = a.text().trim()
        date_upload = parseDate(element.selectFirst(chapterDateSel)?.text().orEmpty())
    }

    private fun parseDate(s: String): Long =
        runCatching { DATE_FORMAT.parse(s.trim())?.time }.getOrNull() ?: 0L

    override fun pageListParse(document: Document): List<Page> =
        document.select(pageImgSel).mapIndexed { i, img ->
            val url = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            Page(i, "", url.trim())
        }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used.")

    companion object {
        private val DATE_FORMAT by lazy { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH) }
    }
}
