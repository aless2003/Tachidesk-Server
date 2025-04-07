package suwayomi.tachidesk.anime.controller

import io.javalin.http.HttpStatus
import suwayomi.tachidesk.anime.impl.Anime
import suwayomi.tachidesk.anime.impl.Anime.getAnime
import suwayomi.tachidesk.anime.impl.Episode
import suwayomi.tachidesk.anime.model.dataclass.AnimeDataClass
import suwayomi.tachidesk.anime.model.dataclass.EpisodeDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation
import kotlin.time.Duration.Companion.days

object AnimeController {
    val anime =
        handler(
            documentWith = {
                withOperation {
                    summary("Get Anime by ID")
                    description("Gets the Anime by it's ID")
                }
            },
            behaviorOf = { ctx ->
                val animeId = ctx.pathParam("animeId").toInt()
                val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean() ?: false

                ctx.future {
                    future {
                        getAnime(animeId, onlineFetch)
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<AnimeDataClass>(HttpStatus.OK)
            },
        )

    val thumbnail =
        handler(
            documentWith = {
                withOperation {
                    summary("Get Anime Thumbnail")
                    description("Gets the Anime Thumbnail")
                }
            },
            behaviorOf = { ctx ->
                val animeId = ctx.pathParam("animeId").toInt()
                ctx.future {
                    future { Anime.getAnimeThumbnail(animeId) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 1.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds")
                            ctx.result(it.first)
                        }
                }
            },
            withResults = {
                image(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    // val animeId = ctx.pathParam("animeId").toInt()
    //
    //                    val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean()
    //
    //                    ctx.future { future { getEpisodeList(animeId, onlineFetch) } }
    var episodesList =
        handler(
            documentWith = {
                withOperation {
                    summary("Anime Episode List")
                    description("Get the Episode List of the anime with the specified ID")
                }
            },
            behaviorOf = { ctx ->
                val animeId = ctx.pathParam("animeId").toInt()
                val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean()
                ctx.future {
                    future {
                        Episode.getEpisodeList(animeId, onlineFetch)
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<List<EpisodeDataClass>>(HttpStatus.OK)
            },
        )

    val episode =
        handler(
            documentWith = {
                withOperation {
                    summary("Get Episode Info")
                    description("Gets the Episode Info")
                }
            },
            behaviorOf = { ctx ->
                val episodeIndex = ctx.pathParam("episodeIndex").toInt()
                val animeId = ctx.pathParam("animeId").toInt()
                ctx.future {
                    future {
                        Episode.getEpisode(episodeIndex, animeId)
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<EpisodeDataClass>(HttpStatus.OK)
            },
        )
}
