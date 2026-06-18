package com.omarea.common.firmware

import java.io.File
import java.io.InputStream

/**
 * RU: Базовый класс для анализаторов отдельных `.img` файлов.
 *
 * Содержит общую логику чтения заголовков/сигнатур.
 *
 * EN: Base class for analyzers of individual `.img` files.
 *
 * Contains shared logic for reading headers/signatures.
 */
abstract class ImageFirmwareAnalyzer : FirmwareAnalyzer {
    /**
     * RU: Открывает поток для чтения первых N байтов файла.
     * EN: Opens a stream for reading the first N bytes of the file.
     */
    protected fun openStream(source: FirmwareSource): InputStream = when (source) {
        is FirmwareSource.DirectPath -> File(source.path).inputStream()
        is FirmwareSource.WorkspaceFile -> source.file.inputStream()
        is FirmwareSource.FileNameOnly ->
            throw FirmwareAnalysisException("Cannot read image from FileNameOnly source")
    }

    /**
     * RU: Читает до [maxBytes] байтов из начала файла.
     * EN: Reads up to [maxBytes] from the start of the file.
     */
    protected fun readHead(source: FirmwareSource, maxBytes: Int): ByteArray {
        openStream(source).use { input ->
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            return if (read == maxBytes) buffer else buffer.copyOf(read)
        }
    }

    /**
     * RU: Возвращает имя файла источника.
     * EN: Returns the source file name.
     */
    protected fun fileName(source: FirmwareSource): String = when (source) {
        is FirmwareSource.DirectPath -> File(source.path).name
        is FirmwareSource.WorkspaceFile -> source.file.name
        is FirmwareSource.FileNameOnly -> source.fileName
    }
}

/**
 * RU: Анализатор `boot.img` / `vendor_boot.img` / `init_boot.img` /
 *     `recovery.img`.
 *
 * Определяет версию boot image header (v0..v4) по магическим байтам `ANDROID!`.
 *
 * EN: Analyzer for `boot.img` / `vendor_boot.img` / `init_boot.img` /
 *     `recovery.img`.
 *
 * Detects the boot image header version (v0..v4) by the `ANDROID!` magic bytes.
 */
class BootImageAnalyzer : ImageFirmwareAnalyzer() {
    override fun supports(source: FirmwareSource): Boolean {
        val name = fileName(source).lowercase()
        return name == "boot.img" ||
            name == "vendor_boot.img" ||
            name == "init_boot.img" ||
            name == "recovery.img" ||
            name.endsWith(".img") && name.contains("boot")
    }

    override fun analyze(source: FirmwareSource): FirmwareProfile {
        val head = readHead(source, maxBytes = 4096)
        val magic = String(head, 0, minOf(8, head.size), Charsets.US_ASCII)
        val name = fileName(source).lowercase()

        val packageType = when {
            name.startsWith("vendor_boot") -> FirmwarePackageType.VENDOR_BOOT_IMAGE
            name.startsWith("init_boot") -> FirmwarePackageType.INIT_BOOT_IMAGE
            name.startsWith("recovery") -> FirmwarePackageType.RECOVERY_IMAGE
            else -> FirmwarePackageType.BOOT_IMAGE
        }

        val headerVersion = if (magic.startsWith("ANDROID!")) {
            // Header version is at offset 40 (v3+) or detected by structure.
            if (head.size >= 41) head[40].toInt() and 0xFF else 0
        } else {
            // Non-Android boot magic — could be Qualcomm/MTK proprietary.
            -1
        }

        val warnings = mutableListOf<FirmwareWarning>()
        if (headerVersion < 0) {
            warnings.add(
                FirmwareWarning(
                    code = "unknown-boot-magic",
                    message = "Boot image magic is not ANDROID! — proprietary header?"
                )
            )
        }
        if (headerVersion == 4) {
            warnings.add(
                FirmwareWarning(
                    code = "boot-header-v4",
                    message = "Boot image header v4 — verify GKI compatibility",
                    severity = FirmwareWarning.Severity.INFO
                )
            )
        }

        val capabilities = FirmwareCapabilities(
            hasBootImage = packageType == FirmwarePackageType.BOOT_IMAGE,
            hasVendorBootImage = packageType == FirmwarePackageType.VENDOR_BOOT_IMAGE,
            hasInitBootImage = packageType == FirmwarePackageType.INIT_BOOT_IMAGE
        )

        return FirmwareProfile(
            source = source,
            packageType = packageType,
            androidVersion = AndroidVersionHint(null),
            capabilities = capabilities,
            partitions = listOf(
                PartitionInfo(
                    name = name.removeSuffix(".img"),
                    sizeBytes = null
                )
            ),
            compression = emptySet(),
            warnings = warnings
        )
    }
}

