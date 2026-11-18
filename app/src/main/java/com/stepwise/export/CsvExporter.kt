package com.stepwise.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object CsvExporter {

    private const val CHOOSER_TITLE = "Export recorded data"

    /**
     * Writes [csv] to cacheDir/exports/[fileName] and returns a chooser Intent that shares it
     * via a FileProvider content Uri. The caller is responsible for launching the Intent.
     */
    fun export(context: Context, csv: String, fileName: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, CHOOSER_TITLE)
    }
}
