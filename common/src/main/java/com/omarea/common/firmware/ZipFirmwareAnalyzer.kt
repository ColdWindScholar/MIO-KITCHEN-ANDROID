package com.omarea.common.firmware

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * RU: Анализатор OTA/ROM zip-архивов.
 *
 * Определяет по содержимому zip:
 *   - `payload.bin` -> [FirmwarePackageType.PAYLOAD_BIN] (Android 10+ OTA);
 *   - `images/*.img` -> набор отдельных образов;
 *   - `META-INF/com/android/metadata` -> A/B или non-A/B OTA;
 *   - `super.img` -> dynamic partitions;
 *   - `vbmeta.img` -> AVB;
 *   - `boot.img` / `vendor_boot.img` / `init_boot.img` -> boot images;
 *   - `system.img` / `vendor.img` / `product.img` -> filesystem images.
 *
 * Анализатор не извлекает файлы — он только читает `ZipInputStream` и собирает
 * capabilities.
 *
 * EN: Analyzer for OTA/ROM zip archives.
 *
 * Detects from the zip contents:
 *   - `payload.bin` -> [FirmwarePackageType.PAYLOAD_BIN] (Android 10+ OTA);
 *   - `images/*.img` -> set of individual images;
 *   - `META-INF/com/android/metadata` -> A/B or non-A/B OTA;
 *   - `super.img` -> dynamic partitions;
 *   - `vbmeta.img` -> AVB;
 *   - `boot.img` / `vendor_boot.img` / `init_boot.img` -> boot images;
 *   - `system.img` / `vendor.img` / `product.img` -> filesystem images.
 *
 * The analyzer does NOT extract files — it only walks `ZipInputStream` and
 * collects capabilities.
 */
class ZipFirmwareAnalyzer : FirmwareAnalyzer {
    override fun supports(source: FirmwareSource): Boolean = when (source) {
        is FirmwareSource.DirectPath -> source.path.lowercase().endsWith(".zip")
        is FirmwareSource.WorkspaceFile -> source.file.name.lowercase().endsWith(".zip")
        is FirmwareSource.FileNameOnly -> source.fileName.lowercase().endsWith(".zip")
    }

