package dev.cue.rebuild

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Android grants and Cue's tool policies are separate; both are checked at execution time.
enum class AccessPolicy {
    Allow,
    Ask,
    Deny,
}

data class DeviceAction(
    val id: String,
    val integrationId: String,
    val title: String,
    val permission: String? = null,
    val writes: Boolean = false,
)

val deviceActions =
    listOf(
        DeviceAction(
            "read_calendar",
            "device-calendar",
            "Read calendar events",
            Manifest.permission.READ_CALENDAR,
        ),
        DeviceAction("create_event", "device-calendar", "Prepare a calendar event", writes = true),
        DeviceAction(
            "read_contacts",
            "device-contacts",
            "Read contacts",
            Manifest.permission.READ_CONTACTS,
        ),
        DeviceAction("create_contact", "device-contacts", "Prepare a new contact", writes = true),
        DeviceAction(
            "read_messages",
            "device-messages",
            "Read SMS messages",
            Manifest.permission.READ_SMS,
        ),
        DeviceAction("compose_message", "device-messages", "Prepare a message", writes = true),
        DeviceAction("open_dialer", "device-dialer", "Open the phone dialer", writes = true),
        DeviceAction("read_notifications", "device-notifications", "Read active notifications"),
        DeviceAction(
            "read_call_log",
            "device-call-log",
            "Read recent calls",
            Manifest.permission.READ_CALL_LOG,
        ),
        DeviceAction(
            "read_health",
            "device-health",
            "Read step records",
            "android.permission.health.READ_STEPS",
        ),
        DeviceAction(
            "read_location",
            "device-location",
            "Read approximate location",
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

class DeviceAccess(private val context: Context, storageName: String) {
    private val preferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    var revision by mutableIntStateOf(0)
        private set

    // Capability metadata only; device records are read solely by an approved tool request.
    fun capabilities(): JSONObject =
        JSONObject()
            .put("timeZone", java.util.TimeZone.getDefault().id)
            .put("reportedAt", System.currentTimeMillis())
            .put(
                "operations",
                JSONArray(
                    deviceActions.map { action ->
                        JSONObject()
                            .put("operation", action.id)
                            .put("connected", connected(action.integrationId))
                            .put("policy", policy(action).name)
                            .put("permissionGranted", permitted(action))
                    }
                ),
            )

    fun connected(id: String): Boolean {
        revision
        return preferences.getBoolean("enabled-$id", false)
    }

    fun connect(id: String, enabled: Boolean) {
        preferences
            .edit()
            .apply {
                putBoolean("enabled-$id", enabled)
                deviceActions
                    .filter { it.integrationId == id }
                    .forEach { remove("policy-${it.id}") }
                preferences.getString("last-result", null)?.let { remove("result-$it") }
            }
            .apply()
        revision++
    }

    fun policy(action: DeviceAction): AccessPolicy {
        revision
        return runCatching {
                AccessPolicy.valueOf(preferences.getString("policy-${action.id}", "Ask")!!)
            }
            .getOrDefault(AccessPolicy.Ask)
    }

    fun setPolicy(action: DeviceAction, policy: AccessPolicy) {
        preferences.edit().putString("policy-${action.id}", policy.name).apply()
        revision++
    }

    fun permitted(action: DeviceAction): Boolean =
        when {
            action.id == "read_notifications" ->
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            action.id == "read_health" && Build.VERSION.SDK_INT < 34 -> false
            action.permission != null ->
                ContextCompat.checkSelfPermission(context, action.permission) ==
                    PackageManager.PERMISSION_GRANTED
            else -> true
        }

    fun savedResult(requestId: String): String? = preferences.getString("result-$requestId", null)

    fun saveResult(requestId: String, result: String) {
        // Keep only the most recent result: enough to retry transport without repeating an action.
        val old = preferences.getString("last-result", null)
        preferences
            .edit()
            .apply {
                if (old != null) remove("result-$old")
                putString("last-result", requestId)
                putString("result-$requestId", result)
            }
            .commit()
    }

    fun clearResult(requestId: String) {
        preferences.edit().remove("result-$requestId").apply()
    }

    suspend fun execute(input: JSONObject, approved: Boolean = false): JSONObject {
        val action =
            deviceActions.firstOrNull { it.id == input.optString("operation") }
                ?: return failure("Unknown device action.")
        if (!connected(action.integrationId) || policy(action) == AccessPolicy.Deny)
            return failure("Device access is disabled for this action.")
        if (policy(action) == AccessPolicy.Ask && !approved)
            return failure("This action needs your approval.")
        if (!permitted(action))
            return failure("Android permission is missing. Open Integrations to grant access.")
        return try {
            if (action.writes) withContext(Dispatchers.Main) { launchDraft(action.id, input) }
            else if (action.id == "read_health" && Build.VERSION.SDK_INT >= 34) readHealth(input)
            else withContext(Dispatchers.IO) { read(action.id, input) }
        } catch (_: SecurityException) {
            failure("Android access was denied or revoked.")
        } catch (_: Exception) {
            failure("This device action is not available right now.")
        }
    }

    private fun read(operation: String, input: JSONObject): JSONObject {
        val query = input.optString("query").take(200)
        val limit = input.optInt("limit", 20).coerceIn(1, 50)
        val rows =
            when (operation) {
                "read_contacts" ->
                    query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf("display_name", "data1"),
                        "display_name LIKE ?",
                        arrayOf("%$query%"),
                        "display_name ASC",
                        limit,
                    )
                "read_messages" ->
                    query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf("address", "body", "date", "type"),
                        if (query.isBlank()) null else "address LIKE ? OR body LIKE ?",
                        if (query.isBlank()) null else arrayOf("%$query%", "%$query%"),
                        "date DESC",
                        limit,
                    )
                "read_call_log" ->
                    query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf("number", "name", "date", "duration", "type"),
                        if (query.isBlank()) null else "number LIKE ? OR name LIKE ?",
                        if (query.isBlank()) null else arrayOf("%$query%", "%$query%"),
                        "date DESC",
                        limit,
                    )
                "read_calendar" -> {
                    val start = input.optLong("startMillis", System.currentTimeMillis())
                    val end =
                        input
                            .optLong("endMillis", start + 7 * 86400000L)
                            .coerceIn(start + 1, start + 31 * 86400000L)
                    val uri =
                        CalendarContract.Instances.CONTENT_URI.buildUpon()
                            .appendPath(start.toString())
                            .appendPath(end.toString())
                            .build()
                    query(
                        uri,
                        arrayOf("title", "begin", "end", "eventLocation", "description"),
                        null,
                        null,
                        "begin ASC",
                        limit,
                    )
                }
                "read_notifications" -> {
                    val listener =
                        CueNotificationListener.active
                            ?: return failure(
                                "Notification access is still connecting. Try again shortly."
                            )
                    JSONArray(
                        listener.activeNotifications.take(limit).map { n ->
                            JSONObject()
                                .put("app", n.packageName)
                                .put("postedAt", n.postTime)
                                .put(
                                    "title",
                                    n.notification.extras
                                        .getCharSequence(Notification.EXTRA_TITLE)
                                        ?.toString()
                                        ?.take(500),
                                )
                                .put(
                                    "text",
                                    n.notification.extras
                                        .getCharSequence(Notification.EXTRA_TEXT)
                                        ?.toString()
                                        ?.take(2000),
                                )
                        }
                    )
                }
                "read_location" -> {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) != PackageManager.PERMISSION_GRANTED
                    )
                        return failure("Location permission is required.")
                    val manager =
                        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val location =
                        manager
                            .getProviders(true)
                            .mapNotNull {
                                try {
                                    manager.getLastKnownLocation(it)
                                } catch (_: SecurityException) {
                                    null
                                }
                            }
                            .maxByOrNull { it.time }
                            ?: return failure(
                                "No recent location is available. Enable location on your phone and try again."
                            )
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("latitude", location.latitude)
                                .put("longitude", location.longitude)
                                .put("accuracyMeters", location.accuracy)
                                .put("recordedAt", location.time)
                        )
                }
                else -> return failure("Use Health Connect to grant access before reading steps.")
            }
        return JSONObject().put("status", "ok").put("records", rows).put("limit", limit)
    }

    @androidx.annotation.RequiresApi(34)
    private suspend fun readHealth(input: JSONObject): JSONObject =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val manager =
                context.getSystemService(android.health.connect.HealthConnectManager::class.java)
            val end = Instant.now()
            val request =
                android.health.connect.ReadRecordsRequestUsingFilters.Builder(
                        android.health.connect.datatypes.StepsRecord::class.java
                    )
                    .setTimeRangeFilter(
                        android.health.connect.TimeInstantRangeFilter.Builder()
                            .setStartTime(end.minusSeconds(7 * 86400))
                            .setEndTime(end)
                            .build()
                    )
                    .setPageSize(input.optInt("limit", 20).coerceIn(1, 50))
                    .build()
            manager.readRecords(
                request,
                context.mainExecutor,
                object :
                    android.os.OutcomeReceiver<
                        android.health.connect.ReadRecordsResponse<
                            android.health.connect.datatypes.StepsRecord
                        >,
                        android.health.connect.HealthConnectException,
                    > {
                    override fun onResult(
                        result:
                            android.health.connect.ReadRecordsResponse<
                                android.health.connect.datatypes.StepsRecord
                            >
                    ) {
                        if (continuation.isActive)
                            continuation.resumeWith(
                                Result.success(
                                    JSONObject()
                                        .put("status", "ok")
                                        .put(
                                            "records",
                                            JSONArray(
                                                result.records.map { record ->
                                                    JSONObject()
                                                        .put("steps", record.count)
                                                        .put("start", record.startTime.toString())
                                                        .put("end", record.endTime.toString())
                                                        .put(
                                                            "source",
                                                            record.metadata.dataOrigin.packageName,
                                                        )
                                                }
                                            ),
                                        )
                                        .put(
                                            "note",
                                            "Raw step records from the last 7 days; sources may overlap. Do not sum them as a daily total.",
                                        )
                                )
                            )
                    }

                    override fun onError(error: android.health.connect.HealthConnectException) {
                        if (continuation.isActive)
                            continuation.resumeWith(
                                Result.success(
                                    failure(
                                        "Health Connect access is unavailable. Check permissions and connected health apps."
                                    )
                                )
                            )
                    }
                },
            )
        }

    private fun query(
        uri: Uri,
        columns: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String,
        limit: Int,
    ): JSONArray {
        val rows = JSONArray()
        context.contentResolver.query(uri, columns, selection, args, sort)?.use { cursor ->
            while (rows.length() < limit && cursor.moveToNext()) {
                val row = JSONObject()
                columns.forEachIndexed { index, name ->
                    row.put(
                        name,
                        if (cursor.isNull(index)) JSONObject.NULL
                        else cursor.getString(index).take(4000),
                    )
                }
                rows.put(row)
            }
        }
        return rows
    }

    private fun launchDraft(operation: String, input: JSONObject): JSONObject {
        val intent =
            when (operation) {
                "open_dialer" ->
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.fromParts("tel", input.optString("phone").take(60), null),
                    )
                "compose_message" ->
                    Intent(
                            Intent.ACTION_SENDTO,
                            Uri.fromParts("smsto", input.optString("phone").take(60), null),
                        )
                        .putExtra("sms_body", input.optString("text").take(5000))
                "create_contact" ->
                    Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(
                            ContactsContract.Intents.Insert.NAME,
                            input.optString("name").take(200),
                        )
                        .putExtra(
                            ContactsContract.Intents.Insert.PHONE,
                            input.optString("phone").take(60),
                        )
                "create_event" ->
                    Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, input.optString("title").take(200))
                        .putExtra(
                            CalendarContract.Events.DESCRIPTION,
                            input.optString("text").take(5000),
                        )
                        .putExtra(
                            CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                            input.optLong("startMillis", System.currentTimeMillis()),
                        )
                        .putExtra(
                            CalendarContract.EXTRA_EVENT_END_TIME,
                            input.optLong("endMillis", System.currentTimeMillis() + 3600000),
                        )
                else -> error("Unknown action")
            }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return JSONObject()
            .put("status", "confirmation_opened")
            .put(
                "message",
                "The device app is open. The user must confirm; do not claim the action completed.",
            )
    }

    companion object {
        fun failure(message: String) =
            JSONObject().put("status", "unavailable").put("message", message)
    }
}

// Only reads the current system-provided list on an explicit tool request; no history or background
// upload.
class CueNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        active = this
    }

    override fun onListenerDisconnected() {
        active = null
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    companion object {
        @Volatile var active: CueNotificationListener? = null
    }
}
