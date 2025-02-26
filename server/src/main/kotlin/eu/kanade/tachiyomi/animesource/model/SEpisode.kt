package eu.kanade.tachiyomi.animesource.model

// import tachiyomi.animesource.model.EpisodeInfo
import java.io.Serializable

interface SEpisode : Serializable {
    var url: String

    var name: String

    var dateUpload: Long

    var episodeNumber: Float

    var scanlator: String?

    fun copyFrom(other: SEpisode) {
        name = other.name
        url = other.url
        dateUpload = other.dateUpload
        episodeNumber = other.episodeNumber
        scanlator = other.scanlator
    }

    companion object {
        fun create(): SEpisode = SEpisodeImpl()
    }
}

// fun SEpisode.toEpisodeInfo(): EpisodeInfo {
//    return EpisodeInfo(
//        dateUpload = this.date_upload,
//        key = this.url,
//        name = this.name,
//        number = this.episode_number,
//        scanlator = this.scanlator ?: ""
//    )
// }
//
// fun EpisodeInfo.toSEpisode(): SEpisode {
//    val episode = this
//    return SEpisode.create().apply {
//        url = episode.key
//        name = episode.name
//        date_upload = episode.dateUpload
//        episode_number = episode.number
//        scanlator = episode.scanlator
//    }
// }
