package eu.kanade.tachiyomi.animesource.model

class SEpisodeImpl : SEpisode {
    override lateinit var url: String

    override lateinit var name: String

    override var dateUpload: Long = 0

    override var episodeNumber: Float = -1f

    override var scanlator: String? = null
}
