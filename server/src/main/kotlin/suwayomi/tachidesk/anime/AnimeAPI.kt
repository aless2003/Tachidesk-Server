package suwayomi.tachidesk.anime

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.apibuilder.ApiBuilder
import io.javalin.apibuilder.ApiBuilder.path
import suwayomi.tachidesk.anime.controller.AnimeExtensionController
import suwayomi.tachidesk.anime.impl.Anime.getAnime
import suwayomi.tachidesk.anime.impl.Anime.getAnimeThumbnail
import suwayomi.tachidesk.anime.impl.AnimeList.getAnimeList
import suwayomi.tachidesk.anime.impl.Episode.getEpisode
import suwayomi.tachidesk.anime.impl.Episode.getEpisodeList
import suwayomi.tachidesk.anime.impl.Episode.modifyEpisode
import suwayomi.tachidesk.anime.impl.Search.sourceSearch
import suwayomi.tachidesk.anime.impl.Source.getAnimeSource
import suwayomi.tachidesk.anime.impl.Source.getSourceList
import suwayomi.tachidesk.anime.impl.extension.Extension.getExtensionIcon
import suwayomi.tachidesk.anime.impl.extension.Extension.installExtension
import suwayomi.tachidesk.anime.impl.extension.Extension.uninstallExtension
import suwayomi.tachidesk.anime.impl.extension.Extension.updateExtension
import suwayomi.tachidesk.server.JavalinSetup.future

