package xyz.hvdw.speedalert

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient

class WebViewFetcher(private val context: Context) {

    fun fetch(url: String, callback: (String?) -> Unit) {
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            try {
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = false
                webView.settings.domStorageEnabled = false
                webView.settings.loadsImagesAutomatically = false

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            view?.evaluateJavascript(
                                "(function() { return document.body.innerText; })();"
                            ) { result ->
                                val cleaned = result
                                    ?.trim('"')
                                    ?.replace("\\n", "\n")
                                    ?.replace("\\\"", "\"")

                                callback(cleaned)
                                webView.destroy()
                            }
                        } catch (e: Exception) {
                            callback(null)
                            webView.destroy()
                        }
                    }
                }

                webView.loadUrl(url)

                // Timeout safeguard
                handler.postDelayed({
                    callback(null)
                    webView.destroy()
                }, 8000)

            } catch (e: Exception) {
                callback(null)
            }
        }
    }
}
