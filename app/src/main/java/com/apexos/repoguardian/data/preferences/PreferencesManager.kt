package com.apexos.repoguardian.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "repo_guardian_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_GITHUB_TOKEN = stringPreferencesKey("github_token")
        private val KEY_SELECTED_REPO_OWNER = stringPreferencesKey("selected_repo_owner")
        private val KEY_SELECTED_REPO_NAME = stringPreferencesKey("selected_repo_name")
        private val KEY_MODEL_PATH = stringPreferencesKey("model_path")
        private val KEY_BACKEND = stringPreferencesKey("backend") // cpu, gpu, npu
        private val KEY_CUSTOM_RULES = stringPreferencesKey("custom_rules")
        private val KEY_GUIDE_DISMISSED = booleanPreferencesKey("guide_dismissed")
        private val KEY_ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
    }

    // === GitHub Token ===
    suspend fun saveGitHubToken(token: String) {
        context.dataStore.edit { it[KEY_GITHUB_TOKEN] = token }
    }

    suspend fun getGitHubToken(): String? {
        return context.dataStore.data.first()[KEY_GITHUB_TOKEN]
    }

    suspend fun clearGitHubToken() {
        context.dataStore.edit { it.remove(KEY_GITHUB_TOKEN) }
    }

    fun observeGitHubToken(): Flow<String?> {
        return context.dataStore.data.map { it[KEY_GITHUB_TOKEN] }
    }

    // === Selected Repo ===
    suspend fun saveSelectedRepo(owner: String, name: String) {
        context.dataStore.edit {
            it[KEY_SELECTED_REPO_OWNER] = owner
            it[KEY_SELECTED_REPO_NAME] = name
        }
    }

    suspend fun clearSelectedRepo() {
        context.dataStore.edit {
            it.remove(KEY_SELECTED_REPO_OWNER)
            it.remove(KEY_SELECTED_REPO_NAME)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getSelectedRepo(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val owner = prefs[KEY_SELECTED_REPO_OWNER] ?: return null
        val name = prefs[KEY_SELECTED_REPO_NAME] ?: return null
        return Pair(owner, name)
    }

    fun observeSelectedRepo(): Flow<Pair<String, String>?> {
        return context.dataStore.data.map { prefs ->
            val owner = prefs[KEY_SELECTED_REPO_OWNER] ?: return@map null
            val name = prefs[KEY_SELECTED_REPO_NAME] ?: return@map null
            Pair(owner, name)
        }
    }

    // === Model Path ===
    suspend fun saveModelPath(path: String) {
        context.dataStore.edit { it[KEY_MODEL_PATH] = path }
    }

    suspend fun getModelPath(): String? {
        return context.dataStore.data.first()[KEY_MODEL_PATH]
    }

    fun observeModelPath(): Flow<String?> {
        return context.dataStore.data.map { it[KEY_MODEL_PATH] }
    }

    // === Backend ===
    suspend fun saveBackend(backend: String) {
        context.dataStore.edit { it[KEY_BACKEND] = backend }
    }

    suspend fun getBackend(): String {
        return context.dataStore.data.first()[KEY_BACKEND] ?: "cpu"
    }

    fun observeBackend(): Flow<String> {
        return context.dataStore.data.map { it[KEY_BACKEND] ?: "cpu" }
    }

    // === Custom Rules ===
    suspend fun saveCustomRules(rules: String) {
        context.dataStore.edit { it[KEY_CUSTOM_RULES] = rules }
    }

    suspend fun getCustomRules(): String {
        return context.dataStore.data.first()[KEY_CUSTOM_RULES] ?: ""
    }

    fun observeCustomRules(): Flow<String> {
        return context.dataStore.data.map { it[KEY_CUSTOM_RULES] ?: "" }
    }

    // === Non-Techy Guide Dismissed ===
    suspend fun setGuideDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[KEY_GUIDE_DISMISSED] = dismissed }
    }

    suspend fun isGuideDismissed(): Boolean {
        return context.dataStore.data.first()[KEY_GUIDE_DISMISSED] ?: false
    }

    fun observeGuideDismissed(): Flow<Boolean> {
        return context.dataStore.data.map { it[KEY_GUIDE_DISMISSED] ?: false }
    }

    // === Onboarding ===
    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[KEY_ONBOARDING_SEEN] = true }
    }

    suspend fun hasSeenOnboarding(): Boolean {
        return context.dataStore.data.first()[KEY_ONBOARDING_SEEN] ?: false
    }
}
