package xyz.hvdw.speedalert

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SpeedAlertApp : Application() {

    fun copyLogfile(context: Context) {
        try {
            val src = File(context.filesDir, "speedalert.log")
            if (!src.exists()) {
                Toast.makeText(context, context.getString(R.string.logfile_not_exist), Toast.LENGTH_SHORT).show()
                return
            }

            val destDir = File("/sdcard/SpeedAlert")
            if (!destDir.exists()) destDir.mkdirs()

            val dest = File(destDir, "speedalert.log")

            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(context, context.getString(R.string.logfile_copied), Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.logfile_copy_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    fun shareLogfile(context: Context) {
        try {
            val file = File(context.filesDir, "speedalert.log")
            if (!file.exists()) {
                Toast.makeText(context, context.getString(R.string.logfile_not_exist), Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_logfile_title)))

        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.logfile_share_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    fun emptyLogfile(context: Context) {
        try {
            val file = File(context.filesDir, "speedalert.log")
            if (file.exists()) {
                file.writeText("")
                Toast.makeText(context, context.getString(R.string.logfile_emptied), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.logfile_not_exist), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.logfile_empty_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
}
