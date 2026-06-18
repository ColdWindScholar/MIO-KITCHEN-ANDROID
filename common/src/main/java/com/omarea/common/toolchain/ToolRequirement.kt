package com.omarea.common.toolchain

/**
 * RU: Назначение инструмента.
 *
 * Каждое значение соответствует capabilities из [com.omarea.common.firmware.FirmwareCapabilities]
 * или категориям операций.
 *
 * EN: Purpose of a tool.
 *
 * Each value corresponds to a capability from [com.omarea.common.firmware.FirmwareCapabilities]
 * or to operation categories.
 */
enum class ToolPurpose {
    SUPER_IMAGE,            // lpunpack, lpmake, simg2img, img2simg
    PAYLOAD_BIN,            // payload-dumper, update_engine_parser
    EROFS,                  // fsck.erofs, dump.erofs, mkfs.erofs
    EXT4,                   // mke2fs, e2fsdroid, resize2fs, make_ext4fs
    BOOT_IMAGE,             // unpack_bootimg, mkbootimg, magiskboot
    VBMETA,                 // avbtool, vbmeta parser
    SPARSE,                 // simg2img, img2simg
    COMPRESSION_BROTLI,     // brotli
    COMPRESSION_LZ4,        // lz4
    COMPRESSION_LZMA,       // xz, unlzma
    COMPRESSION_ZSTD,       // zstd
    COMPRESSION_BZIP2,      // bzip2, bunzip2
    BUSYBOX,                // busybox (general utilities)
    FILESYSTEM_MOUNT,       // mount/umount helpers
    GENERIC                 // misc / unknown
}

/**
 * RU: Требование к одному инструменту.
 *
 * @param name имя бинарника, например `lpunpack`.
 * @param minVersion минимальная версия или `null`, если любая.
 * @param purpose назначение из [ToolPurpose].
 * @param abi список поддерживаемых ABI, например `["arm64-v8a", "armeabi-v7a"]`.
 * @param checksum SHA-256 или `null`, если проверка не требуется.
 * @param required `true`, если инструмент обязателен; `false`, если опциональный.
 *
 * EN: Requirement for a single tool.
 *
 * @param name binary name, e.g. `lpunpack`.
 * @param minVersion minimum version or `null` for any.
 * @param purpose purpose from [ToolPurpose].
 * @param abi list of supported ABIs, e.g. `["arm64-v8a", "armeabi-v7a"]`.
 * @param checksum SHA-256 or `null` if verification is skipped.
 * @param required `true` if the tool is required; `false` if optional.
 */
data class ToolRequirement(
    val name: String,
    val minVersion: String? = null,
    val purpose: ToolPurpose,
    val abi: List<String> = emptyList(),
    val checksum: String? = null,
    val required: Boolean = true
) {
    init {
        require(name.isNotEmpty()) { "ToolRequirement.name must not be empty" }
    }
}

/**
 * RU: Описание доступного инструмента в манифесте.
 *
 * Загружается из `assets/toolchain/manifest.json`.
 *
 * EN: Description of an available tool from the manifest.
 *
 * Loaded from `assets/toolchain/manifest.json`.
 */
data class ToolDescriptor(
    val name: String,
    val version: String,
    val abi: List<String>,
    val sha256: String?,
    val capabilities: List<ToolPurpose>,
    val source: String,
    val license: String,
    val supports16KbPageSize: Boolean?
) {
    init {
        require(name.isNotEmpty()) { "ToolDescriptor.name must not be empty" }
        require(version.isNotEmpty()) { "ToolDescriptor.version must not be empty" }
    }
}

/**
 * RU: Профиль toolchain для конкретной операции.
 *
 * @param requiredTools обязательные инструменты.
 * @param optionalTools опциональные инструменты.
 * @param selectedAbi выбранный ABI для устройства.
 * @param toolsDir директория с распакованными бинарниками.
 * @param verifiedChecksums `true`, если все SHA-256 проверены.
 *
 * EN: Toolchain profile for a specific operation.
 *
 * @param requiredTools required tools.
 * @param optionalTools optional tools.
 * @param selectedAbi the ABI selected for the device.
 * @param toolsDir directory with extracted binaries.
 * @param verifiedChecksums `true` when all SHA-256 have been verified.
 */
data class ToolchainProfile(
    val requiredTools: List<ToolRequirement>,
    val optionalTools: List<ToolRequirement>,
    val selectedAbi: String,
    val toolsDir: String,
    val verifiedChecksums: Boolean
) {
    /**
     * RU: Возвращает все инструменты (обязательные + опциональные).
     * EN: Returns all tools (required + optional).
     */
    val allTools: List<ToolRequirement> get() = requiredTools + optionalTools

    /**
     * RU: Возвращает `true`, если хотя бы один обязательный инструмент отсутствует.
     * EN: Returns `true` if any required tool is missing.
     */
    fun isMissingRequired(availableTools: Set<String>): Boolean =
        requiredTools.any { it.name !in availableTools }

    /**
     * RU: Возвращает имена отсутствующих обязательных инструментов.
     * EN: Returns the names of missing required tools.
     */
    fun missingRequired(availableTools: Set<String>): List<String> =
        requiredTools.filter { it.name !in availableTools }.map { it.name }
}
