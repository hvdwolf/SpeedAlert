package xyz.hvdw.speedalert

import android.location.Location
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes a GPX 1.1 track in the app-private files/tracks directory.
 * All public methods are synchronized because service actions and location
 * callbacks can arrive on different threads.
 */
class GpsTrackRecorder(private val filesDir: File) {

    companion object {
        private val FILE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

        private val GPX_TIME_FORMAT =
        DateTimeFormatter.ISO_INSTANT
    }

    @Synchronized
    fun getPointCount(): Int = pointCount

    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var pointCount = 0

    private var recordingIntervalMs: Long = 2000L
    private var lastRecordedTimeMs: Long = 0L

    @Synchronized
    fun setRecordingInterval(intervalMs: Long) {
        recordingIntervalMs = intervalMs.coerceAtLeast(1000L)
    }

    @Synchronized
    @Throws(IOException::class)
    fun start(): File {
        if (writer != null) return currentFile!!

        val directory = File(filesDir, "tracks")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create track directory: ${directory.absolutePath}")
        }

        val timestamp = FILE_TIME_FORMAT.format(Instant.now())
        var file = File(directory, "SpeedAlert_$timestamp.gpx")
        var suffix = 1
        while (file.exists()) {
            file = File(directory, "SpeedAlert_${timestamp}_$suffix.gpx")
            suffix++
        }

        val newWriter = BufferedWriter(FileWriter(file, false))
        try {
            newWriter.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            newWriter.newLine()
            newWriter.write(
                """<gpx version="1.1" creator="SpeedAlert" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:sa="https://hvdwolf.xyz/speedalert/gpx/1">"""
            )
            newWriter.newLine()
            newWriter.write("  <trk>")
            newWriter.newLine()
            newWriter.write("    <name>SpeedAlert $timestamp</name>")
            newWriter.newLine()
            newWriter.write("    <trkseg>")
            newWriter.newLine()
            newWriter.flush()
        } catch (e: Exception) {
            try { newWriter.close() } catch (_: Exception) {}
            try { file.delete() } catch (_: Exception) {}
            throw e
        }

        writer = newWriter
        currentFile = file
        pointCount = 0
        lastRecordedTimeMs = 0L
        return file
    }

    @Synchronized
    @Throws(IOException::class)
    fun addLocation(location: Location) {
        val out = writer ?: return

        val currentTimeMs = location.time.takeIf { it > 0L }
        ?: System.currentTimeMillis()

        // Alleen een GPS-punt opslaan als het interval verstreken is.
        if (lastRecordedTimeMs > 0L &&
            currentTimeMs - lastRecordedTimeMs < recordingIntervalMs
        ) {
            return
        }

        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
            if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return

        lastRecordedTimeMs = currentTimeMs

        out.write(
            String.format(
                Locale.US,
                "      <trkpt lat=\"%.7f\" lon=\"%.7f\">",
                location.latitude,
                location.longitude
            )
        )
        out.newLine()

        if (location.hasAltitude() && location.altitude.isFinite()) {
            out.write(String.format(Locale.US, "        <ele>%.2f</ele>", location.altitude))
            out.newLine()
        }

        out.write("        <time>${GPX_TIME_FORMAT.format(Instant.ofEpochMilli(currentTimeMs))}</time>")
        out.newLine()

        out.write("        <extensions>")
        out.newLine()
        if (location.hasSpeed() && location.speed.isFinite()) {
            val speedKmh = location.speed * 3.6f
            out.write(String.format(Locale.US, "          <sa:speedKmh>%.2f</sa:speedKmh>", speedKmh))
            out.newLine()
        }
        if (location.hasAccuracy() && location.accuracy.isFinite()) {
            out.write(String.format(Locale.US, "          <sa:accuracyMeters>%.2f</sa:accuracyMeters>", location.accuracy))
            out.newLine()
        }
        out.write("        </extensions>")
        out.newLine()
        out.write("      </trkpt>")
        out.newLine()

        pointCount++
        // Flush every point: a process kill loses at most the point currently being written.
        if (pointCount % 1 == 0) out.flush()
    }

    @Synchronized
    fun isRecording(): Boolean = writer != null

    @Synchronized
    fun currentFile(): File? = currentFile

    @Synchronized
    fun stop(): File? {
        val out = writer ?: run {
            android.util.Log.w("SpeedAlert", "GPX STOP: writer is already null")
            return currentFile
        }

        val file = currentFile

        android.util.Log.i(
            "SpeedAlert",
            "GPX STOP: closing ${file?.absolutePath}, points=$pointCount"
        )

        try {
            out.write("    </trkseg>")
            out.newLine()
            out.write("  </trk>")
            out.newLine()
            out.write("</gpx>")
            out.newLine()

            out.flush()

            android.util.Log.i("SpeedAlert", "GPX STOP: closing tags written and flushed")

        } catch (e: Exception) {
            android.util.Log.e("SpeedAlert", "GPX STOP: error writing closing tags", e)
            throw e
        } finally {
            try {
                out.close()
                android.util.Log.i("SpeedAlert", "GPX STOP: writer closed successfully")
            } catch (e: Exception) {
                android.util.Log.e("SpeedAlert", "GPX STOP: error closing writer", e)
                throw e
            } finally {
                writer = null
                currentFile = null
                pointCount = 0
                lastRecordedTimeMs = 0L
            }
        }

        return file
    }
}
