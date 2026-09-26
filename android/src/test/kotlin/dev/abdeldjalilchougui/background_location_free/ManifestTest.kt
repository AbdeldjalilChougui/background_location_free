package dev.abdeldjalilchougui.background_location_free

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * The Android Gradle plugin merges only `src/main/AndroidManifest.xml` into
 * the app. Without the service there, `startForegroundService` silently
 * returns null and `start()` times out (seen on an API 37 emulator).
 */
internal class ManifestTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private val manifest: Element by lazy {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue(file.isFile, "missing ${file.absolutePath}")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun mainManifestDeclaresTheLocationForegroundService() {
        val service = elements("service").firstOrNull {
            it.getAttributeNS(ns, "name") == LocationService::class.java.name
        }
        assertNotNull(service, "LocationService is not declared in src/main/AndroidManifest.xml")
        assertEquals("location", service.getAttributeNS(ns, "foregroundServiceType"))
        assertEquals("false", service.getAttributeNS(ns, "exported"))
    }

    @Test
    fun mainManifestDeclaresForegroundServicePermissions() {
        val permissions = elements("uses-permission").map { it.getAttributeNS(ns, "name") }
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_LOCATION" in permissions)
    }

    @Test
    fun mainManifestDeclaresTheDisabledBootReceiver() {
        val receiver = elements("receiver").firstOrNull {
            it.getAttributeNS(ns, "name") == BootReceiver::class.java.name
        }
        assertNotNull(receiver, "BootReceiver is not declared in src/main/AndroidManifest.xml")
        assertEquals("false", receiver.getAttributeNS(ns, "enabled"))
        assertEquals("false", receiver.getAttributeNS(ns, "exported"))
    }

    @Test
    fun noStrayManifestOutsideTheMainSourceSet() {
        assertTrue(!File("src/AndroidManifest.xml").exists(), "src/AndroidManifest.xml is never merged")
    }
}