/**
 * RU: Анализатор `super.img` — образа dynamic partitions.
 *
 * Определяет sparse-формат по магическим байтам `0x3aff26ed`.
 *
 * EN: Analyzer for `super.img` — the dynamic partitions image.
 *
 * Detects sparse format by the `0x3aff26ed` magic bytes.
 */
class SuperImageAnalyzer : ImageFirmwareAnalyzer() {
    override fun supports(source: FirmwareSource): Boolean {
        val name = fileName(source).lowercase()
        return name == "super.img" || name.endsWith("super.img")
    }

    override fun analyze(source: FirmwareSource): FirmwareProfile {
        val head = readHead(source, maxBytes = 4)
        val isSparse = head.size >= 4 &&
            (head[0].toInt() and 0xFF) == 0x3a &&
            (head[1].toInt() and 0xFF) == 0xff &&
            (head[2].toInt() and 0xFF) == 0x26 &&
            (head[3].toInt() and 0xFF) == 0xed

        val capabilities = FirmwareCapabilities(
            hasSuperImage = true,
            hasDynamicPartitions = true,
            hasSparseImages = isSparse
        )

        return FirmwareProfile(
            source = source,
            packageType = FirmwarePackageType.SUPER_IMAGE,
            androidVersion = AndroidVersionHint(null),
            capabilities = capabilities,
            partitions = listOf(
                PartitionInfo(
                    name = "super",
                    sizeBytes = null,
                    isLogical = true,
                    isSparse = isSparse
                )
            ),
            compression = if (isSparse) setOf(CompressionType.NONE) else emptySet(),
            warnings = emptyList()
        )
    }
}

/**
 * RU: Анализатор `vbmeta.img` — образа Android Verified Boot.
 *
 * Определяет AVB-структуру по магическим байтам `AVB0`.
 *
 * EN: Analyzer for `vbmeta.img` — the Android Verified Boot image.
 *
 * Detects AVB structure by the `AVB0` magic bytes.
 */
class VbmetaAnalyzer : ImageFirmwareAnalyzer() {
    override fun supports(source: FirmwareSource): Boolean {
        val name = fileName(source).lowercase()
        return name == "vbmeta.img" || name.startsWith("vbmeta_") || name.contains("vbmeta")
    }

    override fun analyze(source: FirmwareSource): FirmwareProfile {
        val head = readHead(source, maxBytes = 4)
        val isAvb = head.size >= 4 &&
            head[0] == 'A'.code.toByte() &&
            head[1] == 'V'.code.toByte() &&
            head[2] == 'B'.code.toByte() &&
            head[3] == '0'.code.toByte()

        val warnings = mutableListOf<FirmwareWarning>()
        if (!isAvb) {
            warnings.add(
                FirmwareWarning(
                    code = "vbmeta-bad-magic",
                    message = "vbmeta image does not start with AVB0 magic"
                )
            )
        }

        return FirmwareProfile(
            source = source,
            packageType = FirmwarePackageType.VBMETA_IMAGE,
            androidVersion = AndroidVersionHint(null),
            capabilities = FirmwareCapabilities(
                hasVbmetaImage = true,
                usesAvb = isAvb
            ),
            partitions = listOf(
                PartitionInfo(name = fileName(source).removeSuffix(".img"), sizeBytes = null)
            ),
            compression = emptySet(),
            warnings = warnings
        )
    }
}

