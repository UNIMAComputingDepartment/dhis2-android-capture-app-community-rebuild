package org.dhis2.community.tasking.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import timber.log.Timber

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Timber.w("NotificationActionReceiver: Context or Intent is null")
            return
        }

        Timber.d("NotificationActionReceiver: onReceive() called with action: ${intent.action}")

        // Extract the TEI, Program, and Enrollment UIDs from intent extras
        val sourceTeiUid = intent.getStringExtra("SOURCE_TEI_UID")
        val sourceProgramUid = intent.getStringExtra("SOURCE_PROGRAM_UID")
        val sourceEnrollmentUid = intent.getStringExtra("SOURCE_ENROLLMENT_UID")

        Timber.d(
            "NotificationActionReceiver: TEI: %s, Program: %s, Enrollment: %s",
            sourceTeiUid, sourceProgramUid, sourceEnrollmentUid
        )

        if (sourceTeiUid.isNullOrBlank() || sourceProgramUid.isNullOrBlank() || sourceEnrollmentUid.isNullOrBlank()) {
            Timber.w("NotificationActionReceiver: Missing required UIDs in intent extras")
            return
        }

        // Create implicit intent to open TEI dashboard using deep link
        // The app's main activity should be configured to handle this deep link
        val deepLinkUri = "app://tei/$sourceTeiUid/$sourceProgramUid/$sourceEnrollmentUid".toUri()
        val teiIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            `package` = context.packageName  // Ensure intent stays within the app
        }

        try {
            context.startActivity(teiIntent)
            Timber.d("NotificationActionReceiver: Successfully started TEI dashboard activity")
        } catch (e: Exception) {
            Timber.e(e, "NotificationActionReceiver: Error starting TEI dashboard activity")
        }
    }
}
