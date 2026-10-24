package com.eio258.cleanchooser.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-side runtime log shown on the log page: what this process actually did (component batches,
 * restores, config resets, failures) plus crash summaries. Single-line events go to the ring and
 * the bounded file; crash stack traces are appended to the file directly.
 */
object AppLog {
    private const val TAG = "ListCleaner.AppLog"
    private const val FILE_NAME = "app_runtime_log.txt"
    private const val MAX_LINES = 250
    private const val MAX_FILE_BYTES = 64 * 1024

    private val ring = ArrayDeque<String>()
    private val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Volatile
    private var file: File? = null

    fun init(app: Context) {
        val target = File(app.filesDir, FILE_NAME)
        file = target
        synchronized(ring) {
            ring.clear()
            runCatching {
                target.takeIf { it.exists() }?.readLines()?.takeLast(MAX_LINES)?.forEach(ring::addLast)
            }
        }
    }

    fun event(message: String) = append("I", message, null)

    fun error(message: String, failure: Throwable? = null) = append("E", message, failure)

    /** Crash entry: one summary line in the ring, the full trace only in the file. */
    fun crash(threadName: String, failure: Throwable) {
        val summary = "E crash on $threadName ${failure.javaClass.name}: ${failure.message}"
        appendRaw(summary)
        runCatching {
            file?.appendText(Log.getStackTraceString(failure) + "\n")
        }
    }

    @Synchronized
    fun snapshot(): List<String> = ring.toList()

    @Synchronized
    fun clear() {
        ring.clear()
        runCatching { file?.delete() }
    }

    private fun append(level: String, message: String, failure: Throwable?) {
        val detail = failure?.let { " ${it.javaClass.simpleName}: ${it.message}" } ?: ""
        appendRaw("$level $message$detail")
    }

    private fun appendRaw(line: String) {
        val stamped = "${time.format(Date())} $line"
        synchronized(ring) {
            if (ring.size >= MAX_LINES) ring.removeFirst()
            ring.addLast(stamped)
        }
        Log.i(TAG, line)
        runCatching {
            val target = file ?: return
            target.appendText(stamped + "\n")
            if (target.length() > MAX_FILE_BYTES) {
                val text = target.readText()
                target.writeText(text.substring(text.length - MAX_FILE_BYTES / 2))
            }
        }
    }
}
