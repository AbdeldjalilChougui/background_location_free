package dev.abdeldjalilchougui.background_location_free

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class NotificationSpecTest {
    private val base = NotificationSpec(
        title = "Tracking",
        text = "On",
        iconResource = "ic_stat_pin",
        channelId = "c",
        channelName = "Channel",
        color = null,
        ongoing = true,
    )

    @Test
    fun updateReplacesOnlyGivenFields() {
        assertEquals(base.copy(title = "New"), base.updated(title = "New", text = null))
        assertEquals(base.copy(text = "Body"), base.updated(title = null, text = "Body"))
        assertEquals(base.copy(title = "A", text = "B"), base.updated("A", "B"))
    }

    @Test
    fun updateRejectsAnEmptyTitle() {
        assertFailsWith<IllegalArgumentException> { base.updated(title = "", text = null) }
    }

    @Test
    fun iconResolutionPrefersDrawableThenMipmapThenFallback() {
        val res = mapOf(("ic_stat_pin" to "mipmap") to 7, ("ic_stat_pin" to "drawable") to 5)
        val lookup = { name: String, type: String -> res[name to type] ?: 0 }
        assertEquals(5, base.resolveIcon(lookup, fallback = 1))
        val onlyMipmap = { name: String, type: String ->
            if (type == "mipmap" && name == "ic_stat_pin") 7 else 0
        }
        assertEquals(7, base.resolveIcon(onlyMipmap, fallback = 1))
        assertEquals(1, base.resolveIcon({ _, _ -> 0 }, fallback = 1))
        assertEquals(1, base.copy(iconResource = null).resolveIcon(lookup, fallback = 1))
    }

    @Test
    fun zeroFallbackUsesTheFrameworkLocationIcon() {
        assertEquals(
            NotificationSpec.FRAMEWORK_FALLBACK_ICON,
            base.copy(iconResource = null).resolveIcon({ _, _ -> 0 }, fallback = 0),
        )
    }

    @Test
    fun colorIsStoredAsArgbInt() {
        val spec = NotificationSpec.fromMap(
            mapOf("title" to "t", "text" to "x", "color" to 0xFFFF0000L),
        )
        assertEquals(0xFFFF0000.toInt(), spec.color)
        assertEquals(0xFFFF0000L, spec.toMap()["color"])
    }
}
