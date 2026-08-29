package com.apexos.repoguardian.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val tag: String,
    val level: LogLevel,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

object AppLogger {
    private const val MAX_LOGS = 800
    private val idCounter = AtomicLong(1)
    private val lock = Any()
    private val logList = ArrayList<LogEntry>(MAX_LOGS)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun d(tag: String, message: String, details: String? = null) {
        try { Log.d(tag, if (details != null) "$message\n$details" else message) } catch (ignored: Throwable) {}
        addEntry(tag, LogLevel.DEBUG, message, details)
    }

    fun i(tag: String, message: String, details: String? = null) {
        try { Log.i(tag, if (details != null) "$message\n$details" else message) } catch (ignored: Throwable) {}
        addEntry(tag, LogLevel.INFO, message, details)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val detail = throwable?.stackTraceToString()
        try { Log.w(tag, message, throwable) } catch (ignored: Throwable) {}
        addEntry(tag, LogLevel.WARN, message, detail)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val detail = throwable?.stackTraceToString()
        try { Log.e(tag, message, throwable) } catch (ignored: Throwable) {}
        addEntry(tag, LogLevel.ERROR, message, detail)
    }

    private fun addEntry(tag: String, level: LogLevel, message: String, details: String?) {
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            tag = tag,
            level = level,
            message = message,
            details = details
        )
        synchronized(lock) {
            if (logList.size >= MAX_LOGS) {
                logList.removeAt(0)
            }
            logList.add(entry)
            _logs.value = logList.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            logList.clear()
            _logs.value = emptyList()
        }
    }

    fun getFormattedLogsText(): String {
        val entries = synchronized(lock) { logList.toList() }
        val dateHeader = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("=== REPO GUARDIAN RUNTIME LOGS (DEV STAGE) ===")
        sb.appendLine("Generated at: $dateHeader")
        sb.appendLine("Total Entries: ${entries.size}")
        sb.appendLine("==============================================")
        sb.appendLine()

        for (e in entries) {
            sb.appendLine("[${e.formattedTime}] [${e.level.name}] [${e.tag}] ${e.message}")
            if (!e.details.isNullOrBlank()) {
                sb.appendLine("   Details/Stacktrace:\n${e.details.prependIndent("      ")}")
            }
        }
        return sb.toString()
    }
}