    override fun analyze(source: FirmwareSource): FirmwareProfile {
        val partitions = mutableListOf<PartitionInfo>()
        val warnings = mutableListOf<FirmwareWarning>()
        val compression = mutableSetOf<CompressionType>()
        var hasPayloadBin = false
        var hasSuperImage = false
        var hasBootImage = false
        var hasVendorBootImage = false
        var hasInitBootImage = false
        var hasDtboImage = false
        var hasVbmetaImage = false
        var hasExt4 = false
        var hasErofs = false
        var usesAB = false
        var androidVersion: Int? = null

        openZipStream(source).use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    when {
                        name == "payload.bin" || name.endsWith("/payload.bin") -> {
                            hasPayloadBin = true
                            // payload.bin always means A/B OTA.
                            usesAB = true
                            // Android 10+ uses payload.bin exclusively.
                            if (androidVersion == null || androidVersion!! < 10) {
                                androidVersion = 10
                            }
                        }
                        name == "super.img" || name.endsWith("/super.img") -> {
                            hasSuperImage = true
                            partitions.add(
                                PartitionInfo(
                                    name = "super",
                                    sizeBytes = entry.size.takeIf { it > 0 },
                                    isLogical = true
                                )
                            )
                        }
                        name == "boot.img" || name.endsWith("/boot.img") -> {
                            hasBootImage = true
                            partitions.add(
                                PartitionInfo(name = "boot", sizeBytes = entry.size.takeIf { it > 0 })
                            )
                        }
                        name == "vendor_boot.img" || name.endsWith("/vendor_boot.img") -> {
                            hasVendorBootImage = true
                            partitions.add(
                                PartitionInfo(name = "vendor_boot", sizeBytes = entry.size.takeIf { it > 0 })
                            )
                        }
                        name == "init_boot.img" || name.endsWith("/init_boot.img") -> {
                            hasInitBootImage = true
                            // init_boot appears in Android 13+.
                            if (androidVersion == null || androidVersion!! < 13) {
                                androidVersion = 13
                            }
                            partitions.add(
                                PartitionInfo(name = "init_boot", sizeBytes = entry.size.takeIf { it > 0 })
                            )
                        }
                        name == "dtbo.img" || name.endsWith("/dtbo.img") -> {
                            hasDtboImage = true
                            partitions.add(
                                PartitionInfo(name = "dtbo", sizeBytes = entry.size.takeIf { it > 0 })
                            )
                        }
                        name == "vbmeta.img" || name.endsWith("/vbmeta.img") ||
                        name.startsWith("vbmeta_") || name.endsWith("/vbmeta_system.img") -> {
                            hasVbmetaImage = true
                            partitions.add(
                                PartitionInfo(name = name.substringAfterLast('/'), sizeBytes = entry.size.takeIf { it > 0 })
                            )
                        }
                        name.endsWith("system.img") -> {
                            hasExt4 = true
                            partitions.add(
                                PartitionInfo(
                                    name = name.substringAfterLast('/').removeSuffix(".img"),
                                    sizeBytes = entry.size.takeIf { it > 0 },
                                    filesystem = "ext4"
                                )
                            )
                        }
                        name.endsWith("vendor.img") || name.endsWith("product.img") ||
                        name.endsWith("odm.img") || name.endsWith("system_ext.img") -> {
                            hasExt4 = true
                            partitions.add(
                                PartitionInfo(
                                    name = name.substringAfterLast('/').removeSuffix(".img"),
                                    sizeBytes = entry.size.takeIf { it > 0 },
                                    filesystem = "ext4"
                                )
                            )
                        }
                        name == "meta-inf/com/android/metadata" -> {
                            // Read up to 8 KB of metadata to detect A/B vs non-A/B.
                            val metaBytes = ByteArray(8 * 1024)
                            val read = zip.read(metaBytes)
                            if (read > 0) {
                                val meta = String(metaBytes, 0, read, Charsets.UTF_8)
                                if (meta.contains("ota-type=AB", ignoreCase = true) ||
                                    meta.contains("\"ota_type\": \"AB\"", ignoreCase = true) ||
                                    meta.contains("block-image", ignoreCase = true).not() &&
                                    meta.contains("PAYLOAD_TYPE", ignoreCase = true)
                                ) {
                                    usesAB = true
                                }
                                val versionMatch = Regex(
                                    "post-build=.*?(/\\d+/|android-)(\\d+)",
                                    RegexOption.IGNORE_CASE
                                ).find(meta)
                                versionMatch?.groupValues?.get(2)?.toIntOrNull()?.let { v ->
                                    if (androidVersion == null || androidVersion!! < v) {
                                        androidVersion = v
                                    }
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        // Build capabilities.
        val capabilities = FirmwareCapabilities(
            hasPayloadBin = hasPayloadBin,
            hasSuperImage = hasSuperImage,
            hasDynamicPartitions = hasSuperImage,
            hasSparseImages = false, // Detected by SparseImageAnalyzer.
            hasErofs = hasErofs,
            hasExt4 = hasExt4,
            hasF2fs = false,
            hasBootImage = hasBootImage,
            hasVendorBootImage = hasVendorBootImage,
            hasInitBootImage = hasInitBootImage,
            hasDtboImage = hasDtboImage,
            hasVbmetaImage = hasVbmetaImage,
            usesAvb = hasVbmetaImage,
            usesAB = usesAB,
            usesVirtualAB = false, // Detected by PayloadBinAnalyzer.
            usesCompressionZstd = compression.contains(CompressionType.ZSTD),
            usesCompressionBr = compression.contains(CompressionType.BROTLI),
            usesCompressionLz4 = compression.contains(CompressionType.LZ4),
            requires16KbAlignmentCheck = androidVersion != null && androidVersion!! >= 15
        )

        // Warnings.
        if (hasVbmetaImage && !hasBootImage) {
            warnings.add(
                FirmwareWarning(
                    code = "vbmeta-without-boot",
                    message = "vbmeta image present but no boot image — verification chain may be incomplete"
                )
            )
        }
        if (androidVersion != null && androidVersion!! >= 15) {
            warnings.add(
                FirmwareWarning(
                    code = "android-15-16kb",
                    message = "Android 15+ firmware — verify 16 KB page-size compatibility of bundled tools",
                    severity = FirmwareWarning.Severity.INFO
                )
            )
        }

        val packageType = when {
            hasPayloadBin -> FirmwarePackageType.PAYLOAD_BIN
            hasSuperImage -> FirmwarePackageType.SUPER_IMAGE
            hasBootImage -> FirmwarePackageType.BOOT_IMAGE
            else -> FirmwarePackageType.ZIP_OTA
        }

        return FirmwareProfile(
            source = source,
            packageType = packageType,
            androidVersion = AndroidVersionHint(androidVersion),
            capabilities = capabilities,
            partitions = partitions,
            compression = compression.toSet(),
            warnings = warnings
        )
    }

    private fun openZipStream(source: FirmwareSource): InputStream = when (source) {
        is FirmwareSource.DirectPath -> File(source.path).inputStream()
        is FirmwareSource.WorkspaceFile -> source.file.inputStream()
        is FirmwareSource.FileNameOnly ->
            throw FirmwareAnalysisException("Cannot read zip from FileNameOnly source")
    }
}
