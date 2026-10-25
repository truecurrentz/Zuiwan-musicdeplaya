package zuiwan.tube

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.zuiwan.BuildConfig
import app.zuiwan.R
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import zuiwan.Activity
import zuiwan.model.GhRelease
import zuiwan.model.GhRunsPayload
import zuiwan.model.ReleaseNote
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

class AppUpdater(
    private val context: Context,
    private val tube: TubeClient,
) {
    private val channelId = "zuiwan_updates"
    private val notifyId = 1001
    private val cacheWindowMs = 2 * 60 * 60 * 1_000L
    private val verRegex = Regex("""(\d+(?:\.\d+)+(?:-?[A-Za-z0-9.]+)?)""")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var cachedLatest: ReleaseNote? = null
    private var cachedList: List<ReleaseNote> = emptyList()
    private var cachedHasUpdate: Boolean = false
    private var cachedNightly: Boolean = false
    var lastCheckMs: Long = 0L
        private set

    fun initchannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Zuiwan Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Phone notifications when a new Zuiwan release is published"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun extractver(raw: String): String {
        val trimmed = raw.trim()
        val found = verRegex.find(trimmed)?.groupValues?.getOrNull(1)
        return (found ?: trimmed.removePrefix("v").removePrefix("V")).trim()
    }

    fun comparever(v1: String, v2: String): Int {
        val clean1 = extractver(v1)
        val clean2 = extractver(v2)
        val base1 = clean1.substringBefore("-")
        val base2 = clean2.substringBefore("-")
        val parts1 = base1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = base2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        val suf1 = clean1.substringAfter("-", "")
        val suf2 = clean2.substringAfter("-", "")
        return when {
            suf1.isEmpty() && suf2.isNotEmpty() -> 1
            suf1.isNotEmpty() && suf2.isEmpty() -> -1
            else -> suf1.compareTo(suf2)
        }
    }

    fun isnewer(currentVer: String, candidateVer: String): Boolean =
        comparever(candidateVer, currentVer) > 0

    suspend fun loadreleases(
        force: Boolean = false,
        includePre: Boolean = false,
    ): Result<List<ReleaseNote>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            if (!force && cachedList.isNotEmpty() && (now - lastCheckMs) < cacheWindowMs) {
                val filtered = if (includePre) cachedList else cachedList.filter { !it.isNightly }
                return@withContext Result.success(filtered)
            }

            val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases?per_page=30"
            val res = tube.http.get(url) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "Zuiwan/${BuildConfig.VERSION_NAME}")
            }
            if (res.status == HttpStatusCode.NotFound) {
                cachedList = emptyList()
                return@withContext Result.success(emptyList())
            }
            if (!res.status.isSuccess()) {
                error("GitHub API returned ${res.status.value}")
            }
            val raw = res.bodyAsText()
            val ghList = json.decodeFromString<List<GhRelease>>(raw)
            val parsed = ghList
                .filter { !it.draft && (includePre || !it.prerelease) && it.tagName.isNotBlank() }
                .map { maprelease(it) }

            cachedList = parsed
            Result.success(parsed)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            if (cachedList.isNotEmpty()) {
                Result.success(cachedList)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun checkupdate(
        force: Boolean = false,
        includeNightly: Boolean = false,
    ): Result<Pair<ReleaseNote?, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val validCache = !force &&
                lastCheckMs > 0L &&
                (now - lastCheckMs) < cacheWindowMs &&
                cachedNightly == includeNightly

            if (validCache) {
                return@withContext Result.success(cachedLatest to cachedHasUpdate)
            }

            val releases = loadreleases(force = true, includePre = includeNightly).getOrThrow()
            val topRelease = releases.maxWithOrNull { a, b -> comparever(a.versionName, b.versionName) }
                ?: releases.firstOrNull()

            if (topRelease != null && isnewer(BuildConfig.VERSION_NAME, topRelease.versionName)) {
                cachedLatest = topRelease
                cachedHasUpdate = true
                cachedNightly = includeNightly
                lastCheckMs = now
                return@withContext Result.success(topRelease to true)
            }

            if (includeNightly) {
                val nightly = fetchnightly()
                if (nightly != null) {
                    cachedLatest = nightly
                    cachedHasUpdate = true
                    cachedNightly = true
                    lastCheckMs = now
                    return@withContext Result.success(nightly to true)
                }
            }

            cachedLatest = topRelease
            cachedHasUpdate = false
            cachedNightly = includeNightly
            lastCheckMs = now
            Result.success(topRelease to false)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchnightly(): ReleaseNote? {
        return runCatching {
            val url =
                "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/actions/workflows/build.yml/runs?branch=main&status=completed&per_page=5"
            val res = tube.http.get(url) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "Zuiwan/${BuildConfig.VERSION_NAME}")
            }
            if (!res.status.isSuccess()) return null
            val payload = json.decodeFromString<GhRunsPayload>(res.bodyAsText())
            val run = payload.runs.firstOrNull { it.conclusion == "success" && it.headSha.isNotBlank() }
                ?: return null

            val shortSha = run.headSha.take(7)
            if (shortSha.equals(BuildConfig.GIT_SHA, ignoreCase = true)) return null

            val stamp = run.updatedAt.ifBlank { run.createdAt }
            val runMs = runCatching { Instant.parse(stamp).toEpochMilli() }.getOrDefault(0L)
            if (runMs <= BuildConfig.BUILD_TIME_MS + 90_000L) return null

            val artifactName = if (BuildConfig.DEBUG) "Zuiwan-debug" else "Zuiwan-release"
            val dlUrl =
                "https://nightly.link/${BuildConfig.GITHUB_REPO}/workflows/build/main/$artifactName.zip"
            val commitMsg = run.headCommit?.message?.trim().orEmpty().ifBlank { run.displayTitle }

            ReleaseNote(
                tagName = "nightly-$shortSha",
                versionName = "${BuildConfig.VERSION_NAME}-$shortSha",
                title = "Nightly Build ($shortSha)",
                body = commitMsg,
                publishedAt = stamp,
                htmlUrl = run.htmlUrl.ifBlank { "https://github.com/${BuildConfig.GITHUB_REPO}/actions" },
                downloadUrl = dlUrl,
                apkSizeBytes = 0L,
                isNightly = true,
            )
        }.getOrNull()
    }

    private fun maprelease(rel: GhRelease): ReleaseNote {
        val rawVer = extractver(rel.tagName).ifBlank { extractver(rel.name) }
        val apkAssets = rel.assets.filter {
            (it.name.endsWith(".apk", ignoreCase = true) || it.name.endsWith(".zip", ignoreCase = true)) &&
                it.downloadUrl.isNotBlank()
        }
        val onlyApks = apkAssets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val pool = onlyApks.ifEmpty { apkAssets }
        val preferred = if (BuildConfig.DEBUG) {
            pool.firstOrNull { it.name.contains("debug", ignoreCase = true) }
                ?: pool.firstOrNull()
        } else {
            pool.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
                ?: pool.firstOrNull()
        }
        val fallbackPage = rel.htmlUrl.ifBlank {
            "https://github.com/${BuildConfig.GITHUB_REPO}/releases"
        }

        return ReleaseNote(
            tagName = rel.tagName,
            versionName = rawVer.ifBlank { rel.tagName },
            title = rel.name.ifBlank { rel.tagName },
            body = rel.body?.trim().orEmpty(),
            publishedAt = rel.publishedAt,
            htmlUrl = fallbackPage,
            downloadUrl = preferred?.downloadUrl ?: fallbackPage,
            apkSizeBytes = preferred?.size ?: 0L,
            isNightly = rel.prerelease,
        )
    }

    fun isdirectapk(url: String): Boolean {
        val clean = url.substringBefore("?").lowercase()
        return clean.endsWith(".apk") ||
            clean.endsWith(".zip") ||
            url.contains("/releases/download/", ignoreCase = true)
    }

    fun caninstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun opensources() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun openbrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    suspend fun downloadapk(
        release: ReleaseNote,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }

            val rawFile = File(dir, "payload.bin")
            val finalApk = File(dir, "Zuiwan-update.apk")

            val conn = openconn(release.downloadUrl)
            try {
                val total = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
                conn.inputStream.use { ins ->
                    FileOutputStream(rawFile).use { outs ->
                        val buf = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = ins.read(buf)
                            if (n < 0) break
                            if (n > 0) {
                                outs.write(buf, 0, n)
                                downloaded += n
                                val now = System.currentTimeMillis()
                                if (total > 0L && now - lastEmit >= 75L) {
                                    lastEmit = now
                                    onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0.01f, 0.98f))
                                }
                            }
                        }
                        outs.flush()
                    }
                }
            } finally {
                runCatching { conn.disconnect() }
            }

            unpackapk(rawFile, finalApk)
            runCatching { rawFile.delete() }
            onProgress(1f)
            Result.success(finalApk)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openconn(startUrl: String): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Zuiwan/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/octet-stream, */*")
            }
            val code = conn.responseCode
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("Redirect missing Location header")
                current = URL(URL(current), loc).toString()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                error("Download server returned HTTP $code")
            }
        }
        error("Too many redirects while downloading update")
    }

    @Suppress("DEPRECATION")
    private fun unpackapk(rawFile: File, targetApk: File) {
        if (!rawFile.exists() || rawFile.length() < 64L * 1024L) {
            error("Downloaded update file is incomplete")
        }

        ZipFile(rawFile).use { zip ->
            val hasManifest = zip.getEntry("AndroidManifest.xml") != null
            if (hasManifest) {
                rawFile.copyTo(targetApk, overwrite = true)
            } else {
                val entries = zip.entries().toList().filter {
                    !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true)
                }
                val chosen = if (BuildConfig.DEBUG) {
                    entries.firstOrNull { it.name.contains("debug", ignoreCase = true) }
                        ?: entries.firstOrNull()
                } else {
                    entries.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
                        ?: entries.firstOrNull()
                } ?: error("Archive does not contain an APK file")

                zip.getInputStream(chosen).use { zIn ->
                    FileOutputStream(targetApk).use { fOut ->
                        zIn.copyTo(fOut, 16 * 1024)
                    }
                }
            }
        }

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                targetApk.absolutePath,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(targetApk.absolutePath, 0)
        } ?: error("Downloaded APK package could not be parsed")

        if (info.packageName != context.packageName) {
            error("Package mismatch (${info.packageName} vs ${context.packageName})")
        }
    }

    @Suppress("DEPRECATION")
    fun launchapk(apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) { "Update APK file not found" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        }
        context.startActivity(intent)
    }

    fun postalert(release: ReleaseNote): Boolean {
        initchannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        val pFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val installIntent = Intent(context, Activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("install_update", true)
        }
        val installPending = PendingIntent.getActivity(context, 1002, installIntent, pFlags)

        val firstNotes = release.body
            .lineSequence()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("#").trim() }
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString("\n")
        val shortText = firstNotes.lineSequence().firstOrNull()
            ?: "Tap to install Zuiwan v${release.versionName}"
        val bigBody = if (firstNotes.isNotBlank()) {
            firstNotes
        } else {
            "Zuiwan v${release.versionName} is available. Tap to install."
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.update)
            .setContentTitle("Zuiwan v${release.versionName} available")
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(installPending)
            .setAutoCancel(true)
            .addAction(0, "Update", installPending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
            true
        }.getOrDefault(false)
    }

    fun postprogress(release: ReleaseNote, progress: Float) {
        initchannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val pct = (progress * 100f).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.update)
            .setContentTitle("Updating Zuiwan v${release.versionName}")
            .setContentText("Downloading update ($pct%)")
            .setProgress(100, pct, false)
            .setOnlyAlertOnce(true)
            .setOngoing(pct < 100)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
        }
    }

    fun clearalert() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notifyId)
        }
    }
}