object AnimeAPI {
    fun defineEndpoints() {
        // TODO fix this like in MangaAPI.kt
        path("anime") {
            // list all extensions
            path("extension") {
                ApiBuilder.get("list", AnimeExtensionController.list)
                // install extension identified with "pkgName"
                ApiBuilder.get("/api/v1/anime/extension/install/{pkgName}") { ctx ->
                    val pkgName = ctx.pathParam("pkgName")

                    ctx.future {
                        future {
                            installExtension(pkgName)
                        }
                    }
                }

                // update extension identified with "pkgName"
                ApiBuilder.get("/api/v1/anime/extension/update/{pkgName}") { ctx ->
                    val pkgName = ctx.pathParam("pkgName")

                    ctx.future {
                        future {
                            updateExtension(pkgName)
                        }
                    }
                }

                // uninstall extension identified with "pkgName"
                ApiBuilder.get("/api/v1/anime/extension/uninstall/{pkgName}") { ctx ->
                    val pkgName = ctx.pathParam("pkgName")

                    ctx.future {
                        future {
                            uninstallExtension(pkgName)
                        }
                    }
                }

                // icon for extension named `apkName`
                ApiBuilder.get("/api/v1/anime/extension/icon/{apkName}") { ctx ->
                    // TODO: move to pkgName
                    val apkName = ctx.pathParam("apkName")

                    ctx.future {
                        future {
                            getExtensionIcon(apkName)
                        }.thenApply {
                            ctx.header("content-type", it.second)
                            it.first
                        }
                    }
                }
            }

            path("source") {
                // list sources
                ApiBuilder.get("/api/v1/anime/source/list") { ctx ->
                    ctx.json(getSourceList())
                }

                // fetch source with id `sourceId`
                ApiBuilder.get("/api/v1/anime/source/{sourceId}") { ctx ->
                    val sourceId = ctx.pathParam("sourceId").toLong()
                    ctx.json(getAnimeSource(sourceId))
                }

                // popular animes from source with id `sourceId`
                ApiBuilder.get("/api/v1/anime/source/{sourceId}/popular/{pageNum}") { ctx ->
                    val sourceId = ctx.pathParam("sourceId").toLong()
                    val pageNum = ctx.pathParam("pageNum").toInt()
                    ctx.future {
                        future {
                            getAnimeList(sourceId, pageNum, popular = true)
                        }
                    }
                }

                // latest animes from source with id `sourceId`
                ApiBuilder.get("/api/v1/anime/source/{sourceId}/latest/{pageNum}") { ctx ->
                    val sourceId = ctx.pathParam("sourceId").toLong()
                    val pageNum = ctx.pathParam("pageNum").toInt()
                    ctx.future {
                        future {
                            getAnimeList(sourceId, pageNum, popular = false)
                        }
                    }
                }

                // single source search
                ApiBuilder.get("/api/v1/anime/source/{sourceId}/search/{searchTerm}/{pageNum}") { ctx ->
                    val sourceId = ctx.pathParam("sourceId").toLong()
                    val searchTerm = ctx.pathParam("searchTerm")
                    val pageNum = ctx.pathParam("pageNum").toInt()
                    ctx.future { future { sourceSearch(sourceId, searchTerm, pageNum) } }
                }
            }

            path("anime") {
                // get anime info
                ApiBuilder.get("/api/v1/anime/anime/{animeId}/") { ctx ->
                    val animeId = ctx.pathParam("animeId").toInt()
                    val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean() ?: false

                    ctx.future {
                        future {
                            getAnime(animeId, onlineFetch)
                        }
                    }
                }

                // anime thumbnail
                ApiBuilder.get("/api/v1/anime/anime/{animeId}/thumbnail") { ctx ->
                    val animeId = ctx.pathParam("animeId").toInt()

                    ctx.future {
                        future { getAnimeThumbnail(animeId) }
                            .thenApply {
                                ctx.header("content-type", it.second)
                                it.first
                            }
                    }
                }
            }

            path("episode") {
                // get episode list when showing a anime
                ApiBuilder.get("/api/v1/anime/anime/{animeId}/episodes") { ctx ->
                    val animeId = ctx.pathParam("animeId").toInt()

                    val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean()

                    ctx.future { future { getEpisodeList(animeId, onlineFetch) } }
                }

                ApiBuilder.get("/api/v1/anime/{animeId}/episode/{episodeIndex}") { ctx ->
                    val episodeIndex = ctx.pathParam("episodeIndex").toInt()
                    val animeId = ctx.pathParam("animeId").toInt()
                    ctx.future { future { getEpisode(episodeIndex, animeId) } }
                }

                // modify a episode's parameters
                ApiBuilder.patch("/api/v1/anime/anime/{animeId}/episode/{episodeIndex}") { ctx ->
                    val episodeIndex = ctx.pathParam("episodeIndex").toInt()
                    val animeId = ctx.pathParam("animeId").toInt()

                    val read = ctx.formParam("read")?.toBoolean()
                    val bookmarked = ctx.formParam("bookmarked")?.toBoolean()
                    val markPrevRead = ctx.formParam("markPrevRead")?.toBoolean()
                    val lastPageRead = ctx.formParam("lastPageRead")?.toInt()

                    modifyEpisode(
                        animeId,
                        episodeIndex,
                        read,
                        bookmarked,
                        markPrevRead,
                        lastPageRead,
                    )

                    ctx.status(200)
                }
            }
        }
//
//        // source filter list
//        app.get("/api/v1/source/{sourceId}/filters/") { ctx ->
//            val sourceId = ctx.pathParam("sourceId").toLong()
//            ctx.json(sourceFilters(sourceId))
//        }
//
//        // adds the manga to library
//        app.get("api/v1/manga/{mangaId}/library") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//
//            ctx.future(
//                JavalinSetup.future { addMangaToLibrary(mangaId) }
//            )
//        }
//
//        // removes the manga from the library
//        app.delete("api/v1/manga/{mangaId}/library") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//
//            ctx.future(
//                JavalinSetup.future { removeMangaFromLibrary(mangaId) }
//            )
//        }
//
//        // lists mangas that have no category assigned
//        app.get("/api/v1/library/") { ctx ->
//            ctx.json(getLibraryMangas())
//        }
//
//        // category list
//        app.get("/api/v1/category/") { ctx ->
//            ctx.json(Category.getCategoryList())
//        }
//
//        // category create
//        app.post("/api/v1/category/") { ctx ->
//            val name = ctx.formParam("name")!!
//            Category.createCategory(name)
//            ctx.status(200)
//        }
//
//        // returns some static info of the current app build
//        app.get("/api/v1/about/") { ctx ->
//            ctx.json(About.getAbout())
//        }
//
//        // category modification
//        app.patch("/api/v1/category/{categoryId}") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            val name = ctx.formParam("name")
//            val isDefault = ctx.formParam("default")?.toBoolean()
//            Category.updateCategory(categoryId, name, isDefault)
//            ctx.status(200)
//        }
//
//        // category re-ordering
//        app.patch("/api/v1/category/{categoryId}/reorder") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            val from = ctx.formParam("from")!!.toInt()
//            val to = ctx.formParam("to")!!.toInt()
//            Category.reorderCategory(categoryId, from, to)
//            ctx.status(200)
//        }
//
//        // category delete
//        app.delete("/api/v1/category/{categoryId}") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            Category.removeCategory(categoryId)
//            ctx.status(200)
//        }
//
//        // returns the manga list associated with a category
//        app.get("/api/v1/category/{categoryId}") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            ctx.json(getCategoryMangaList(categoryId))
//        }
//
//        // expects a Tachiyomi legacy backup json in the body
//        app.post("/api/v1/backup/legacy/import") { ctx ->
//            ctx.future(
//                future {
//                    restoreLegacyBackup(ctx.bodyAsInputStream())
//                }
//            )
//        }
//
//        // expects a Tachiyomi legacy backup json as a file upload, the file must be named "backup.json"
//        app.post("/api/v1/backup/legacy/import/file") { ctx ->
//            ctx.future(
//                JavalinSetup.future {
//                    restoreLegacyBackup(ctx.uploadedFile("backup.json")!!.content)
//                }
//            )
//        }
//
//        // returns a Tachiyomi legacy backup json created from the current database as a json body
//        app.get("/api/v1/backup/legacy/export") { ctx ->
//            ctx.contentType("application/json")
//            ctx.future(
//                JavalinSetup.future {
//                    createLegacyBackup(
//                        BackupFlags(
//                            includeManga = true,
//                            includeCategories = true,
//                            includeChapters = true,
//                            includeTracking = true,
//                            includeHistory = true,
//                        )
//                    )
//                }
//            )
//        }
//
//        // returns a Tachiyomi legacy backup json created from the current database as a file
//        app.get("/api/v1/backup/legacy/export/file") { ctx ->
//            ctx.contentType("application/json")
//            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm")
//            val currentDate = sdf.format(Date())
//
//            ctx.header("Content-Disposition", "attachment; filename=\"tachidesk_$currentDate.json\"")
//            ctx.future(
//                JavalinSetup.future {
//                    createLegacyBackup(
//                        BackupFlags(
//                            includeManga = true,
//                            includeCategories = true,
//                            includeChapters = true,
//                            includeTracking = true,
//                            includeHistory = true,
//                        )
//                    )
//                }
//            )
//        }
//
//        // Download queue stats
//        app.ws("/api/v1/downloads") { ws ->
//            ws.onConnect { ctx ->
//                // TODO: send current stat
//                // TODO: add to downlad subscribers
//            }
//            ws.onMessage {
//                // TODO: send current stat
//            }
//            ws.onClose { ctx ->
//                // TODO: remove from subscribers
//            }
//        }
    }
}
