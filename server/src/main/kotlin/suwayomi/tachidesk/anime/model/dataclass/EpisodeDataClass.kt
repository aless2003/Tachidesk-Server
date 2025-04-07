package suwayomi.tachidesk.anime.model.dataclass

import eu.kanade.tachiyomi.animesource.model.Track

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class EpisodeDataClass(
    val url: String,
    val name: String,
    val uploadDate: Long,
    val episodeNumber: Float,
    val scanlator: String?,
    val animeId: Int,
    /** Episode has been watched */
    val watched: Boolean,
    /** Episode is bookmarked */
    val bookmarked: Boolean,
    /** last position watched, zero means not read/no data */
    val lastPosition: Int,
    /** this Episode's index, starts with 1 */
    val index: Int,
    /** total episode count, used to calculate if there's a next and prev episode */
    val episodeCount: Int? = null,
    /** used to construct pages in the front-end */
    val videos: List<VideoDataClass>? = null,
    /** used to get subtitles */
    val subtitles: List<Track>? = null,
)
