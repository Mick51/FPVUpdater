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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    
    companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val USER_REPOS = stringPreferencesKey("user_repos")
        val THEME_MODE = stringPreferencesKey("theme_mode") // "dark", "light", "system"
        val LANGUAGE = stringPreferencesKey("language") // "fr", "en", "it", "es", "pl", "ru", "system"
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
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

    // Lire une version spécifique
    suspend fun getVersion(repo: String): String? {
        val key = stringPreferencesKey(repo)
        val preferences = context.dataStore.data.first()
        return preferences[key]
    }

    // Lire toutes les préférences d'un coup (optimisation)
    suspend fun getAllPreferences(): Map<String, String> {
        return context.dataStore.data.first().asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[THEME_MODE] ?: "dark" }
        .distinctUntilChanged()

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[LANGUAGE] = lang }
    }

    val language: Flow<String> = context.dataStore.data
        .map { it[LANGUAGE] ?: "system" }
        .distinctUntilChanged()

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_REFRESH] = enabled }
    }

    val isAutoRefreshEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[AUTO_REFRESH] ?: true }
        .distinctUntilChanged()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    val isNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[NOTIFICATIONS_ENABLED] ?: true }
        .distinctUntilChanged()
}
