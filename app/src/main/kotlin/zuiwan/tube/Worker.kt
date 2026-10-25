package zuiwan.tube

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zuiwan.App

private const val UPDATE_JOB_ID = 4401

fun schedulejob(context: Context) {
    val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
    if (scheduler.allPendingJobs.any { it.id == UPDATE_JOB_ID }) return
    val info = JobInfo.Builder(
        UPDATE_JOB_ID,
        ComponentName(context, UpdateJob::class.java),
    )
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setPeriodic(3L * 60L * 60L * 1_000L)
        .setPersisted(true)
        .build()
    runCatching { scheduler.schedule(info) }
}

class UpdateJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTask: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as? App ?: return false
        activeTask = scope.launch {
            runCatching {
                val cfg = app.hub.store.settings.first()
                if (cfg.checkUpdates && cfg.updateNotifications) {
                    app.hub.updater.checkupdate(
                        force = true,
                        includeNightly = cfg.nightlyUpdates,
                    ).onSuccess { (release, hasNew) ->
                        if (hasNew && release != null && release.tagName != cfg.lastNotifiedTag) {
                            if (app.hub.updater.postalert(release)) {
                                app.hub.store.updatesettings { it.copy(lastNotifiedTag = release.tagName) }
                            }
                        }
                    }
                }
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeTask?.cancel()
        return true
    }
}
