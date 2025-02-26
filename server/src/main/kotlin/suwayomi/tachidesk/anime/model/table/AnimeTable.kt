package suwayomi.tachidesk.anime.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.Manga.getMangaMetaMap
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaStatus.Companion
import suwayomi.tachidesk.manga.model.table.MangaTable.chaptersLastFetchedAt
import suwayomi.tachidesk.manga.model.table.MangaTable.inLibraryAt
import suwayomi.tachidesk.manga.model.table.MangaTable.lastFetchedAt
import suwayomi.tachidesk.manga.model.table.MangaTable.realUrl
import suwayomi.tachidesk.manga.model.table.MangaTable.thumbnailUrlLastFetched
import suwayomi.tachidesk.manga.model.table.MangaTable.updateStrategy

object AnimeTable : IntIdTable() {
    val url = varchar("url", 2048)
    val title = varchar("title", 512)
    val initialized = bool("initialized").default(false)

    val artist = varchar("artist", 64).nullable()
    val author = varchar("author", 64).nullable()
    val description = varchar("description", 4096).nullable()
    val genre = varchar("genre", 1024).nullable()

    val status = integer("status").default(SAnime.UNKNOWN)
    val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

    val inLibrary = bool("in_library").default(false)
    val defaultCategory = bool("default_category").default(true)

    // source is used by some ancestor of IntIdTable
    val sourceReference = long("source")
}

fun AnimeTable.toDataClass(animeEntry: ResultRow) =
    MangaDataClass(
        id = animeEntry[this.id].value,
        sourceId = animeEntry[sourceReference].toString(),
        url = animeEntry[url],
        title = animeEntry[title],
        thumbnailUrl = proxyThumbnailUrl(animeEntry[this.id].value),
        thumbnailUrlLastFetched = animeEntry[thumbnailUrlLastFetched],
        initialized = animeEntry[initialized],
        artist = animeEntry[artist],
        author = animeEntry[author],
        description = animeEntry[description],
        genre = animeEntry[genre].toGenreList(),
        status = Companion.valueOf(animeEntry[status]).name,
        inLibrary = animeEntry[inLibrary],
        inLibraryAt = animeEntry[inLibraryAt],
        meta = getMangaMetaMap(animeEntry[id].value),
        realUrl = animeEntry[realUrl],
        lastFetchedAt = animeEntry[lastFetchedAt],
        chaptersLastFetchedAt = animeEntry[chaptersLastFetchedAt],
        updateStrategy = UpdateStrategy.valueOf(animeEntry[updateStrategy]),
    )

enum class AnimeStatus(
    val status: Int,
) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3),
    ;

    companion object {
        fun valueOf(value: Int): AnimeStatus = values().find { it.status == value } ?: UNKNOWN
    }
}
