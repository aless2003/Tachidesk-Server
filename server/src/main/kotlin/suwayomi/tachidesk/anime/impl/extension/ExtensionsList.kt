package suwayomi.tachidesk.anime.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.adaptivity.xmlutil.core.impl.multiplatform.Language
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.anime.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.anime.impl.extension.github.OnlineExtension
import suwayomi.tachidesk.anime.model.dataclass.AnimeExtensionDataClass
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.server.serverConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object ExtensionsList {
    private val logger = KotlinLogging.logger {}

    var lastUpdateCheck: Long = 0
    var updateMap = ConcurrentHashMap<String, OnlineExtension>()

    /** 60,000 milliseconds = 60 seconds */
    private const val EXTENSION_UPDATE_DELAY_TIME = 60 * 1000

    suspend fun fetchExtensions() {
        // update if 60 seconds has passed or requested offline and database is empty
        val extensions =
            serverConfig.animeExtensionRepos.value.map { repo ->
                kotlin
                    .runCatching {
                        ExtensionGithubApi.findExtensions(repo.repoUrlReplace())
                    }.onFailure {
                        logger.warn(it) {
                            "Failed to fetch extensions for repo: $repo"
                        }
                    }
            }
        val foundExtensions = extensions.mapNotNull { it.getOrNull() }.flatten()
        updateExtensionDatabase(foundExtensions)
    }

    suspend fun fetchExtensionsCached() {
        // update if 60 seconds has passed or requested offline and database is empty
        if (lastUpdateCheck + 60.seconds.inWholeMilliseconds < System.currentTimeMillis()) {
            logger.debug { "Getting extensions list from the internet" }
            lastUpdateCheck = System.currentTimeMillis()

            fetchExtensions()
        } else {
            logger.debug { "used cached extension list" }
        }
    }

    suspend fun getExtensionList(): List<AnimeExtensionDataClass> {
        fetchExtensionsCached()
        return extensionTableAsDataClass()
    }

    fun extensionTableAsDataClass() =
        transaction {
            AnimeExtensionTable.selectAll().map {
                AnimeExtensionDataClass(
                    it[AnimeExtensionTable.repo],
                    it[AnimeExtensionTable.apkName],
                    getExtensionIconUrl(it[AnimeExtensionTable.apkName]),
                    it[AnimeExtensionTable.name],
                    it[AnimeExtensionTable.pkgName],
                    it[AnimeExtensionTable.versionName],
                    it[AnimeExtensionTable.versionCode],
                    it[AnimeExtensionTable.lang],
                    it[AnimeExtensionTable.isNsfw],
                    it[AnimeExtensionTable.isInstalled],
                    it[AnimeExtensionTable.hasUpdate],
                    it[AnimeExtensionTable.isObsolete],
                )
            }
        }

    private fun updateExtensionDatabase(foundExtensions: List<OnlineExtension>) {
        transaction {
            foundExtensions.forEach { foundExtension ->
                val extensionRecord =
                    AnimeExtensionTable
                        .selectAll()
                        .where { AnimeExtensionTable.pkgName eq foundExtension.pkgName }
                        .firstOrNull()
                if (extensionRecord != null) {
                    if (extensionRecord[AnimeExtensionTable.isInstalled]) {
                        when {
                            foundExtension.versionCode > extensionRecord[AnimeExtensionTable.versionCode] -> {
                                // there is an update
                                AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq foundExtension.pkgName }) {
                                    it[hasUpdate] = true
                                }
                                updateMap.putIfAbsent(foundExtension.pkgName, foundExtension)
                            }
                            foundExtension.versionCode < extensionRecord[AnimeExtensionTable.versionCode] -> {
                                // some how the user installed an invalid version
                                AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq foundExtension.pkgName }) {
                                    it[isObsolete] = true
                                }
                            }
                        }
                    } else {
                        // extension is not installed so we can overwrite the data without a care
                        AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq foundExtension.pkgName }) {
                            it[repo] = foundExtension.repo
                            it[name] = foundExtension.name
                            it[versionName] = foundExtension.versionName
                            it[versionCode] = foundExtension.versionCode
                            it[lang] = foundExtension.lang
                            it[isNsfw] = foundExtension.isNsfw
                            it[apkName] = foundExtension.apkName
                            it[iconUrl] = foundExtension.iconUrl
                        }
                    }
                } else {
                    // insert new record
                    AnimeExtensionTable.insert {
                        it[repo] = foundExtension.repo
                        it[name] = foundExtension.name
                        it[pkgName] = foundExtension.pkgName
                        it[versionName] = foundExtension.versionName
                        it[versionCode] = foundExtension.versionCode
                        it[lang] = foundExtension.lang
                        it[isNsfw] = foundExtension.isNsfw
                        it[apkName] = foundExtension.apkName
                        it[iconUrl] = foundExtension.iconUrl
                    }
                }
            }

            // deal with obsolete extensions
            AnimeExtensionTable.selectAll().forEach { extensionRecord ->
                val foundExtension = foundExtensions.find { it.pkgName == extensionRecord[AnimeExtensionTable.pkgName] }
                if (foundExtension == null) {
                    // not in the repo, so this extensions is obsolete
                    if (extensionRecord[AnimeExtensionTable.isInstalled]) {
                        // is installed so we should mark it as obsolete
                        AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq extensionRecord[AnimeExtensionTable.pkgName] }) {
                            it[isObsolete] = true
                        }
                    } else {
                        // is not installed so we can remove the record without a care
                        AnimeExtensionTable.deleteWhere { AnimeExtensionTable.pkgName eq extensionRecord[AnimeExtensionTable.pkgName] }
                    }
                }
            }
        }
    }

    private fun String.repoUrlReplace(): String =
        if (contains("github")) {
            replace(repoMatchRegex) {
                "https://raw.githubusercontent.com/${it.groupValues[2]}/${it.groupValues[3]}/" +
                    (it.groupValues.getOrNull(4)?.ifBlank { null } ?: "repo") +
                    "/" +
                    (it.groupValues.getOrNull(5)?.ifBlank { null } ?: "index.min.json")
            }
        } else {
            this
        }

    @Language("RegExp")
    val repoMatchRegex =
        (
            "https:\\/\\/(?>www\\.|raw\\.)?(github|githubusercontent)\\.com" +
                "\\/([^\\/]+)\\/([^\\/]+)(?>(?>\\/tree|\\/blob)?\\/([^\\/\\n]*))?(?>\\/([^\\/\\n]*\\.json)?)?"
        ).toRegex()
}
