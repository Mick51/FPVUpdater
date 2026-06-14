package com.example.fpvupdater

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {
    
    private val _projects = MutableStateFlow(listOf(
        ProjectInfo("Betaflight", "betaflight", "betaflight", "https://avatars.githubusercontent.com/u/19597933?s=200&v=4"),
        ProjectInfo("INAV", "iNavFlight", "inav", "https://avatars.githubusercontent.com/u/15979895?s=200&v=4"),
        ProjectInfo("Gyroflow", "gyroflow", "gyroflow", "https://avatars.githubusercontent.com/u/94141432?s=200&v=4"),
        ProjectInfo("BLHeli_s", "bitdump", "BLHeli", "https://avatars.githubusercontent.com/u/1569750?v=4"),
        ProjectInfo("Bluejay", "bird-sanctuary", "bluejay", "https://avatars.githubusercontent.com/u/110229629?s=200&v=4"),
        ProjectInfo("AM32", "am32-firmware", "AM32", "https://avatars.githubusercontent.com/u/155652001?s=200&v=4"),
        ProjectInfo("ExpressLRS", "ExpressLRS", "ExpressLRS", "https://avatars.githubusercontent.com/u/77287864?s=200&v=4"),
        ProjectInfo("EdgeTX", "EdgeTX", "edgetx", "https://avatars.githubusercontent.com/u/83762968?s=200&v=4")
    ))
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val notificationsEnabled = dataStoreManager.isNotificationsEnabled

    init {
        Log.d("API_DEBUG", "Token chargé : ${BuildConfig.GITHUB_TOKEN.take(10)}...")
        loadLocalData()
    }

    private fun getStorageKey(project: ProjectInfo): String {
        return project.repo // Simplified since we now group Stable/Beta in one Info
    }

    private fun loadLocalData() {
        viewModelScope.launch {
            _projects.value = _projects.value.map { project ->
                val localStable = dataStoreManager.getVersion("${project.repo}_stable") ?: "Chargement..."
                val localBeta = dataStoreManager.getVersion("${project.repo}_beta") ?: "Chargement..."
                project.copy(stableVersion = localStable, betaVersion = localBeta)
            }
        }
    }

    private fun isNewVersionAvailable(local: String, online: String): Boolean {
        // Simple comparison to check for a new version
        val ignoredValues = listOf("Chargement...", "Loading...", "Aucune version", "No version", "Erreur réseau", "Network error")
        return !ignoredValues.contains(local) && local != online
    }

    private fun sendNotification(context: Context, projectName: String, version: String) {
        val title = context.getString(R.string.new_version_title, projectName)
        val message = context.getString(R.string.new_version_message, version)
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, "FPV_UPDATES_CHANNEL")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(androidx.core.app.NotificationManagerCompat.from(context)) {
            try {
                notify(projectName.hashCode(), builder.build())
            } catch (_: SecurityException) { }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("FPV_UPDATES_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun refreshData(context: Context? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            // On bascule sur le dispatcher IO pour toute la logique réseau
            val updatedList = withContext(Dispatchers.IO) {
                _projects.value.map { project ->
                    async {
                        try {
                            val releases = RetrofitInstance.api.getAllReleases(project.owner, project.repo)
                            
                            val beta = releases.firstOrNull { it.prerelease }
                            var stableResult = releases.firstOrNull { !it.prerelease }

                            // Si pas de releases, on essaie les Tags pour la version Stable
                            if (stableResult == null) {
                                val tags = RetrofitInstance.api.getTags(project.owner, project.repo)
                                if (tags.isNotEmpty()) {
                                    val latestTag = tags[0]
                                    stableResult = ReleaseResponse(
                                        tagName = latestTag.name,
                                        name = latestTag.name,
                                        htmlUrl = "https://github.com/${project.owner}/${project.repo}/tags",
                                        prerelease = false
                                    )
                                }
                            }

                            val localStable = dataStoreManager.getVersion("${project.repo}_stable")
                            val localBeta = dataStoreManager.getVersion("${project.repo}_beta")

                            if (stableResult != null) {
                                if (context != null && localStable != null && isNewVersionAvailable(localStable, stableResult.tagName)) {
                                    createNotificationChannel(context)
                                    sendNotification(context, "${project.name} (Stable)", stableResult.tagName)
                                }
                                dataStoreManager.saveVersion("${project.repo}_stable", stableResult.tagName)
                            }
                            
                            if (beta != null) {
                                if (context != null && localBeta != null && isNewVersionAvailable(localBeta, beta.tagName)) {
                                    createNotificationChannel(context)
                                    sendNotification(context, "${project.name} (Beta)", beta.tagName)
                                }
                                dataStoreManager.saveVersion("${project.repo}_beta", beta.tagName)
                            }

                            project.copy(
                                stableVersion = stableResult?.tagName ?: "No version",
                                stableUrl = stableResult?.htmlUrl ?: "",
                                betaVersion = beta?.tagName ?: "No Beta",
                                betaUrl = beta?.htmlUrl ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Erreur lors du chargement de ${project.name}: ${e.message}", e)
                            val localStable = dataStoreManager.getVersion("${project.repo}_stable") ?: "Network error"
                            val localBeta = dataStoreManager.getVersion("${project.repo}_beta") ?: "Network error"
                            project.copy(stableVersion = localStable, betaVersion = localBeta)
                        }
                    }
                }.awaitAll()
            }
            
            _projects.value = updatedList
            _isRefreshing.value = false
        }
    }

    fun toggleNotifications(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            dataStoreManager.setNotificationsEnabled(enabled)
            
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    "FPVUpdateCheck",
                    ExistingPeriodicWorkPolicy.KEEP,
                    updateRequest
                )
            } else {
                workManager.cancelUniqueWork("FPVUpdateCheck")
            }
        }
    }
}