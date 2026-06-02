package com.prism.music.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prism_settings")

class SettingsStore(private val context: Context) {

    companion object {
        val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
        val YANDEX_TOKEN_KEY = stringPreferencesKey("yandex_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")

        const val DEFAULT_BACKEND_URL = "https://pm.standrise.net"
        val HOSTS = listOf(
            "https://pm.standrise.net",
            "https://prism-music-one.vercel.app"
        )
    }

    val backendURL: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BACKEND_URL_KEY] ?: DEFAULT_BACKEND_URL
    }

    val yandexToken: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[YANDEX_TOKEN_KEY] ?: ""
    }

    val userId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY] ?: ""
    }

    val username: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USERNAME_KEY] ?: ""
    }

    suspend fun getBackendURL(): String {
        return backendURL.first()
    }

    suspend fun getYandexToken(): String {
        return yandexToken.first()
    }

    suspend fun getUserId(): String {
        return userId.first()
    }

    suspend fun getUsername(): String {
        return username.first()
    }

    suspend fun setBackendURL(url: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKEND_URL_KEY] = url
        }
    }

    suspend fun setYandexToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[YANDEX_TOKEN_KEY] = token
        }
    }

    suspend fun setUserId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = id
        }
    }

    suspend fun setUsername(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = name
        }
    }

    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(YANDEX_TOKEN_KEY)
        }
    }
}
