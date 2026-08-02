package com.vague.crewtally.testutil

import com.vague.crewtally.report.ReportFileWriter
import java.io.File
import java.io.IOException

/**
 * Fake [ReportFileWriter] for headless ViewModel tests. [shouldFail] simulates the one failure
 * shape a real file write can hit (disk full, PDF drawing error) so `ClerkStatementShareViewModel`
 * / `CompanyReportShareViewModel`'s `generate()` failure path (isGenerating reset,
 * generationFailure surfaced) is exercised without touching a real file system.
 */
class FakeReportFileWriter(private val shouldFail: Boolean = false) : ReportFileWriter {

    override suspend fun writeText(fileName: String, content: String): File {
        if (shouldFail) throw IOException("simulated write failure")
        return File(fileName)
    }

    override suspend fun writePdf(fileName: String, lines: List<String>, pageLabelTemplate: String): File {
        if (shouldFail) throw IOException("simulated write failure")
        return File(fileName)
    }
}
