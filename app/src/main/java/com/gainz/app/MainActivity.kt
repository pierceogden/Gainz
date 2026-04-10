package com.gainz.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                intent.data?.let { arrayOf(it) }
                    ?: intent.clipData?.let { clip ->
                        Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    }
            }
        } else {
            null
        }
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge dark status bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = 0xFF0A0A0F.toInt()
        window.navigationBarColor = 0xFF0D0D14.toInt()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.databaseEnabled = true

            // Inject the storage bridge as "Android" JS interface
            addJavascriptInterface(StorageBridge(this@MainActivity), "Android")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // Block any external navigation
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("blob:") || url.startsWith("file:")) return false
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    filePickerLauncher.launch(intent)
                    return true
                }
            }

            // Handle CSV downloads: intercept blob URL downloads
            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                if (url.startsWith("blob:")) {
                    // Extract filename from content disposition or use default
                    val filename = contentDisposition
                        ?.substringAfter("filename=", "")
                        ?.trim('"', '\'', ' ')
                        ?.takeIf { it.isNotEmpty() }
                        ?: "recomp_export.csv"

                    // Read blob content via JS and pass to Android for saving
                    evaluateJavascript(
                        """
                        (function() {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', '$url', true);
                            xhr.responseType = 'text';
                            xhr.onload = function() {
                                Android.saveFile('$filename', xhr.responseText);
                            };
                            xhr.send();
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/recomp_tracker.html")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Called from JavaScript via StorageBridge to save CSV files to Downloads.
     */
    fun saveFileToDownloads(filename: String, content: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        out.write(content.toByteArray())
                    }
                    Toast.makeText(this, "Saved to Downloads/$filename", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Pre-Android 10
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(dir, filename)
                file.writeText(content)
                Toast.makeText(this, "Saved to Downloads/$filename", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}
