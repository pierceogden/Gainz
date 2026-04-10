# Keep the JavaScript bridge methods accessible from WebView
-keepclassmembers class com.gainz.app.StorageBridge {
    @android.webkit.JavascriptInterface <methods>;
}
