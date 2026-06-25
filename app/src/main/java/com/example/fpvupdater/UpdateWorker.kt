/*
 * Copyright (C) 2026 Mick
 *
 * Ce programme est un logiciel libre : vous pouvez le redistribuer et/ou le modifier
 * selon les termes de la Licence Publique Générale GNU telle que publiée par
 * la Free Software Foundation, soit la version 3 de la licence, ou (au choix)
 * toute version ultérieure.
 *
 * Ce programme est distribué dans l'espoir qu'il sera utile, mais SANS AUCUNE GARANTIE ;
 * sans même la garantie implicite de COMMERCIALISATION ou D'ADÉQUATION À UN USAGE PARTICULIER.
 * Voir la Licence Publique Générale GNU pour plus de détails.
 *
 * Vous devriez avoir reçu une copie de la Licence Publique Générale GNU avec ce programme.
 * Sinon, voir <https://www.gnu.org/licenses/>.
 */

package com.example.fpvupdater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.flow.first

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private fun parseVersion(version: String): List<Int> {
        return version.lowercase()
            .replace(Regex("[^0-9.]"), "")
            .split(".")
            .mapNotNull { it.toIntOrNull() }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = parseVersion(v1)
        val parts2 = parseVersion(v2)
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }

    override suspend fun doWork(): Result {
        val dataStore = DataStoreManager(applicationContext)
        
        if (!dataStore.isNotificationsEnabled.first()) {
            return Result.success()
        }

        val defaultProjects = listOf(
            ProjectInfo("Betaflight", "betaflight", "betaflight", "https://avatars.githubusercontent.com/u/19597933?s=200&v=4"),
            ProjectInfo("INAV", "iNavFlight", "inav", "https://avatars.githubusercontent.com/u/15979895?s=200&v=4"),
            ProjectInfo("Gyroflow", "gyroflow", "gyroflow", "https://avatars.githubusercontent.com/u/94141432?s=200&v=4"),
            ProjectInfo("BLHeli_s", "bitdump", "BLHeli", "https://avatars.githubusercontent.com/u/1569750?v=4"),
            ProjectInfo("Bluejay", "bird-sanctuary", "bluejay", "https://avatars.githubusercontent.com/u/110229629?s=200&v=4"),
            ProjectInfo("AM32", "am32-firmware", "AM32", "https://avatars.githubusercontent.com/u/155652001?s=200&v=4"),
            ProjectInfo("ExpressLRS", "ExpressLRS", "ExpressLRS", "https://avatars.githubusercontent.com/u/77287864?s=200&v=4"),
            ProjectInfo("EdgeTX", "EdgeTX", "edgetx", "https://avatars.githubusercontent.com/u/83762968?s=200&v=4")
        )

        val userProjects = dataStore.getUserRepos().first()
        val projectsToCheck = defaultProjects + userProjects

        createNotificationChannel()

        projectsToCheck.forEach { project ->
            try {
                val releases = RetrofitInstance.api.getAllReleases(project.owner, project.repo)
                val sortedByDate = releases.sortedByDescending { it.publishedAt ?: "" }

                // Recherche SemVer (numéro le plus haut)
                var stable: ReleaseResponse? = releases
                    .filter { !it.prerelease && !it.tagName.startsWith("untagged-") }
                    .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }

                if (stable == null) {
                    try {
                        stable = RetrofitInstance.api.getLatestRelease(project.owner, project.repo)
                    } catch (_: Exception) {}
                }

                if (stable == null) {
                    stable = sortedByDate.firstOrNull { !it.prerelease }
                }

                if (stable == null || stable.tagName.startsWith("untagged-")) {
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
                    val currentStable = stable
                    val lastKnownStable = dataStore.getVersion("${project.repo}_stable")
                    if (lastKnownStable != null && lastKnownStable != currentStable.tagName && isNewVersionAvailable(lastKnownStable, currentStable.tagName)) {
                        sendNotification("${project.name} (Stable)", currentStable.tagName)
                    }
                    dataStore.saveVersion("${project.repo}_stable", currentStable.tagName)
                }

                // Vérification Beta
                val beta = sortedByDate.firstOrNull { it.prerelease }
                if (beta != null) {
                    val lastKnownBeta = dataStore.getVersion("${project.repo}_beta")
                    if (lastKnownBeta != null && lastKnownBeta != beta.tagName && isNewVersionAvailable(lastKnownBeta, beta.tagName)) {
                        sendNotification("${project.name} (Beta)", beta.tagName)
                    }
                    dataStore.saveVersion("${project.repo}_beta", beta.tagName)
                }
            } catch (e: Exception) {
                Log.e("UpdateWorker", "Erreur lors du check pour ${project.name}: ${e.message}")
            }
        }

        return Result.success()
    }

    private fun isNewVersionAvailable(local: String, online: String): Boolean {
        val ignoredValues = listOf("Chargement...", "Loading...", "Aucune version", "No version", "Erreur réseau", "Network error")
        return !ignoredValues.contains(local) && local != online
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
