package info.christianreiner.fritzdial.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import info.christianreiner.fritzdial.tr064.RecentCall

/**
 * Dialled, received and missed calls from the phone's own call log (calls through the mobile network or other apps;
 * FritzDial's calls run on the FRITZ!Box and never show up there).
 *
 * Needs READ_CALL_LOG. Google Play only allows that for the default phone app, so this works only
 * when FritzDial is installed from elsewhere (APK, F-Droid).
 */
object DeviceCallLog {

    /** How many calls to read at most. */
    const val MAX_ENTRIES = 100

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Reads the most recent dialled, received and missed calls (not rejected or blocked ones), newest first.
     * Call off the main thread.
     * Throws (typically a [SecurityException]) when Android does not allow access.
     */
    fun load(context: Context): List<RecentCall> {
        if (!hasPermission(context)) throw SecurityException("No permission to read the call log")
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.NUMBER_PRESENTATION,
        )
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.TYPE} IN (?, ?, ?)",
            arrayOf(CallLog.Calls.OUTGOING_TYPE, CallLog.Calls.INCOMING_TYPE, CallLog.Calls.MISSED_TYPE)
                .map(Int::toString).toTypedArray(),
            "${CallLog.Calls.DATE} DESC",
        ) ?: throw IllegalStateException("The call log is not available on this phone")
        val result = mutableListOf<RecentCall>()
        cursor.use { c ->
            val numberCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameCol = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val dateCol = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val typeCol = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val presentationCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER_PRESENTATION)
            // No LIMIT in the query: not every call log provider accepts it in the sort order.
            while (result.size < MAX_ENTRIES && c.moveToNext()) {
                val number = c.getString(numberCol)?.trim().orEmpty()
                // Withheld / unknown callers: nothing to call back.
                if (number.isEmpty() || c.getInt(presentationCol) != CallLog.Calls.PRESENTATION_ALLOWED) continue
                val name = c.getString(nameCol)?.trim()?.takeIf { it.isNotEmpty() }
                val direction = when (c.getInt(typeCol)) {
                    CallLog.Calls.INCOMING_TYPE -> RecentCall.Direction.INCOMING
                    CallLog.Calls.MISSED_TYPE -> RecentCall.Direction.MISSED
                    else -> RecentCall.Direction.OUTGOING
                }
                result += RecentCall(number, name, c.getLong(dateCol), RecentCall.Source.PHONE, direction)
            }
        }
        return result
    }
}