/**
 * RU: Анализатор отдельных filesystem-образов (system.img, vendor.img, ...).
 *
 * Определяет filesystem по магическим байтам:
 *   - `0xef53` (little-endian) — ext4/ext2 superblock magic;
 *   - `0xe0f5e1e2` — EROFS magic.
 *
 * EN: Analyzer for individual filesystem images (system.img, vendor.img, ...).
 *
 * Detects filesystem by magic bytes:
 *   - `0xef53` (little-endian) — ext4/ext2 superblock magic;
 *   - `0xe0f5e1e2` — EROFS magic.
 */
class FilesystemImageAnalyzer : ImageFirmwareAnalyzer() {
    override fun supports(source: FirmwareSource): Boolean {
        val name = fileName(source).lowercase()
        if (!name.endsWith(".img")) return false
        if (name == "boot.img" || name == "vendor_boot.img" ||
            name == "init_boot.img" || name == "recovery.img" ||
            name == "super.img" || name == "dtbo.img" ||
            name.startsWith("vbmeta")
        ) return false
        return true
    }

    override fun analyze(source: FirmwareSource): FirmwareProfile {
        // ext4 superblock magic 0xef53 lives at offset 0x438 (1080).
        val head = readHead(source, maxBytes = 2048)
        var filesystem: String? = null
        var isExt4 = false
        var isErofs = false
        var isF2fs = false

        if (head.size >= 1082) {
            val ext4Magic = (head[1080].toInt() and 0xFF) or
                ((head[1081].toInt() and 0xFF) shl 8)
            if (ext4Magic == 0xef53) {
                filesystem = "ext4"
                isExt4 = true
            }
        }
        // EROFS magic: 0xe0f5e1e2 (little-endian) at offset 0x400.
        if (head.size >= 1028 && filesystem == null) {
            val erofsMagic = (head[1024].toInt() and 0xFF) or
                ((head[1025].toInt() and 0xFF) shl 8) or
                ((head[1026].toInt() and 0xFF) shl 16) or
                ((head[1027].toInt() and 0xFF) shl 24)
            if (erofsMagic.toLong() == 0xe0f5e1e2L) {
                filesystem = "erofs"
                isErofs = true
            }
        }
        // F2FS superblock magic 0xf2f52010 at offset 0x400.
        if (head.size >= 1028 && filesystem == null) {
            val f2fsMagic = (head[1024].toInt() and 0xFF) or
                ((head[1025].toInt() and 0xFF) shl 8) or
                ((head[1026].toInt() and 0xFF) shl 16) or
                ((head[1027].toInt() and 0xFF) shl 24)
            if (f2fsMagic.toLong() == 0xf2f52010L) {
                filesystem = "f2fs"
                isF2fs = true
            }
        }

        return FirmwareProfile(
            source = source,
            packageType = FirmwarePackageType.FILESYSTEM_IMAGE,
            androidVersion = AndroidVersionHint(null),
            capabilities = FirmwareCapabilities(
                hasExt4 = isExt4,
                hasErofs = isErofs,
                hasF2fs = isF2fs
            ),
            partitions = listOf(
                PartitionInfo(
                    name = fileName(source).removeSuffix(".img"),
                    sizeBytes = null,
                    filesystem = filesystem
                )
            ),
            compression = emptySet(),
            warnings = if (filesystem == null)
                listOf(
                    FirmwareWarning(
                        code = "unknown-filesystem",
                        message = "Could not detect filesystem magic for ${fileName(source)}"
                    )
                )
            else emptyList()
        )
    }
}
