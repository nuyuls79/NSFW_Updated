package com.jacekun

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.logError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.*

class Hahomoe : MainAPI() {

    companion object {
        fun getType(t: String): TvType {
            return TvType.NSFW
        }
    }

    private val globalTvType = TvType.NSFW

    override var mainUrl = "https://haho.moe"
    override var name = "Haho moe"

    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.NSFW)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val items = ArrayList<HomePageList>()

        val soup = app.get(mainUrl).document

        for (section in soup.select("#content > section")) {

            try {

                if (section.attr("id") == "toplist-tabs") {

                    for (top in section.select(".tab-content > [role=\"tabpanel\"]")) {

                        val title = "Top - " + top.attr("id").split("-")[1].uppercase(Locale.UK)

                        val anime = top.select("li > a").mapNotNull {

                            val epTitle = it.selectFirst(".thumb-title")?.text() ?: ""

                            val url = fixUrlNull(it.attr("href")) ?: return@mapNotNull null

                            AnimeSearchResponse(
                                name = epTitle,
                                url = url,
                                apiName = this.name,
                                type = globalTvType,
                                posterUrl = it.selectFirst("img")?.attr("src"),
                                dubStatus = EnumSet.of(DubStatus.Subbed)
                            )
                        }

                        items.add(HomePageList(title, anime))
                    }

                } else {

                    val title = section.selectFirst("h2")?.text() ?: ""

                    val anime = section.select("li > a").mapNotNull {

                        val epTitle = it.selectFirst(".thumb-title")?.text() ?: ""

                        val url = fixUrlNull(it.attr("href")) ?: return@mapNotNull null

                        AnimeSearchResponse(
                            name = epTitle,
                            url = url,
                            apiName = this.name,
                            type = globalTvType,
                            posterUrl = it.selectFirst("img")?.attr("src"),
                            dubStatus = EnumSet.of(DubStatus.Subbed)
                        )
                    }

                    items.add(HomePageList(title, anime))
                }

            } catch (e: Exception) {

                e.printStackTrace()
                logError(e)

            }
        }

        if (items.isEmpty()) throw ErrorLoadingException()

        return HomePageResponse(items)
    }

    private fun parseSearchPage(soup: Document): ArrayList<SearchResponse> {

        val items = soup.select("ul.thumb > li > a")

        if (items.isEmpty()) return ArrayList()

        val returnValue = ArrayList<SearchResponse>()

        for (i in items) {

            val href = fixUrlNull(i.attr("href")) ?: ""

            val img = fixUrlNull(i.selectFirst("img")?.attr("src"))

            val title = i.attr("title")

            if (href.isNotBlank()) {

                returnValue.add(
                    AnimeSearchResponse(
                        name = title,
                        url = href,
                        apiName = this.name,
                        type = globalTvType,
                        posterUrl = img,
                        dubStatus = EnumSet.of(DubStatus.Subbed)
                    )
                )
            }
        }

        return returnValue
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {

        val url = "$mainUrl/anime"

        val response = app.get(
            url,
            params = mapOf("q" to query)
        )

        val document = Jsoup.parse(response.text)

        return parseSearchPage(document)
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val canonicalTitle =
            document.selectFirst("header.entry-header > h1.mb-3")?.text()?.trim() ?: ""

        val episodeNodes = document.select("li[class*=\"episode\"] > a")

        val episodes = episodeNodes.mapNotNull {

            val dataUrl = it.attr("href")

            Episode(
                data = dataUrl,
                name = it.selectFirst(".episode-title")?.text()?.trim(),
                posterUrl = it.selectFirst("img")?.attr("src")
            )
        }

        val poster = document.selectFirst("img.cover-image")?.attr("src")

        return newAnimeLoadResponse(
            canonicalTitle,
            url,
            TvType.NSFW
        ) {
            posterUrl = poster
            episodes = hashMapOf(DubStatus.Subbed to episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val soup = app.get(data).document

        for (source in soup.select("""video#player > source""")) {

            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    fixUrl(source.attr("src")),
                    mainUrl,
                    getQualityFromName(source.attr("title"))
                )
            )
        }

        return true
    }
}
