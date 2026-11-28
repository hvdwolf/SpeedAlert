package xyz.hvdw.speedalert

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class GpxFilesActivity : AppCompatActivity() {

    private lateinit var selectAllCheckBox: CheckBox
    private lateinit var fileList: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button

    private val files = mutableListOf<File>()
    private val selectedFiles = mutableSetOf<File>()
    private val fileCheckBoxes = mutableMapOf<File, CheckBox>()
    private var updatingSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpx_files)

        selectAllCheckBox = findViewById(R.id.checkSelectAllGpx)
        fileList = findViewById(R.id.gpxFileList)
        emptyMessage = findViewById(R.id.txtNoGpxFiles)
        shareButton = findViewById(R.id.btnShareSelectedGpx)
        deleteButton = findViewById(R.id.btnDeleteSelectedGpx)

        selectAllCheckBox.setOnCheckedChangeListener { _, checked ->
            if (!updatingSelection) {
                selectedFiles.clear()
                if (checked) selectedFiles.addAll(files)
                updateSelectionUi()
            }
        }
        shareButton.setOnClickListener { shareSelectedFiles() }
        deleteButton.setOnClickListener { confirmDeleteSelectedFiles() }

        loadGpxFiles()
    }

    private fun loadGpxFiles() {
        val tracksDirectory = GpsTrackRecorder.tracksDirectory(this)
        val directoryFiles = tracksDirectory.listFiles { file ->
            file.isFile && file.extension.equals("gpx", ignoreCase = true)
        }

        if (directoryFiles == null && tracksDirectory.exists()) {
            Log.e("GpxFiles", "Could not read tracks directory: ${tracksDirectory.absolutePath}")
            Toast.makeText(
                this,
                getString(R.string.no_access_to_gpx_folder),
                Toast.LENGTH_LONG
            ).show()
        }

        files.clear()
        files.addAll(directoryFiles.orEmpty().sortedBy { it.name })
        selectedFiles.clear()
        fileCheckBoxes.clear()
        fileList.removeAllViews()

        files.forEach { file ->
            val checkBox = CheckBox(this).apply {
                text = file.name
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedFiles.add(file) else selectedFiles.remove(file)
                    updateSelectionUi()
                }
            }
            fileCheckBoxes[file] = checkBox
            fileList.addView(
                checkBox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        emptyMessage.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        selectAllCheckBox.isEnabled = files.isNotEmpty()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        updatingSelection = true
        fileCheckBoxes.forEach { (file, checkBox) ->
            checkBox.isChecked = file in selectedFiles
        }
        selectAllCheckBox.isChecked =
            files.isNotEmpty() && selectedFiles.size == files.size
        updatingSelection = false

        val hasSelection = selectedFiles.isNotEmpty()
        shareButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
    }

    private fun shareSelectedFiles() {
        val selected = selectedFiles.toList()
        if (selected.isEmpty()) return

        try {
            val uris = ArrayList(
                selected.map { file ->
                    FileProvider.getUriForFile(
                        this,
                        "xyz.hvdw.speedalert.fileprovider",
                        file
                    )
                }
            )
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/gpx+xml"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.gpx_share_selected)
                )
            )
        } catch (e: Exception) {
            Log.e("GpxFiles", "Failed to share selected GPX files", e)
            Toast.makeText(
                this,
                getString(R.string.gpx_sharing_impossible),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmDeleteSelectedFiles() {
        val selected = selectedFiles.toList()
        if (selected.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gpx_dialog_title))
            .setMessage(getString(R.string.gpx_delete_confirmation, selected.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.gpx_dialog_btn)) { _, _ ->
                deleteSelectedFiles(selected)
            }
            .show()
    }

    private fun deleteSelectedFiles(selected: List<File>) {
        var deleted = 0
        var failed = 0

        selected.forEach { file ->
            if (file.delete()) {
                deleted++
            } else {
                failed++
                Log.e("GpxFiles", "Could not delete GPX file: ${file.absolutePath}")
            }
        }

        val message = if (failed == 0) {
            getString(R.string.gpx_files_deleted, deleted)
        } else {
            getString(R.string.gpx_delete_result, deleted, failed)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        loadGpxFiles()
    }
}
