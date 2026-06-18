package com.mio.kitchen.ui.modern

import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareWarning

/**
 * RU: Форматирует [FirmwareProfile] в человекочитаемый текст для UI.
 *
 * Этот класс НЕ зависит от Android View API — он работает со строками. Это
 * позволяет переиспользовать его в тестах, в Compose, в View, в логах.
 *
 * EN: Formats a [FirmwareProfile] into human-readable text for UI.
 *
 * This class does NOT depend on Android View API — it works with strings. This
 * makes it reusable from tests, Compose, View, and logs.
 */
object FirmwareProfileFormatter {

    /**
     * RU: Возвращает короткое однострочное описание профиля.
     * EN: Returns a short one-line description of the profile.
     */
    fun short(profile: FirmwareProfile): String = profile.summary

    /**
     * RU: Возвращает многострочное детальное описание профиля.
     * EN: Returns a multi-line detailed description of the profile.
     */
    fun detailed(profile: FirmwareProfile): String = buildString {
        appendLine("Type: ${profile.packageType.name.lowercase().replace('_', ' ')}")
        appendLine("Android: ${profile.androidVersion.label}")
        appendLine("Capabilities:")
        val caps = profile.capabilities
        val capLines = mutableListOf<String>()
        if (caps.hasPayloadBin) capLines += "  - payload.bin"
        if (caps.hasSuperImage) capLines += "  - super.img"
        if (caps.hasDynamicPartitions) capLines += "  - dynamic partitions"
        if (caps.hasSparseImages) capLines += "  - sparse images"
        if (caps.hasErofs) capLines += "  - EROFS"
        if (caps.hasExt4) capLines += "  - ext4"
        if (caps.hasF2fs) capLines += "  - F2FS"
        if (caps.hasBootImage) capLines += "  - boot.img"
        if (caps.hasVendorBootImage) capLines += "  - vendor_boot.img"
        if (caps.hasInitBootImage) capLines += "  - init_boot.img"
        if (caps.hasDtboImage) capLines += "  - dtbo.img"
        if (caps.usesAvb) capLines += "  - AVB"
        if (caps.usesAB) capLines += "  - A/B"
        if (caps.usesVirtualAB) capLines += "  - Virtual A/B"
        if (caps.usesCompressionZstd) capLines += "  - zstd"
        if (caps.usesCompressionBr) capLines += "  - brotli"
        if (caps.usesCompressionLz4) capLines += "  - lz4"
        if (caps.requires16KbAlignmentCheck) capLines += "  - 16 KB page-size check"
        if (capLines.isEmpty()) {
            appendLine("  (none)")
        } else {
            capLines.forEach { appendLine(it) }
        }

        if (profile.partitions.isNotEmpty()) {
            appendLine("Partitions:")
            profile.partitions.forEach { p ->
                val parts = mutableListOf<String>()
                p.filesystem?.let { parts += it }
                if (p.isSparse) parts += "sparse"
                if (p.isLogical) parts += "logical"
                p.sizeBytes?.let { parts += formatBytes(it) }
                val tail = if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
                appendLine("  - ${p.name}$tail")
            }
        }

        if (profile.warnings.isNotEmpty()) {
            appendLine("Warnings:")
            profile.warnings.forEach { w ->
                val tag = when (w.severity) {
                    FirmwareWarning.Severity.ERROR -> "[ERROR]"
                    FirmwareWarning.Severity.WARNING -> "[WARN] "
                    FirmwareWarning.Severity.INFO -> "[INFO] "
                }
                appendLine("  $tag ${w.code}: ${w.message}")
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.size - 1) {
            size /= 1024
            unit++
        }
        return String.format("%.1f %s", size, units[unit])
    }
}
