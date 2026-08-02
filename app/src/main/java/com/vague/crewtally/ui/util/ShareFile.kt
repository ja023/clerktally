package com.vague.crewtally.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.vague.crewtally.BuildConfig
import java.io.File

/**
 * Builds and launches the system share sheet for [file] via the app's FileProvider — the one
 * seam every Phase 5 share action (clerk statement, company report, backup export) goes
 * through, so the provider authority and grant flags live in exactly one place. CrewTally is
 * offline-only (no INTERNET permission); this still works because [ACTION_SEND] hands the file
 * off to whatever app the user picks in the chooser — CrewTally itself never makes a network call.
 */
fun Context.shareFile(file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, null))
}
