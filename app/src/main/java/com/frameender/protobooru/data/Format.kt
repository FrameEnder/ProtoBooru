package com.frameender.protobooru.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneId.systemDefault())
    private val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US).withZone(ZoneId.systemDefault())

    private fun parse(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else it + "Z") }.getOrNull() }

    fun date(iso: String?): String = parse(iso)?.let { dateFmt.format(it) } ?: "—"
    fun dateTime(iso: String?): String = parse(iso)?.let { dateTimeFmt.format(it) } ?: "—"

    /** "2h ago" for an epoch-millisecond time. */
    fun agoMillis(ms: Long): String = agoFrom(Instant.ofEpochMilli(ms))

    fun ago(iso: String?): String = parse(iso)?.let { agoFrom(it) } ?: "—"

    private fun agoFrom(t: Instant): String {
        val d = Duration.between(t, Instant.now())
        return when {
            d.isNegative -> "just now"
            d.seconds < 60 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            d.toDays() < 30 -> "${d.toDays()}d ago"
            d.toDays() < 365 -> "${d.toDays() / 30}mo ago"
            else -> "${d.toDays() / 365}y ago"
        }
    }

    fun bytes(n: Long?): String {
        if (n == null || n <= 0) return "—"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = n.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (i == 0) "$n B" else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun count(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 10_000 -> String.format(Locale.US, "%.1fk", n / 1000.0)
        else -> n.toString()
    }

    /** ISO timestamp [days] from now, for token expiration. */
    fun isoInDays(days: Long): String = Instant.now().plus(Duration.ofDays(days)).toString()
}
