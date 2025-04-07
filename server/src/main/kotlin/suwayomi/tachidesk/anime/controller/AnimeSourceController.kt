package suwayomi.tachidesk.anime.controller

import io.javalin.http.HttpStatus
import suwayomi.tachidesk.anime.impl.AnimeList.getAnimeList
import suwayomi.tachidesk.anime.model.dataclass.PagedAnimeListDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation

object AnimeSourceController {
    val popular =
        handler(
            documentWith = {
                withOperation {
                    summary("Popular Source Anime")
                    description("List Popular Anime from Source")
                }
            },
            behaviorOf = { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val pageNum = ctx.pathParam("pageNum").toInt()
                ctx.future {
                    future {
                        getAnimeList(sourceId, pageNum, popular = true)
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<PagedAnimeListDataClass>(HttpStatus.OK)
            },
        )

    val latest =
        handler(
            documentWith = {
                withOperation {
                    summary("Popular Source Anime")
                    description("List Popular Anime from Source")
                }
            },
            behaviorOf = { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val pageNum = ctx.pathParam("pageNum").toInt()
                ctx.future {
                    future {
                        getAnimeList(sourceId, pageNum, popular = false)
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<PagedAnimeListDataClass>(HttpStatus.OK)
            },
        )
}
