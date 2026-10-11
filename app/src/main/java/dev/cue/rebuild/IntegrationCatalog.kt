package dev.cue.rebuild

import android.content.Context
import org.json.JSONObject

data class Integration(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val url: String = "",
    val icon: String? = null,
    val glyph: Glyph = Glyph.Box,
)

val integrationCategories =
    linkedMapOf(
        "device" to "From this device",
        "channels" to "Channels",
        "connectors" to "Connectors",
        "extensions" to "Extensions",
        "memory" to "Memory",
        "observability" to "Observability",
    )

fun integrationCatalog(context: Context): List<Integration> {
    val bundled =
        JSONObject(
                context.assets.open("integrations/catalog.json").bufferedReader().use {
                    it.readText()
                }
            )
            .getJSONArray("items")
    return deviceIntegrations +
        (0 until bundled.length()).map { i ->
            val item = bundled.getJSONObject(i)
            Integration(
                item.getString("id"),
                item.getString("name"),
                item.getString("category"),
                item.getString("description"),
                item.getString("url"),
                item.optString("icon").takeUnless { it == "null" || it.isBlank() },
            )
        }
}

val deviceIntegrations =
    listOf(
        Integration(
            "device-calendar",
            "Calendar",
            "device",
            "Read your schedule and prepare events in your calendar app.",
            glyph = Glyph.Calendar,
        ),
        Integration(
            "device-contacts",
            "Contacts",
            "device",
            "Find contacts and prepare new contacts on this phone.",
            glyph = Glyph.Contacts,
        ),
        Integration(
            "device-messages",
            "Messages",
            "device",
            "Read SMS messages and prepare replies in your messaging app.",
            glyph = Glyph.Chat,
        ),
        Integration(
            "device-dialer",
            "Phone dialer",
            "device",
            "Prepare a phone call. You place the call in your dialer.",
            glyph = Glyph.Call,
        ),
        Integration(
            "device-notifications",
            "Notifications",
            "device",
            "Read currently visible notifications when you ask.",
            glyph = Glyph.Bell,
        ),
        Integration(
            "device-call-log",
            "Call Log",
            "device",
            "Look up recent calls on this phone.",
            glyph = Glyph.Call,
        ),
        Integration(
            "device-health",
            "Health Connect",
            "device",
            "Read step records shared with Health Connect.",
            glyph = Glyph.Heart,
        ),
        Integration(
            "device-location",
            "Location",
            "device",
            "Use this phone's approximate last-known location.",
            glyph = Glyph.Location,
        ),
    )
