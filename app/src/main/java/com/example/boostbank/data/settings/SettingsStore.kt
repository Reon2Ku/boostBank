package com.example.boostbank.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.boostbank.model.AppSettings
import com.example.boostbank.model.MainPage
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "boostbank_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                language = preferences[LANGUAGE] ?: "简体中文",
                confirmBeforeReward = preferences[CONFIRM_BEFORE_REWARD] ?: true,
                confirmBeforeEarn = preferences[CONFIRM_BEFORE_EARN] ?: true,
                useWarmBackground = preferences[USE_WARM_BACKGROUND] ?: false,
                avatarUri = preferences[AVATAR_URI],
                earnBackgroundUri = preferences[EARN_BACKGROUND_URI],
                rewardBackgroundUri = preferences[REWARD_BACKGROUND_URI],
                overviewBackgroundUri = preferences[OVERVIEW_BACKGROUND_URI],
                meBackgroundUri = preferences[ME_BACKGROUND_URI]
            )
        }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[LANGUAGE] = language }
    }

    suspend fun setConfirmBeforeReward(enabled: Boolean) {
        context.dataStore.edit { it[CONFIRM_BEFORE_REWARD] = enabled }
    }

    suspend fun setAvatarUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) preferences.remove(AVATAR_URI) else preferences[AVATAR_URI] = uri
        }
    }

    suspend fun setConfirmBeforeEarn(enabled: Boolean) {
        context.dataStore.edit { it[CONFIRM_BEFORE_EARN] = enabled }
    }

    suspend fun setUseWarmBackground(enabled: Boolean) {
        context.dataStore.edit { it[USE_WARM_BACKGROUND] = enabled }
    }

    suspend fun setPageBackground(page: MainPage, uri: String?) {
        context.dataStore.edit { preferences ->
            val key = backgroundKeyFor(page)
            if (uri.isNullOrBlank()) {
                preferences.remove(key)
            } else {
                preferences[key] = uri
            }
        }
    }

    private fun backgroundKeyFor(page: MainPage): Preferences.Key<String> {
        return when (page) {
            MainPage.EARN -> EARN_BACKGROUND_URI
            MainPage.REWARD -> REWARD_BACKGROUND_URI
            MainPage.OVERVIEW -> OVERVIEW_BACKGROUND_URI
            MainPage.ME -> ME_BACKGROUND_URI
        }
    }

    companion object {
        private val LANGUAGE = stringPreferencesKey("language")
        private val CONFIRM_BEFORE_REWARD = booleanPreferencesKey("confirm_before_reward")
        private val CONFIRM_BEFORE_EARN = booleanPreferencesKey("confirm_before_earn")
        private val USE_WARM_BACKGROUND = booleanPreferencesKey("use_warm_background")
        private val AVATAR_URI = stringPreferencesKey("avatar_uri")
        private val EARN_BACKGROUND_URI = stringPreferencesKey("earn_background_uri")
        private val REWARD_BACKGROUND_URI = stringPreferencesKey("reward_background_uri")
        private val OVERVIEW_BACKGROUND_URI = stringPreferencesKey("overview_background_uri")
        private val ME_BACKGROUND_URI = stringPreferencesKey("me_background_uri")
    }
}