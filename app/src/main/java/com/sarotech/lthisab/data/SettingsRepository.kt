package com.sarotech.lthisab.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sarotech.lthisab.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lt_hisab_settings")

enum class DateFormatOption(val pattern: String, val label: String) {
    DMY_TEXT("dd MMM yyyy", "24 Nov 2026"),
    DMY_SLASH("dd/MM/yyyy", "24/11/2026"),
    YMD_DASH("yyyy-MM-dd", "2026-11-24");

    companion object {
        fun fromPattern(pattern: String?): DateFormatOption =
            entries.firstOrNull { it.pattern == pattern } ?: DMY_TEXT
    }
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val darkTheme: Boolean = false,
    val userName: String = "",
    val userAddress: String = "",
    val openingBalancePaise: Long = 0L,
    val currencySymbol: String = "₹",
    val indianNumberFormat: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.DMY_TEXT,
    val use24HourTime: Boolean = false,
    val firstDayOfWeek: Int = Calendar.MONDAY,
    val defaultPeriod: PeriodFilter = PeriodFilter.ALL,
    val showRunningBalance: Boolean = true,
    val showTimeOnRows: Boolean = true,
    val notesSuggestionsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val securityQuestion: String = "",
    val securityAnswerHash: String = "",
    val biometricEnabled: Boolean = false
) {
    fun timePattern(): String = if (use24HourTime) "HH:mm" else "hh:mm a"
    fun dateTimePattern(): String = "${dateFormat.pattern}, ${timePattern()}"
}

object PinCrypto {
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashSecret(secret: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:${secret.trim().lowercase()}".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ADDRESS = stringPreferencesKey("user_address")
        val OPENING_BALANCE = longPreferencesKey("opening_balance_paise")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val INDIAN_FORMAT = booleanPreferencesKey("indian_number_format")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val USE_24H = booleanPreferencesKey("use_24h_time")
        val FIRST_DAY = intPreferencesKey("first_day_of_week")
        val DEFAULT_PERIOD = stringPreferencesKey("default_period")
        val SHOW_RUNNING_BALANCE = booleanPreferencesKey("show_running_balance")
        val SHOW_TIME = booleanPreferencesKey("show_time_on_rows")
        val NOTES_SUGGESTIONS = booleanPreferencesKey("notes_suggestions")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val SECURITY_QUESTION = stringPreferencesKey("security_question")
        val SECURITY_ANSWER_HASH = stringPreferencesKey("security_answer_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            language = AppLanguage.fromCode(p[Keys.LANGUAGE]),
            darkTheme = p[Keys.DARK_THEME] ?: false,
            userName = p[Keys.USER_NAME] ?: "",
            userAddress = p[Keys.USER_ADDRESS] ?: "",
            openingBalancePaise = p[Keys.OPENING_BALANCE] ?: 0L,
            currencySymbol = p[Keys.CURRENCY_SYMBOL] ?: "₹",
            indianNumberFormat = p[Keys.INDIAN_FORMAT] ?: true,
            dateFormat = DateFormatOption.fromPattern(p[Keys.DATE_FORMAT]),
            use24HourTime = p[Keys.USE_24H] ?: false,
            firstDayOfWeek = p[Keys.FIRST_DAY] ?: Calendar.MONDAY,
            defaultPeriod = PeriodFilter.fromKey(p[Keys.DEFAULT_PERIOD]),
            showRunningBalance = p[Keys.SHOW_RUNNING_BALANCE] ?: true,
            showTimeOnRows = p[Keys.SHOW_TIME] ?: true,
            notesSuggestionsEnabled = p[Keys.NOTES_SUGGESTIONS] ?: true,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            pinEnabled = p[Keys.PIN_ENABLED] ?: false,
            pinHash = p[Keys.PIN_HASH] ?: "",
            pinSalt = p[Keys.PIN_SALT] ?: "",
            securityQuestion = p[Keys.SECURITY_QUESTION] ?: "",
            securityAnswerHash = p[Keys.SECURITY_ANSWER_HASH] ?: "",
            biometricEnabled = p[Keys.BIOMETRIC_ENABLED] ?: false
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setUserProfile(name: String, address: String) {
        context.dataStore.edit {
            it[Keys.USER_NAME] = name.trim()
            it[Keys.USER_ADDRESS] = address.trim()
        }
    }

    suspend fun setOpeningBalance(paise: Long) {
        context.dataStore.edit { it[Keys.OPENING_BALANCE] = paise }
    }

    suspend fun setCurrencySymbol(symbol: String) {
        context.dataStore.edit { it[Keys.CURRENCY_SYMBOL] = symbol.ifBlank { "₹" } }
    }

    suspend fun setIndianNumberFormat(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INDIAN_FORMAT] = enabled }
    }

    suspend fun setDateFormat(option: DateFormatOption) {
        context.dataStore.edit { it[Keys.DATE_FORMAT] = option.pattern }
    }

    suspend fun setUse24HourTime(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_24H] = enabled }
    }

    suspend fun setFirstDayOfWeek(day: Int) {
        context.dataStore.edit { it[Keys.FIRST_DAY] = day }
    }

    suspend fun setDefaultPeriod(period: PeriodFilter) {
        context.dataStore.edit { it[Keys.DEFAULT_PERIOD] = period.key }
    }

    suspend fun setShowRunningBalance(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_RUNNING_BALANCE] = enabled }
    }

    suspend fun setShowTimeOnRows(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TIME] = enabled }
    }

    suspend fun setNotesSuggestionsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTES_SUGGESTIONS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setPin(pin: String, question: String, answer: String) {
        val salt = PinCrypto.generateSalt()
        val pinHash = PinCrypto.hashSecret(pin, salt)
        val answerHash = if (answer.isNotBlank()) PinCrypto.hashSecret(answer, salt) else ""
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = true
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = pinHash
            it[Keys.SECURITY_QUESTION] = question.trim()
            it[Keys.SECURITY_ANSWER_HASH] = answerHash
        }
    }

    suspend fun disablePin() {
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = false
            it[Keys.PIN_HASH] = ""
            it[Keys.PIN_SALT] = ""
            it[Keys.SECURITY_QUESTION] = ""
            it[Keys.SECURITY_ANSWER_HASH] = ""
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }
}
