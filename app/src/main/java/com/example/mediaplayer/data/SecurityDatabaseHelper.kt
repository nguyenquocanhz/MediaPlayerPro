package com.example.mediaplayer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.SecureRandom
import android.util.Base64

class SecurityDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "security_settings.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SETTINGS = "settings"
        private const val COLUMN_KEY = "setting_key"
        private const val COLUMN_VAL = "setting_val"

        private const val KEY_PIN = "app_pin"
        private const val KEY_BIOMETRIC = "biometric_enabled"

        private const val ITERATIONS = 12000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH_BYTES = 16
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_SETTINGS (
                $COLUMN_KEY TEXT PRIMARY KEY,
                $COLUMN_VAL TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SETTINGS")
        onCreate(db)
    }

    // Helper to hash password using SHA-256 (retained for backward-compatible migration only)
    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun hashPassword(password: String): String {
        val salt = generateSalt()
        val hash = pbkdf2(password, salt)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "$saltBase64:$hashBase64"
    }

    // Check if a password is set
    fun isPasswordSet(): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf(COLUMN_VAL),
            "$COLUMN_KEY = ?",
            arrayOf(KEY_PIN),
            null, null, null
        )
        val hasPin = cursor.use {
            if (it.moveToFirst()) {
                val pin = it.getString(0)
                !pin.isNullOrEmpty()
            } else {
                false
            }
        }
        return hasPin
    }

    // Save password with salted PBKDF2 hash
    fun setPassword(password: String): Boolean {
        val db = this.writableDatabase
        val hashedWithSalt = hashPassword(password)
        val values = ContentValues().apply {
            put(COLUMN_KEY, KEY_PIN)
            put(COLUMN_VAL, hashedWithSalt)
        }
        val rows = db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return rows != -1L
    }

    // Verify password with salted PBKDF2 and auto-upgrades legacy SHA-256 passwords
    fun verifyPassword(password: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf(COLUMN_VAL),
            "$COLUMN_KEY = ?",
            arrayOf(KEY_PIN),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                val stored = it.getString(0) ?: return@use false
                if (stored.contains(":")) {
                    val parts = stored.split(":")
                    if (parts.size == 2) {
                        try {
                            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                            val storedHash = Base64.decode(parts[1], Base64.NO_WRAP)
                            val computedHash = pbkdf2(password, salt)
                            return@use computedHash.contentEquals(storedHash)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            return@use false
                        }
                    }
                }
                // Upgrade legacy SHA-256 passwords
                if (stored.length == 64) {
                    val oldHashed = sha256(password)
                    if (oldHashed == stored) {
                        setPassword(password) // Seamlessly upgrade to PBKDF2
                        return@use true
                    }
                }
                false
            } else {
                false
            }
        }
    }

    // Clear password (removes lock)
    fun clearPassword() {
        val db = this.writableDatabase
        db.delete(TABLE_SETTINGS, "$COLUMN_KEY = ?", arrayOf(KEY_PIN))
        // Also disable biometric if password is cleared
        setBiometricEnabled(false)
    }

    // Check if biometric is enabled
    fun isBiometricEnabled(): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf(COLUMN_VAL),
            "$COLUMN_KEY = ?",
            arrayOf(KEY_BIOMETRIC),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(0) == "true"
            } else {
                false
            }
        }
    }

    // Set biometric state
    fun setBiometricEnabled(enabled: Boolean) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_KEY, KEY_BIOMETRIC)
            put(COLUMN_VAL, if (enabled) "true" else "false")
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
