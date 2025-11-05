package com.gocavgo.fleetman.settings

/**
 * Global password manager that persists password across activities until app is closed
 * Stores passwords per vehicle ID
 */
object PasswordManager {
    private val passwords = mutableMapOf<Long, String>()

    fun setPassword(vehicleId: Long, pwd: String?) {
        if (pwd != null) {
            passwords[vehicleId] = pwd
        } else {
            passwords.remove(vehicleId)
        }
    }

    fun getPassword(vehicleId: Long): String? = passwords[vehicleId]

    fun clearPassword(vehicleId: Long) {
        passwords.remove(vehicleId)
    }

    fun clearAllPasswords() {
        passwords.clear()
    }
}


