package suwayomi.tachidesk.anime.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.net.Uri
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.CacheControl
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.anime.impl.util.PackageTools
import suwayomi.tachidesk.anime.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.anime.impl.util.PackageTools.METADATA_NSFW
import suwayomi.tachidesk.anime.impl.util.PackageTools.METADATA_SOURCE_CLASS
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream

object Extension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    data class InstallableAPK(
        val apkFilePath: String,
        val pkgName: String,
    )

    suspend fun installExtension(pkgName: String): Int {
        logger.debug { "Installing $pkgName" }
        val extensionRecord =
            ExtensionsList
                .extensionTableAsDataClass()
                .first { it.pkgName == pkgName }

        return installAPK {
            val apkURL =
                ExtensionGithubApi.getApkUrl(
                    extensionRecord.repo
                        ?: throw NullPointerException("Could not find extension repo"),
                    extensionRecord.apkName,
                )
            val apkName = Uri.parse(apkURL).lastPathSegment!!
            val apkSavePath = "${applicationDirs.extensionsRoot}/$apkName"
            // download apk file
            downloadAPKFile(apkURL, apkSavePath)

            apkSavePath
        }
    }

    suspend fun installExternalExtension(
        inputStream: InputStream,
        apkName: String,
    ): Int =
        installAPK(true) {
            val rootPath = Path(applicationDirs.extensionsRoot)
            val downloadedFile = rootPath.resolve(apkName).normalize()
            check(downloadedFile.startsWith(rootPath) && downloadedFile.parent == rootPath) {
                "File '$apkName' is not a valid extension file"
            }
            logger.debug { "Saving apk at $apkName" }
            // download apk file
            downloadedFile.outputStream().sink().buffer().use { sink ->
                inputStream.source().use { source ->
                    sink.writeAll(source)
                    sink.flush()
                }
            }
            downloadedFile.absolutePathString()
        }

    suspend fun installAPK(
        forceReinstall: Boolean = false,
        fetcher: suspend () -> String,
    ): Int {
        val apkFilePath = fetcher()
        val apkName = File(apkFilePath).name

        // check if we don't have the extension already installed
        // if it's installed and we want to update, it first has to be uninstalled
        val isInstalled =
            transaction {
                AnimeExtensionTable.selectAll().where { AnimeExtensionTable.apkName eq apkName }.firstOrNull()
            }?.get(AnimeExtensionTable.isInstalled) ?: false

        val fileNameWithoutType = apkName.substringBefore(".apk")

        val dirPathWithoutType = "${applicationDirs.extensionsRoot}/$fileNameWithoutType"
        val jarFilePath = "$dirPathWithoutType.jar"
        val dexFilePath = "$dirPathWithoutType.dex"

        val packageInfo = PackageTools.getPackageInfo(apkFilePath)
        val pkgName = packageInfo.packageName
        if (isInstalled && forceReinstall) {
            Extension.uninstallExtension(pkgName)
        }

        if (!isInstalled || forceReinstall) {
            if (!packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }) {
                throw Exception("This apk is not a Tachiyomi extension")
            }

            // Validate lib version
            val libVersion = packageInfo.versionName.substringBeforeLast('.').toInt()
            if (libVersion <= PackageTools.LIB_VERSION_MAX && libVersion >= PackageTools.LIB_VERSION_MIN) {
                throw Exception(
                    "Lib version is $libVersion, while only versions " +
                        "${PackageTools.LIB_VERSION_MIN} - ${PackageTools.LIB_VERSION_MAX} are allowed",
                )
            }

            // TODO: allow trusting keys
//            val signatureHash = getSignatureHash(packageInfo)

//            if (signatureHash == null) {
//                throw Exception("Package $pkgName isn't signed")
//            } else if (signatureHash !in trustedSignatures) {
//                throw Exception("This apk is not a signed with the official tachiyomi signature")
//            }

            val isNsfw = packageInfo.applicationInfo.metaData.getString(PackageTools.METADATA_NSFW) == "1"

            val className =
                packageInfo.packageName +
                    packageInfo.applicationInfo.metaData.getString(
                        PackageTools.METADATA_SOURCE_CLASS,
                    )

            logger.debug { "Main class for extension is $className" }

            PackageTools.dex2jar(apkFilePath, jarFilePath, fileNameWithoutType)
            extractAssetsFromApk(apkFilePath, jarFilePath)

            // clean up
            File(apkFilePath).delete()
            File(dexFilePath).delete()

            // collect sources from the extension
            val extensionMainClassInstance = PackageTools.loadExtensionSources(jarFilePath, className)
            val sources: List<AnimeCatalogueSource> =
                when (extensionMainClassInstance) {
                    is AnimeSource -> listOf(extensionMainClassInstance)
                    is AnimeSourceFactory -> extensionMainClassInstance.createSources()
                    else -> throw RuntimeException("Unknown source class type! ${extensionMainClassInstance.javaClass}")
                }.map { it as AnimeCatalogueSource }

            val langs = sources.map { it.lang }.toSet()
            val extensionLang =
                when (langs.size) {
                    0 -> ""
                    1 -> langs.first()
                    else -> "all"
                }

            val extensionName =
                packageInfo.applicationInfo.nonLocalizedLabel
                    .toString()
                    .substringAfter("Aniyomi: ")

            // update extension info
            transaction {
                if (AnimeExtensionTable.selectAll().where { AnimeExtensionTable.pkgName eq pkgName }.firstOrNull() == null) {
                    AnimeExtensionTable.insert {
                        it[this.apkName] = apkName
                        it[name] = extensionName
                        it[this.pkgName] = packageInfo.packageName
                        it[versionName] = packageInfo.versionName
                        it[versionCode] = packageInfo.versionCode
                        it[lang] = extensionLang
                        it[this.isNsfw] = isNsfw
                    }
                }

                AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
                    it[this.apkName] = apkName
                    it[this.isInstalled] = true
                    it[this.classFQName] = className
                    it[versionName] = packageInfo.versionName
                    it[versionCode] = packageInfo.versionCode
                }

                val extensionId =
                    AnimeExtensionTable
                        .selectAll()
                        .where { AnimeExtensionTable.pkgName eq pkgName }
                        .first()[AnimeExtensionTable.id]
                        .value

                sources.forEach { httpSource ->
                    AnimeSourceTable.insert {
                        it[id] = httpSource.id
                        it[name] = httpSource.name
                        it[lang] = httpSource.lang
                        it[extension] = extensionId
                        it[AnimeSourceTable.isNsfw] = isNsfw
                    }
                    logger.debug { "Installed source ${httpSource.name} (${httpSource.lang}) with id:${httpSource.id}" }
                }
            }
            return 201 // we installed successfully
        } else {
            return 302 // extension was already installed
        }
    }

    private fun extractAssetsFromApk(
        apkPath: String,
        jarPath: String,
    ) {
        val apkFile = File(apkPath)
        val jarFile = File(jarPath)

        val assetsFolder = File("${apkFile.parent}/${apkFile.nameWithoutExtension}_assets")
        assetsFolder.mkdir()
        ZipInputStream(apkFile.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name.startsWith("assets/")) {
                    val assetFile = File(assetsFolder, zipEntry.name)
                    assetFile.parentFile.mkdirs()
                    FileOutputStream(assetFile).use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }

        val tempJarFile = File("${jarFile.parent}/${jarFile.nameWithoutExtension}_temp.jar")
        ZipInputStream(jarFile.inputStream()).use { jarZipInputStream ->
            ZipOutputStream(FileOutputStream(tempJarFile)).use { jarZipOutputStream ->
                var zipEntry = jarZipInputStream.nextEntry
                while (zipEntry != null) {
                    if (!zipEntry.name.startsWith("META-INF/")) {
                        jarZipOutputStream.putNextEntry(ZipEntry(zipEntry.name))
                        jarZipInputStream.copyTo(jarZipOutputStream)
                    }
                    zipEntry = jarZipInputStream.nextEntry
                }
                assetsFolder.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        jarZipOutputStream.putNextEntry(ZipEntry(file.relativeTo(assetsFolder).toString().replace("\\", "/")))
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(jarZipOutputStream)
                        }
                        jarZipOutputStream.closeEntry()
                    }
                }
            }
        }

        jarFile.delete()
        tempJarFile.renameTo(jarFile)

        assetsFolder.deleteRecursively()
    }

    private val network: NetworkHelper by injectLazy()

    private suspend fun downloadAPKFile(
        url: String,
        savePath: String,
    ) {
        val request = Request.Builder().url(url).build()
        val response = network.client.newCall(request).await()

        val downloadedFile = File(savePath)
        downloadedFile.sink().buffer().use { sink ->
            response.body!!.source().use { source ->
                sink.writeAll(source)
                sink.flush()
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        logger.debug { "Uninstalling $pkgName" }

        val extensionRecord = transaction { AnimeExtensionTable.selectAll().where { AnimeExtensionTable.pkgName eq pkgName }.first() }
        val fileNameWithoutType = extensionRecord[AnimeExtensionTable.apkName].substringBefore(".apk")
        val jarPath = "${applicationDirs.extensionsRoot}/$fileNameWithoutType.jar"
        val sources =
            transaction {
                val extensionId = extensionRecord[AnimeExtensionTable.id].value

                val sources =
                    AnimeSourceTable.selectAll().where { AnimeSourceTable.extension eq extensionId }.map {
                        it[AnimeSourceTable.id]
                            .value
                    }

                AnimeSourceTable.deleteWhere { extension eq extensionId }

                if (extensionRecord[AnimeExtensionTable.isObsolete]) {
                    AnimeExtensionTable.deleteWhere { AnimeExtensionTable.pkgName eq pkgName }
                } else {
                    AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
                        it[isInstalled] = false
                    }
                }

                sources
            }

        if (File(jarPath).exists()) {
            // free up the file descriptor if exists
            PackageTools.jarLoaderMap.remove(jarPath)?.close()

            // clear all loaded sources
            sources.forEach { GetCatalogueSource.unregisterCatalogueSource(it) }

            File(jarPath).delete()
        }
    }

    suspend fun updateExtension(pkgName: String): Int {
        val targetExtension = ExtensionsList.updateMap.remove(pkgName)!!
        uninstallExtension(pkgName)
        transaction {
            AnimeExtensionTable.update({ AnimeExtensionTable.pkgName eq pkgName }) {
                it[name] = targetExtension.name
                it[versionName] = targetExtension.versionName
                it[versionCode] = targetExtension.versionCode
                it[lang] = targetExtension.lang
                it[isNsfw] = targetExtension.isNsfw
                it[apkName] = targetExtension.apkName
                it[iconUrl] = targetExtension.iconUrl
                it[hasUpdate] = false
            }
        }
        return installExtension(pkgName)
    }

    suspend fun getExtensionIcon(apkName: String): Pair<InputStream, String> {
        val iconUrl =
            transaction {
                AnimeExtensionTable.selectAll().where { AnimeExtensionTable.apkName eq apkName }.first()
            }[AnimeExtensionTable.iconUrl]

        val cacheSaveDir = "${applicationDirs.extensionsRoot}/icon"

        return getImageResponse(cacheSaveDir, apkName) {
            network.client
                .newCall(
                    GET(iconUrl, cache = CacheControl.FORCE_NETWORK),
                ).await()
        }
    }

    fun getExtensionIconUrl(apkName: String): String = "/api/v1/anime/extension/icon/$apkName"
}
