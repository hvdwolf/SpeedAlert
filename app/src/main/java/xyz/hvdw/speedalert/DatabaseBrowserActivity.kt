package xyz.hvdw.speedalert

import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.channels.SeekableByteChannel
import org.apache.commons.compress.archivers.sevenz.SevenZFile

class DatabaseBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_database_browser)

        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        webView = findViewById(R.id.webView)

        setupWebView()

        webView.loadUrl("https://github.com/hvdwolf/SpeedAlert_Databases/tags")
    }

    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true

        // Disable WebView’s internal download handler (prevents ugly toast)
        webView.setDownloadListener(null)

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            // Modern override
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                return handleUrl(url)
            }

            // Legacy override (older head units)
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleUrl(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // GitHub sometimes loads assets via JS
                if (url != null && url.contains(".7z", ignoreCase = true)) {
                    handleUrl(url)
                }
            }
        }
    }

    private fun handleUrl(url: String): Boolean {
        // Extract filename safely
        val fileName = url.substringAfterLast("/").substringBefore("?")

        if (fileName.endsWith(".7z", ignoreCase = true)) {
            // STOP WebView BEFORE it navigates → prevents ugly toast
            downloadAndInstall(url)
            return true
        }

        return false
    }

    private fun downloadAndInstall(url: String) {
        ToastUtils.show(this, prefs, getString(R.string.download_databases))

        Thread {
            try {
                // Download into cache
                val temp7z = File(cacheDir, "downloaded.7z")
                URL(url).openStream().use { input ->
                    temp7z.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Extract
                val extracted = extract7zToTemp(temp7z)
                if (extracted == null) {
                    runOnUiThread {
                        ToastUtils.show(this, prefs, getString(R.string.extraction_failed))
                    }
                    return@Thread
                }

                // Install
                val mediaDir = getExternalMediaDirs().firstOrNull()
                if (mediaDir == null) {
                    runOnUiThread {
                        ToastUtils.show(this, prefs, getString(R.string.media_access_failed))
                    }
                    return@Thread
                }

                val target = File(mediaDir, extracted.name)
                extracted.copyTo(target, overwrite = true)

                extracted.delete()
                temp7z.delete()

                runOnUiThread {
                    ToastUtils.show(
                        this,
                        prefs,
                        getString(R.string.database_installed, target.name)
                    )
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    ToastUtils.show(this, prefs, "Download failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun extract7zToTemp(archive: File): File? {
        val sevenZ = SevenZFile.Builder()
            .setFile(archive)
            .get()

        var entry = sevenZ.nextEntry

        while (entry != null) {
            if (!entry.isDirectory) {
                val outFile = File(cacheDir, entry.name)

                outFile.outputStream().use { out ->
                    val buffer = ByteArray(8192)

                    while (true) {
                        val read = sevenZ.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }

                sevenZ.close()
                return outFile
            }

            entry = sevenZ.nextEntry
        }

        sevenZ.close()
        return null
    }

}
