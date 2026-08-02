package com.vague.crewtally.report

import java.io.File

/**
 * The write seam between a fully-built report (or backup JSON string) and the file the system
 * share sheet actually sends. An interface — mirroring the writer-singleton pattern
 * ([com.vague.crewtally.data.local.PaymentWriter] et al.) — so ViewModel tests substitute an
 * in-memory fake instead of touching a real device cache directory or `PdfDocument`.
 */
interface ReportFileWriter {

    /** Writes [content] verbatim to [fileName] under the app's shared-files area. Used for both text reports and backup JSON. */
    suspend fun writeText(fileName: String, content: String): File

    /**
     * Draws [lines] as a simple, paginated PDF (A4, [lines]\[0\] as a bold header, the rest as
     * body text) and writes it to [fileName] under the shared-files area. [pageLabelTemplate] is
     * the raw `%1$d`/`%2$d` footer template (e.g. "Page %1$d of %2$d"), resolved from resources
     * by the caller since this layer has no [android.content.Context] of its own.
     */
    suspend fun writePdf(fileName: String, lines: List<String>, pageLabelTemplate: String): File
}
