package dev.abdeldjalilchougui.background_location_free

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RingBufferTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("ringbuffer").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun fix(i: Int) = FixRecord(
        lat = i.toDouble() / 10, lon = 3.0, timeMs = i * 1000L,
        accuracy = 5f, altitude = null, speed = 1.5f, bearing = null,
        provider = "gps", isMocked = i % 2 == 0,
    )

    @Test
    fun keepsInsertionOrder() {
        val b = FileRingBuffer(File(dir, "buf.txt"), capacity = 10)
        (1..5).forEach { b.append(fix(it)) }
        assertEquals(5, b.size())
        assertEquals((1..5).map { fix(it) }, b.drain())
    }

    @Test
    fun dropsTheOldestBeyondCapacity() {
        val b = FileRingBuffer(File(dir, "buf.txt"), capacity = 3)
        (1..10).forEach { b.append(fix(it)) }
        assertEquals(3, b.size())
        assertEquals(listOf(fix(8), fix(9), fix(10)), b.drain())
    }

    @Test
    fun drainEmptiesTheBuffer() {
        val b = FileRingBuffer(File(dir, "buf.txt"), capacity = 3)
        b.append(fix(1))
        b.drain()
        assertEquals(0, b.size())
        assertEquals(emptyList(), b.drain())
    }

    @Test
    fun survivesReopening() {
        val f = File(dir, "buf.txt")
        FileRingBuffer(f, capacity = 5).apply { (1..7).forEach { append(fix(it)) } }
        val reopened = FileRingBuffer(f, capacity = 5)
        assertEquals(5, reopened.size())
        assertEquals((3..7).map { fix(it) }, reopened.drain())
    }

    @Test
    fun reopeningWithASmallerCapacityTrims() {
        val f = File(dir, "buf.txt")
        FileRingBuffer(f, capacity = 5).apply { (1..5).forEach { append(fix(it)) } }
        assertEquals(listOf(fix(4), fix(5)), FileRingBuffer(f, capacity = 2).drain())
    }

    @Test
    fun fileStaysBoundedUnderManyAppends() {
        val f = File(dir, "buf.txt")
        val b = FileRingBuffer(f, capacity = 4)
        (1..1000).forEach { b.append(fix(it)) }
        val lines = f.readLines().filter { it.isNotBlank() }
        assertTrue(lines.size <= 8, "file has ${lines.size} lines")
        assertEquals((997..1000).map { fix(it) }, b.drain())
    }

    @Test
    fun skipsATruncatedOrCorruptLine() {
        val f = File(dir, "buf.txt")
        FileRingBuffer(f, capacity = 5).apply { append(fix(1)); append(fix(2)) }
        f.appendText("1\t0.5\tgarbage")
        val b = FileRingBuffer(f, capacity = 5)
        assertEquals(listOf(fix(1), fix(2)), b.drain())
    }

    @Test
    fun missingFileIsEmpty() {
        val b = FileRingBuffer(File(dir, "sub/none.txt"), capacity = 5)
        assertEquals(0, b.size())
        b.append(fix(1))
        assertEquals(listOf(fix(1)), b.drain())
    }

    @Test
    fun encodesProvidersWithSeparators() {
        val weird = fix(1).copy(provider = "a\tb\nc\\d", altitude = 12.5, bearing = 359.5f)
        assertEquals(weird, FixRecord.decode(weird.encode()))
        val none = fix(2).copy(provider = null, accuracy = null, speed = null)
        assertEquals(none, FixRecord.decode(none.encode()))
    }
}
