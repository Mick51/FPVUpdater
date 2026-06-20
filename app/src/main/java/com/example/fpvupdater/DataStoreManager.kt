package com.example.fpvupdater

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    
    companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val USER_REPOS = stringPreferencesKey("user_repos")
    }

    private val gson = Gson()

    // Sauvegarder les dépôts personnalisés
    suspend fun saveUserRepos(repos: List<ProjectInfo>) {
        val json = gson.toJson(repos)
        context.dataStore.edit { it[USER_REPOS] = json }
    }

    // Lire les dépôts personnalisés
    fun getUserRepos(): Flow<List<ProjectInfo>> = context.dataStore.data.map { preferences ->
        val json = preferences[USER_REPOS]
        if (json.isNullOrEmpty()) emptyList()
        else {
            val type = object : TypeToken<List<ProjectInfo>>() {}.type
            gson.fromJson(json, type)
        }
    }

    // Sauvegarder une version
    suspend fun saveVersion(repo: String, version: String) {
        val key = stringPreferencesKey(repo)
        context.dataStore.edit { preferences ->
            preferences[key] = version
        }
    }

    // Lire une version
    suspend fun getVersion(repo: String): String? {
        val key = stringPreferencesKey(repo)
        val preferences = context.dataStore.data.first()
        return preferences[key]
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    val isNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIFICATIONS_ENABLED] ?: true }
}