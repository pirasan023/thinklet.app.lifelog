package ai.fd.thinklet.library.lifelog.data.file.impl

import ai.fd.thinklet.library.lifelog.data.file.FileSelectorRepository
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

internal class FileSelectorRepositoryImpl @Inject constructor(
    private val context: Context
) : FileSelectorRepository {
    override fun audioPath(): File? {
        return File(dir(), "${fileFormat()}.raw")
    }

    override fun gifPath(): File? {
        return File(dir(), "${fileFormat()}.gif")
    }

    override fun jpgPath(): File? {
        return File(dir(), "${fileFormat()}.jpg")
    }

    override fun deploy(file: File): Boolean {
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "deploy skipped. invalid file: ${file.absolutePath}")
            return false
        }
        return updateIndex(file)
    }

    private fun fileFormat(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun dir(): File {
        val dateFolder = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return File(rootDir(), dateFolder).apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Directory $absolutePath created: $created")
            }
        }
    }

    private fun rootDir(): File {
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        return File(baseDir, DIR).apply {
            if (!exists()) {
                val created = mkdirs()
                if (!created) {
                    Log.w(TAG, "Root directory $absolutePath create failed")
                }
            }
        }
    }

    private fun updateIndex(file: File): Boolean {
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "updateIndex skipped. invalid file: ${file.absolutePath}")
            return false
        }
        Log.d(TAG, "handleCompletedFile")
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            null,
            object : MediaScannerConnection.MediaScannerConnectionClient {
                override fun onScanCompleted(path: String, uri: Uri) {
                    Log.d(TAG, "onScanCompleted path:$path, uri:$uri")
                }

                override fun onMediaScannerConnected() {
                    Log.v(TAG, "onMediaScannerConnected")
                }
            })
        Log.d(TAG, "success handleCompletedFile $file")
        return true
    }
}
