package com.example.fpvupdater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.flow.first

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dataStore = DataStoreManager(applicationContext)
        
        if (!dataStore.isNotificationsEnabled.first()) {
            return Result.success()
        }

        val projectsToCheck = listOf(
            ProjectInfo("Betaflight", "betaflight", "betaflight", "https://avatars.githubusercontent.com/u/19597933?s=200&v=4"),
            ProjectInfo("INAV", "iNavFlight", "inav", "https://avatars.githubusercontent.com/u/15979895?s=200&v=4"),
            ProjectInfo("Gyroflow", "gyroflow", "gyroflow", "https://avatars.githubusercontent.com/u/94141432?s=200&v=4"),
            ProjectInfo("BLHeli_s", "bitdump", "BLHeli", "https://avatars.githubusercontent.com/u/1569750?v=4"),
            ProjectInfo("Bluejay", "bird-sanctuary", "bluejay", "https://avatars.githubusercontent.com/u/110229629?s=200&v=4"),
            ProjectInfo("AM32", "am32-firmware", "AM32", "https://avatars.githubusercontent.com/u/155652001?s=200&v=4"),
            ProjectInfo("ExpressLRS", "ExpressLRS", "ExpressLRS", "https://avatars.githubusercontent.com/u/77287864?s=200&v=4"),
            ProjectInfo("EdgeTX", "EdgeTX", "edgetx", "https://avatars.githubusercontent.com/u/83762968?s=200&v=4")
        )

        createNotificationChannel()

        projectsToCheck.forEach { project ->
            try {
                val releases = RetrofitInstance.api.getAllReleases(project.owner, project.repo)
                
                // Vérification Stable
                var stable = releases.firstOrNull { !it.prerelease }
                if (stable == null) {
                    val tags = RetrofitInstance.api.getTags(project.owner, project.repo)
                    if (tags.isNotEmpty()) {
                        val latestTag = tags[0]
                        stable = ReleaseResponse(
                            tagName = latestTag.name,
                            name = latestTag.name,
                            htmlUrl = "https://github.com/${project.owner}/${project.repo}/tags",
                            prerelease = false
                        )
                    }
                }

                if (stable != null) {
                    val lastKnownStable = dataStore.getVersion("${project.repo}_stable")
                    if (lastKnownStable != null && lastKnownStable != stable.tagName) {
                        sendNotification("${project.name} (Stable)", stable.tagName)
                        dataStore.saveVersion("${project.repo}_stable", stable.tagName)
                    }
                }

                // Vérification Beta
                val beta = releases.firstOrNull { it.prerelease }
                if (beta != null) {
                    val lastKnownBeta = dataStore.getVersion("${project.repo}_beta")
                    if (lastKnownBeta != null && lastKnownBeta != beta.tagName) {
                        sendNotification("${project.name} (Beta)", beta.tagName)
                        dataStore.saveVersion("${project.repo}_beta", beta.tagName)
                    }
                }
            } catch (_: Exception) { }
        }

        return Result.success()
    }

    private fun sendNotification(projectName: String, version: String) {
        val title = applicationContext.getString(R.string.new_version_title, projectName)
        val message = applicationContext.getString(R.string.new_version_message, version)

        val builder = NotificationCompat.Builder(applicationContext, "FPV_UPDATES_CHANNEL")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            try {
                notify(projectName.hashCode(), builder.build())
            } catch (_: SecurityException) { }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.notification_channel_name)
            val descriptionText = applicationContext.getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("FPV_UPDATES_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}