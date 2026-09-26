package dev.abdeldjalilchougui.background_location_free

import java.io.File

/**
 * Bounded FIFO of fixes persisted to a text file, one [FixRecord.encode] line
 * per fix. Appends are O(1) file appends; the file is rewritten (to a temp
 * file, then renamed) once it holds twice [capacity] lines, and on open when
 * it needs repair. Corrupt or truncated lines are skipped. Thread-safe.
 */
internal class FileRingBuffer(private val file: File, capacity: Int) {
    var capacity: Int = capacity
        private set

    private val items = ArrayDeque<FixRecord>()
    private var fileLines = 0

    init {
        require(capacity > 0) { "capacity must be positive" }
        load()
    }

    @Synchronized
    fun size(): Int = items.size

    @Synchronized
    fun append(fix: FixRecord) {
        items.addLast(fix)
        while (items.size > capacity) items.removeFirst()
        file.parentFile?.mkdirs()
        file.appendText(fix.encode() + "\n")
        fileLines++
        if (fileLines > capacity * 2) rewrite()
    }

    /** Returns every buffered fix, oldest first, and empties the buffer. */
    @Synchronized
    fun drain(): List<FixRecord> {
        val out = items.toList()
        items.clear()
        rewrite()
        return out
    }

    /** Changes the capacity, dropping the oldest fixes when it shrinks. */
    @Synchronized
    fun resize(newCapacity: Int) {
        require(newCapacity > 0) { "capacity must be positive" }
        capacity = newCapacity
        if (items.size > capacity) {
            while (items.size > capacity) items.removeFirst()
            rewrite()
        }
    }

    private fun load() {
        if (!file.exists()) return
        val text = file.readText()
        var bad = false
        var lines = 0
        for (line in text.split('\n')) {
            if (line.isEmpty()) continue
            lines++
            val fix = FixRecord.decode(line)
            if (fix == null) {
                bad = true
            } else {
                items.addLast(fix)
            }
        }
        while (items.size > capacity) items.removeFirst()
        fileLines = lines
        if (bad || lines != items.size || (text.isNotEmpty() && !text.endsWith("\n"))) {
            rewrite()
        }
    }

    private fun rewrite() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(items.joinToString("") { it.encode() + "\n" })
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        fileLines = items.size
    }
}
