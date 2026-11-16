package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.InspectionPolicyConfig
import com.example.domain.repository.SettingsRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.pcbSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bharat_pcb_settings"
)

data class StoredModelArtifacts(
    val detectionModelPath: String? = null,
    val advisoryModelPath: String? = null,
    val advisoryTokenizerPath: String? = null
)

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val themeMode: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE] ?: AppThemeMode.SYSTEM.name
        runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    override val policyConfig: Flow<InspectionPolicyConfig> = dataStore.data.map { prefs ->
        val defaults = InspectionPolicyConfig()
        InspectionPolicyConfig(
            profileName = prefs[KEY_POLICY_PROFILE] ?: defaults.profileName,
            policyVersion = prefs[KEY_POLICY_VERSION] ?: defaults.policyVersion,
            rejectOnCritical = prefs[KEY_REJECT_ON_CRITICAL] ?: defaults.rejectOnCritical,
            reviewOnMedium = prefs[KEY_REVIEW_ON_MEDIUM] ?: defaults.reviewOnMedium,
            reviewOnLow = prefs[KEY_REVIEW_ON_LOW] ?: defaults.reviewOnLow,
            reviewOnLowClarityZeroDefects = prefs[KEY_REVIEW_ON_LOW_CLARITY]
                ?: defaults.reviewOnLowClarityZeroDefects,
            minImageWidth = (prefs[KEY_MIN_IMAGE_WIDTH] ?: defaults.minImageWidth).coerceAtLeast(64),
            minImageHeight = (prefs[KEY_MIN_IMAGE_HEIGHT] ?: defaults.minImageHeight).coerceAtLeast(64)
        )
    }

    val storedModelArtifacts: Flow<StoredModelArtifacts> = dataStore.data.map { prefs ->
        StoredModelArtifacts(
            detectionModelPath = prefs[KEY_DETECTION_MODEL_PATH]?.takeIf { it.isNotBlank() },
            advisoryModelPath = prefs[KEY_ADVISORY_MODEL_PATH]?.takeIf { it.isNotBlank() },
            advisoryTokenizerPath = prefs[KEY_ADVISORY_TOKENIZER_PATH]?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun updatePolicyConfig(config: InspectionPolicyConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_POLICY_PROFILE] = config.profileName.ifBlank {
                InspectionPolicyConfig.DEFAULT_PROFILE_NAME
            }
            prefs[KEY_POLICY_VERSION] = config.policyVersion.ifBlank {
                InspectionPolicyConfig.DEFAULT_POLICY_VERSION
            }
            prefs[KEY_REJECT_ON_CRITICAL] = config.rejectOnCritical
            prefs[KEY_REVIEW_ON_MEDIUM] = config.reviewOnMedium
            prefs[KEY_REVIEW_ON_LOW] = config.reviewOnLow
            prefs[KEY_REVIEW_ON_LOW_CLARITY] = config.reviewOnLowClarityZeroDefects
            prefs[KEY_MIN_IMAGE_WIDTH] = config.minImageWidth.coerceAtLeast(64)
            prefs[KEY_MIN_IMAGE_HEIGHT] = config.minImageHeight.coerceAtLeast(64)
        }
    }

    override suspend fun resetPolicyDefaults() {
        updatePolicyConfig(InspectionPolicyConfig())
    }

    suspend fun setDetectionModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_DETECTION_MODEL_PATH)
            else prefs[KEY_DETECTION_MODEL_PATH] = path
        }
    }

    suspend fun setAdvisoryModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_ADVISORY_MODEL_PATH)
            else prefs[KEY_ADVISORY_MODEL_PATH] = path
        }
    }

    suspend fun setAdvisoryTokenizerPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_ADVISORY_TOKENIZER_PATH)
            else prefs[KEY_ADVISORY_TOKENIZER_PATH] = path
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_POLICY_PROFILE = stringPreferencesKey("policy_profile")
        private val KEY_POLICY_VERSION = stringPreferencesKey("policy_version")
        private val KEY_REJECT_ON_CRITICAL = booleanPreferencesKey("reject_on_critical")
        private val KEY_REVIEW_ON_MEDIUM = booleanPreferencesKey("review_on_medium")
        private val KEY_REVIEW_ON_LOW = booleanPreferencesKey("review_on_low")
        private val KEY_REVIEW_ON_LOW_CLARITY = booleanPreferencesKey("review_on_low_clarity")
        private val KEY_MIN_IMAGE_WIDTH = intPreferencesKey("min_image_width")
        private val KEY_MIN_IMAGE_HEIGHT = intPreferencesKey("min_image_height")
        private val KEY_DETECTION_MODEL_PATH = stringPreferencesKey("detection_model_path")
        private val KEY_ADVISORY_MODEL_PATH = stringPreferencesKey("advisory_model_path")
        private val KEY_ADVISORY_TOKENIZER_PATH = stringPreferencesKey("advisory_tokenizer_path")
    }
}
