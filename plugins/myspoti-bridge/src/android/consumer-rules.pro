# ProGuard rules for MyspotiBridge plugin
-keep class com.cpotify.app.myspoti.** { *; }
-keep class com.getcapacitor.** { *; }
-keep class org.json.** { *; }

# Keep WebView JavaScript interfaces
-keepclassmembers class com.cpotify.app.myspoti.WebPlayerHost$BridgeInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebViewClient and WebChromeClient
-keep class com.cpotify.app.myspoti.WebPlayerHost$AdBlockingWebViewClient { *; }
-keep class com.cpotify.app.myspoti.WebPlayerHost$1 { *; }
-keep class com.cpotify.app.myspoti.WebPlayerHost$2 { *; }