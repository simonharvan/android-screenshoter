package tech.bero.screenshoter

import android.content.Context
import android.preference.PreferenceManager


object PrefUtils {
    val DEVICE_NAME: String = "DEVICE_NAME"
    val ACCESS_KEY: String = "ACCESS_KEY"
    val SECRET_KEY: String = "SECRET_KEY"
    val BUCKET_NAME: String = "BUCKET_NAME"

    /**
     * Called to save supplied value in shared preferences against given key.
     * @param context Context of caller activity
     * @param key Key of value to save against
     * @param value Value to save
     */
    fun saveToPrefs(context: Context?, key: String?, value: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun saveLongToPrefs(context: Context?, key: String?, value: Long) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    /**
     * Called to retrieve required value from shared preferences, identified by given key.
     * Default value will be returned of no value found or error occurred.
     * @param context Context of caller activity
     * @param key Key to find value against
     * @param defaultValue Value to return if no data found against given key
     * @return Return the value found against given key, default if not found or any error occurs
     */
    fun getFromPrefs(context: Context?, key: String?, defaultValue: String): String {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val smth = sharedPrefs.getString(key, defaultValue)
            return smth ?: defaultValue
        } catch (e: Exception) {
            e.printStackTrace()
            return defaultValue
        }
    }

    fun getLongFromPrefs(context: Context?, key: String?, defaultValue: Long): Long {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            sharedPrefs.getLong(key, defaultValue)
        } catch (e: Exception) {
            e.printStackTrace()
            defaultValue
        }
    }

    /**
     *
     * @param context Context of caller activity
     * @param key Key to delete from SharedPreferences
     */
    fun removeFromPrefs(context: Context?, key: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.remove(key)
        editor.apply()
    }


}