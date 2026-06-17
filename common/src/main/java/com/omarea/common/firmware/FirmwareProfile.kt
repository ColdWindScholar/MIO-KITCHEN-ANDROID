package com.omarea.common.firmware

import java.io.File

/**
 * RU: Источник выбранной пользователем прошивки.
 *
 * Инкапсулирует результат работы `StorageGateway`: либо прямой путь, либо
 * workspace-копия, либо открытый поток.
 *
 * EN: Source of a user-selected firmware.
 *
 * Encapsulates the output of `StorageGateway`: either a direct path, a workspace
 * copy, or an open stream.
 */
sealed class FirmwareSource {
    /**
     * RU: Прямой путь к файлу прошивки (file:// или обычный путь).
     * EN: Direct path to the firmware file (file:// or plain path).
     */
    data class DirectPath(val path: String) : FirmwareSource() {
        init {
            require(path.isNotEmpty()) { "DirectPath must not be empty" }
        }
    }

    /**
     * RU: Файл в FirmwareWorkspace (например, копия content:// URI).
     * EN: A file inside FirmwareWorkspace (e.g. a copy of a content:// URI).
     */
    data class WorkspaceFile(val file: File) : FirmwareSource()

    /**
     * RU: Только имя файла — используется, когда анализатору не нужен путь,
     *     только сигнатуры/имя.
     * EN: Filename only — used when the analyzer needs just the name, not the
     *     path (e.g. for signature-based detection).
     */
    data class FileNameOnly(val fileName: String) : FirmwareSource()
}

/**
 * RU: Тип упаковки прошивки.
 * EN: Firmware package type.
 */
enum class FirmwarePackageType {
    ZIP_OTA,
    PAYLOAD_BIN,
    SUPER_IMAGE,
    BOOT_IMAGE,
    VENDOR_BOOT_IMAGE,
    INIT_BOOT_IMAGE,
    RECOVERY_IMAGE,
    DTBO_IMAGE,
    VBMETA_IMAGE,
    FILESYSTEM_IMAGE,
    UNKNOWN
}

/**
 * RU: Подсказка версии Android внутри прошивки.
 *
 * Это только подсказка — анализ всегда опирается на capabilities, а не на номер.
 *
 * EN: Android version hint inside the firmware.
 *
 * This is only a hint — analysis is always capability-driven, not number-driven.
 */
data class AndroidVersionHint(val version: Int?) {
    val label: String get() = version?.toString() ?: "unknown"
}

/**
 * RU: Тип сжатия, обнаруженный в прошивке.
 * EN: Compression type detected in the firmware.
 */
enum class CompressionType {
    GZIP, LZ4, LZMA, ZSTD, BROTLI, BZIP2, NONE, UNKNOWN
}

/**
 * RU: Информация о разделах прошивки.
 * EN: Partition info inside the firmware.
 */
data class PartitionInfo(
    val name: String,
    val sizeBytes: Long?,
    val filesystem: String? = null,
    val compression: CompressionType = CompressionType.NONE,
    val isSparse: Boolean = false,
    val isLogical: Boolean = false
)

/**
 * RU: Предупреждение о прошивке (например, "AVB chained, проверьте ключи").
 * EN: Warning about the firmware (e.g. "AVB chained, check keys").
 */
data class FirmwareWarning(
    val code: String,
    val message: String,
    val severity: Severity = Severity.WARNING
) {
    enum class Severity { INFO, WARNING, ERROR }
}

/**
 * RU: Возможности прошивки.
 *
 * Каждое поле — это capability, обнаруженная анализатором. Никогда не
 * выводите capability из номера Android — только из содержимого файлов.
 *
 * EN: Firmware capabilities.
 *
 * Each field is a capability detected by the analyzer. Never infer a capability
 * from the Android version number — only from file contents.
 */
data class FirmwareCapabilities(
    val hasPayloadBin: Boolean = false,
    val hasSuperImage: Boolean = false,
    val hasDynamicPartitions: Boolean = false,
    val hasSparseImages: Boolean = false,
    val hasErofs: Boolean = false,
    val hasExt4: Boolean = false,
    val hasF2fs: Boolean = false,
    val hasBootImage: Boolean = false,
    val hasVendorBootImage: Boolean = false,
    val hasInitBootImage: Boolean = false,
    val hasDtboImage: Boolean = false,
    val hasVbmetaImage: Boolean = false,
    val usesAvb: Boolean = false,
    val usesAB: Boolean = false,
    val usesVirtualAB: Boolean = false,
    val usesCompressionZstd: Boolean = false,
    val usesCompressionBr: Boolean = false,
    val usesCompressionLz4: Boolean = false,
    val requires16KbAlignmentCheck: Boolean = false
)

/**
 * RU: Профиль прошивки — финальный результат анализатора.
 *
 * EN: Firmware profile — the analyzer's final result.
 */
data class FirmwareProfile(
    val source: FirmwareSource,
    val packageType: FirmwarePackageType,
    val androidVersion: AndroidVersionHint,
    val capabilities: FirmwareCapabilities,
    val partitions: List<PartitionInfo>,
    val compression: Set<CompressionType>,
    val warnings: List<FirmwareWarning>
) {
    /**
     * RU: Короткое описание профиля для UI.
     * EN: Short profile description for UI display.
     */
    val summary: String
        get() = buildString {
            append("type=").append(packageType.name.lowercase())
            if (androidVersion.version != null) {
                append(", android=").append(androidVersion.version)
            }
            if (capabilities.hasDynamicPartitions) append(", dynamic")
            if (capabilities.hasErofs) append(", erofs")
            if (capabilities.hasExt4) append(", ext4")
            if (capabilities.usesAvb) append(", avb")
            if (capabilities.usesVirtualAB) append(", vAB")
            if (capabilities.requires16KbAlignmentCheck) append(", 16K")
        }
}
