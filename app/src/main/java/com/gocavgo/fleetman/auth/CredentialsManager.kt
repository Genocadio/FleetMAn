package com.gocavgo.fleetman.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.core.content.edit

class CredentialsManager(context: Context) {
    private val securePrefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        securePrefs = EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveUserData(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        username: String,
        companyName: String,
        companyId: Long
    ) {
        securePrefs.edit {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
            putLong("user_id", userId)
            putString("username", username)
            putString("company_name", companyName)
            putBoolean("is_logged_in", true)
            putLong("company_id", companyId)
        }
    }

    fun getLoginStatus(): Boolean = securePrefs.getBoolean("is_logged_in", false)
    fun getUserName(): String = securePrefs.getString("username", "") ?: ""
    fun getCompanyName(): String = securePrefs.getString("company_name", "") ?: ""
    fun getUserId(): Long = securePrefs.getLong("user_id", 0L)
    fun getCompanyId(): Long = securePrefs.getLong("company_id", 0L)
    fun getAccessToken(): String = securePrefs.getString("access_token", "") ?: ""
    fun getRefreshToken(): String = securePrefs.getString("refresh_token", "") ?: ""

    fun clearUserData() {
        securePrefs.edit { clear() }
    }
}