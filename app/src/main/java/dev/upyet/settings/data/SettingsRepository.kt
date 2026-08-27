package dev.upyet.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.evidence.domain.StatsWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val retention: RetentionPolicy,
    val defaultSnoozeMinutes: Int,
    val defaultVibrationEnabled: Boolean,
    val evidenceEnabledByDefault: Boolean,
    val maxSnoozes: Int,
    val upcomingAlarmLeadMinutes: Int = 60,
    val shareExplainerShown: Boolean = false,
    val statsWindow: StatsWindow = StatsWindow.SEVEN_DAYS,
)

@Singleton
class SettingsRepository
@Inject
constructor(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<AppSettings> =
        dataStore.data.map { preferences ->
            AppSettings(
                retention =
                preferences[Keys.retention]?.let { value ->
                    RetentionPolicy.entries.firstOrNull { it.name == value }
                } ?: RetentionPolicy.SEVEN_DAYS,
                defaultSnoozeMinutes = preferences[Keys.snoozeMinutes] ?: 9,
                defaultVibrationEnabled = preferences[Keys.vibrationEnabled] ?: true,
                evidenceEnabledByDefault = preferences[Keys.evidenceEnabled] ?: true,
                maxSnoozes = preferences[Keys.maxSnoozes] ?: SnoozeBudget.DEFAULT_MAX,
                upcomingAlarmLeadMinutes = preferences[Keys.upcomingLeadMinutes] ?: 60,
                shareExplainerShown = preferences[Keys.shareExplainerShown] ?: false,
                statsWindow =
                preferences[Keys.statsWindow]?.let { value ->
                    StatsWindow.entries.firstOrNull { it.name == value }
                } ?: StatsWindow.SEVEN_DAYS,
            )
        }

    suspend fun setRetention(retention: RetentionPolicy) {
        dataStore.edit { it[Keys.retention] = retention.name }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        dataStore.edit { it[Keys.snoozeMinutes] = minutes }
    }

    suspend fun setDefaultVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.vibrationEnabled] = enabled }
    }

    suspend fun setEvidenceEnabledByDefault(enabled: Boolean) {
        dataStore.edit { it[Keys.evidenceEnabled] = enabled }
    }

    suspend fun setMaxSnoozes(value: Int) {
        dataStore.edit { it[Keys.maxSnoozes] = value }
    }

    suspend fun setUpcomingAlarmLeadMinutes(value: Int) {
        dataStore.edit { it[Keys.upcomingLeadMinutes] = value }
    }

    suspend fun setShareExplainerShown() {
        dataStore.edit { it[Keys.shareExplainerShown] = true }
    }

    suspend fun setStatsWindow(window: StatsWindow) {
        dataStore.edit { it[Keys.statsWindow] = window.name }
    }

    private object Keys {
        val retention = stringPreferencesKey("retention")
        val snoozeMinutes = intPreferencesKey("default_snooze_minutes")
        val vibrationEnabled = booleanPreferencesKey("default_vibration_enabled")
        val evidenceEnabled = booleanPreferencesKey("evidence_enabled_by_default")
        val maxSnoozes = intPreferencesKey("max_snoozes")
        val upcomingLeadMinutes = intPreferencesKey("upcoming_alarm_lead_minutes")
        val shareExplainerShown = booleanPreferencesKey("share_explainer_shown")
        val statsWindow = stringPreferencesKey("stats_window")
    }
}
