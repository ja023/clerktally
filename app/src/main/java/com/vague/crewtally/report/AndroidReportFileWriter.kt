package com.vague.crewtally.report

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production [ReportFileWriter]. Every file lands under `cacheDir/shared/` — never external
 * storage, matching the offline/no-extra-permissions constraint — where the manifest's
 * FileProvider `<cache-path>` grants the system share sheet temporary read access. Files are
 * cleaned opportunistically (LOCKED environment note) by [pruneStaleFiles], called once per
 * writer construction rather than on every write, since a share flow may write more than one
 * file in quick succession (e.g. text then PDF for the same report) and pruning mid-flow could
 * delete a sibling file still pending a share.
 */
class AndroidReportFileWriter(private val context: Context) : ReportFileWriter {

    init {
        pruneStaleFiles()
    }

    override suspend fun writeText(fileName: String, content: String): File = withContext(Dispatchers.IO) {
        val file = File(sharedDir(), fileName)
        file.writeText(content)
        file
    }

    override suspend fun writePdf(fileName: String, lines: List<String>, pageLabelTemplate: String): File =
        withContext(Dispatchers.IO) {
            val file = File(sharedDir(), fileName)
            PdfReportRenderer.render(lines, pageLabelTemplate, file)
            file
        }

    private fun sharedDir(): File = File(context.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }

    /** Best-effort cleanup of files older than [STALE_FILE_AGE_MILLIS] — never blocks a write on failure. */
    private fun pruneStaleFiles() {
        val dir = sharedDir()
        val cutoff = System.currentTimeMillis() - STALE_FILE_AGE_MILLIS
        runCatching {
            dir.listFiles()?.forEach { file -> if (file.lastModified() < cutoff) file.delete() }
        }
    }

    private companion object {
        const val SHARED_DIR_NAME = "shared"
        const val STALE_FILE_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
