package zuiwan.tube

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.zuiwan.BuildConfig
import app.zuiwan.R
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import zuiwan.Activity
import zuiwan.model.GhRelease
import zuiwan.model.GhRunsPayload
import zuiwan.model.ReleaseNote
import java.time.Instant

class AppUpdater(
    private val context: Context,
    private val tube: TubeClient,
) {
    private val channelId = "updates"
    private val notifyId = 1001
    private val cacheWindowMs = 2 * 60 * 60 * 1_000L

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
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when a new version of Zuiwan is available"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    fun comparever(v1: String, v2: String): Int {
        val clean1 = v1.trim().removePrefix("v").removePrefix("V")
        val clean2 = v2.trim().removePrefix("v").removePrefix("V")
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
        val rawVer = rel.tagName.trim().removePrefix("v").removePrefix("V")
            .ifBlank { rel.name.trim().removePrefix("v").removePrefix("V") }
        val apkAssets = rel.assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank()
        }
        val preferred = if (BuildConfig.DEBUG) {
            apkAssets.firstOrNull { it.name.contains("debug", ignoreCase = true) }
                ?: apkAssets.firstOrNull()
        } else {
            apkAssets.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
                ?: apkAssets.firstOrNull()
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

    fun postalert(release: ReleaseNote) {
        initchannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = Intent(context, Activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_update", true)
        }
        val pFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openPending = PendingIntent.getActivity(context, 1001, openIntent, pFlags)

        val dlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
        val dlPending = PendingIntent.getActivity(context, 1002, dlIntent, pFlags)

        val headline = "Zuiwan ${release.versionName} is ready to download"
        val firstNotes = release.body
            .lineSequence()
            .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("#").trim() }
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString("\n")
        val bigBody = if (firstNotes.isNotBlank()) "$headline\n\n$firstNotes" else headline

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("Update available")
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .addAction(0, "Download", dlPending)
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
