package com.gainz.app

import android.content.Context
import android.content.SharedPreferences
import android.webkit.JavascriptInterface

/**
 * JavaScript bridge that implements the window.storage API expected by the HTML app.
 * Persists data to Android SharedPreferences.
 */
class StorageBridge(private val activity: MainActivity) {

    private val prefs: SharedPreferences =
        activity.getSharedPreferences("gainz_storage", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun get(key: String): String? {
        return prefs.getString(key, null)
    }

    @JavascriptInterface
    fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    @JavascriptInterface
    fun saveFile(filename: String, content: String) {
        activity.runOnUiThread {
            activity.saveFileToDownloads(filename, content)
        }
    }
}
