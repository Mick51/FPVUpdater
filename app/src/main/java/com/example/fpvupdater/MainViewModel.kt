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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {
    
    private val defaultProjects = listOf(
        ProjectInfo("Betaflight", "betaflight", "betaflight", "https://avatars.githubusercontent.com/u/19597933?s=200&v=4"),
        ProjectInfo("INAV", "iNavFlight", "inav", "https://avatars.githubusercontent.com/u/15979895?s=200&v=4"),
        ProjectInfo("Gyroflow", "gyroflow", "gyroflow", "https://avatars.githubusercontent.com/u/94141432?s=200&v=4"),
        ProjectInfo("BLHeli_s", "bitdump", "BLHeli", "https://avatars.githubusercontent.com/u/1569750?v=4"),
        ProjectInfo("Bluejay", "bird-sanctuary", "bluejay", "https://avatars.githubusercontent.com/u/110229629?s=200&v=4"),
        ProjectInfo("AM32", "am32-firmware", "AM32", "https://avatars.githubusercontent.com/u/155652001?s=200&v=4"),
        ProjectInfo("ExpressLRS", "ExpressLRS", "ExpressLRS", "https://avatars.githubusercontent.com/u/77287864?s=200&v=4"),
        ProjectInfo("EdgeTX", "EdgeTX", "edgetx", "https://avatars.githubusercontent.com/u/83762968?s=200&v=4")
    )

    private val _userProjects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    
    // On utilise un StateFlow pour stocker uniquement les versions chargées
    // La clé est "owner/repo"
    private val _projectData = MutableStateFlow<Map<String, ProjectInfo>>(emptyMap())

    val projects: StateFlow<List<ProjectInfo>> = combine(_userProjects, _projectData) { userRepos, dataMap ->
        (defaultProjects + userRepos).map { project ->
            val key = "${project.owner}/${project.repo}"
            dataMap[key] ?: project
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultProjects)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val notificationsEnabled = dataStoreManager.isNotificationsEnabled

    init {
        Log.d("API_DEBUG", "Token chargé : ${BuildConfig.GITHUB_TOKEN.take(10)}...")
        
        viewModelScope.launch {
            dataStoreManager.getUserRepos().collect { userRepos ->
                // On s'assure que les icônes sont présentes
                val updatedUserRepos = userRepos.map { repo ->
                    if (repo.iconUrl.isEmpty()) {
                        repo.copy(iconUrl = "https://github.com/${repo.owner}.png")
                    } else repo
                }
                _userProjects.value = updatedUserRepos
                // On charge les versions locales pour les nouveaux projets
                loadLocalDataFor(defaultProjects + updatedUserRepos)
            }
        }
    }

    private suspend fun loadLocalDataFor(projectList: List<ProjectInfo>) {
        val currentData = _projectData.value.toMutableMap()
        projectList.forEach { project ->
            val key = "${project.owner}/${project.repo}"
            if (!currentData.containsKey(key)) {
                val localStable = dataStoreManager.getVersion("${project.repo}_stable") ?: "Loading..."
                val localBeta = dataStoreManager.getVersion("${project.repo}_beta") ?: "Loading..."
                currentData[key] = project.copy(stableVersion = localStable, betaVersion = localBeta)
            }
        }
        _projectData.value = currentData
    }

    fun addUserRepository(name: String, owner: String, repo: String) {
        viewModelScope.launch {
            var finalOwner = owner
            var finalRepo = repo

            if (owner.contains("github.com/")) {
                val cleanUrl = owner.substringAfter("github.com/").removeSuffix(".git").removeSuffix("/")
                val parts = cleanUrl.split("/")
                if (parts.size >= 2) {
                    finalOwner = parts[0]
                    finalRepo = parts[1]
                }
            }

            val newRepo = ProjectInfo(
                name = name,
                owner = finalOwner,
                repo = finalRepo,
                iconUrl = "https://github.com/$finalOwner.png",
                isUserAdded = true
            )
            val currentList = _userProjects.value.toMutableList()
            if (!currentList.any { it.owner == finalOwner && it.repo == finalRepo }) {
                currentList.add(newRepo)
                dataStoreManager.saveUserRepos(currentList)
                // Rafraîchir immédiatement après l'ajout
                refreshData()
            }
        }
    }

    fun removeUserRepository(project: ProjectInfo) {
        if (!project.isUserAdded) return
        viewModelScope.launch {
            val currentList = _userProjects.value.toMutableList()
            currentList.removeAll { it.owner == project.owner && it.repo == project.repo }
            dataStoreManager.saveUserRepos(currentList)
        }
    }

    private fun isNewVersionAvailable(local: String, online: String): Boolean {
        val ignoredValues = listOf("Chargement...", "Loading...", "Aucune version", "No version", "Erreur réseau", "Network error")
        return !ignoredValues.contains(local) && local != online
    }

    private fun parseVersion(version: String): List<Int> {
        return version.lowercase()
            .replace(Regex("[^0-9.]"), "") // Garde uniquement les chiffres et les points
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

    fun refreshData(context: Context? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            // On récupère les dépôts utilisateur directement depuis le DataStore 
            // pour être sûr d'avoir la liste complète dès l'ouverture
            val userRepos = dataStoreManager.getUserRepos().first().map { repo ->
                if (repo.iconUrl.isEmpty()) {
                    repo.copy(iconUrl = "https://github.com/${repo.owner}.png")
                } else repo
            }
            val currentProjects = defaultProjects + userRepos
            
            val results = withContext(Dispatchers.IO) {
                currentProjects.map { project ->
                    async {
                        try {
                            val releases = RetrofitInstance.api.getAllReleases(project.owner, project.repo)
                            val sortedByDate = releases.sortedByDescending { it.publishedAt ?: "" }

                            // On cherche la version avec le numéro le plus élevé (SemVer)
                            var stableResult = releases
                                .filter { !it.prerelease && !it.tagName.startsWith("untagged-") }
                                .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }
                            
                            // Si on ne trouve rien par SemVer, on prend la "Latest" officielle
                            if (stableResult == null) {
                                try {
                                    stableResult = RetrofitInstance.api.getLatestRelease(project.owner, project.repo)
                                } catch (_: Exception) {}
                            }

                            // Si toujours rien, on prend la plus récente chronologiquement
                            if (stableResult == null) {
                                stableResult = sortedByDate.firstOrNull { !it.prerelease }
                            }

                            val beta = sortedByDate.firstOrNull { it.prerelease }

                            // Si toujours rien du tout, on tente les Tags
                            if (stableResult == null || stableResult.tagName.startsWith("untagged-")) {
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

                            val key = "${project.owner}/${project.repo}"
                            key to project.copy(
                                stableVersion = stableResult?.tagName ?: "No version",
                                stableUrl = stableResult?.htmlUrl ?: "",
                                betaVersion = beta?.tagName ?: "No Beta",
                                betaUrl = beta?.htmlUrl ?: ""
                            )
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Erreur lors du chargement de ${project.name}: ${e.message}", e)
                            val localStable = dataStoreManager.getVersion("${project.repo}_stable") ?: "Network error"
                            val localBeta = dataStoreManager.getVersion("${project.repo}_beta") ?: "Network error"
                            val key = "${project.owner}/${project.repo}"
                            key to project.copy(stableVersion = localStable, betaVersion = localBeta)
                        }
                    }
                }.awaitAll()
            }
            
            _projectData.value = results.toMap()
            _isRefreshing.value = false
        }
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
