package com.omarea.common.toolchain

import com.omarea.common.firmware.CompressionType
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.runtime.DeviceProfile

/**
 * RU: Реализация [ToolchainResolver], которая строит [ToolchainPlan] по
 *     capabilities прошивки и параметрам устройства.
 *
 * Алгоритм:
 *   1. Определить набор [ToolRequirement] для операции (через [requirementsFor]).
 *   2. Выбрать ABI устройства, который поддерживается максимальным числом
 *      инструментов.
 *   3. Проверить, какие инструменты доступны в манифесте.
 *   4. Сформировать предупреждения (16 KB page size, ABI-несовместимость, …).
 *   5. Пометить `ready = true`, если все обязательные инструменты присутствуют
 *      и ABI совместим.
 *
 * EN: [ToolchainResolver] implementation that builds a [ToolchainPlan] from
 *     the firmware's capabilities and device parameters.
 *
 * Algorithm:
 *   1. Determine the [ToolRequirement] set for the operation (via [requirementsFor]).
 *   2. Pick the device ABI that the maximum number of tools support.
 *   3. Check which tools are present in the manifest.
 *   4. Emit warnings (16 KB page size, ABI mismatch, …).
 *   5. Set `ready = true` if all required tools are present and ABI is compatible.
 */
class CapabilityBasedToolchainResolver(
    private val manifest: List<ToolDescriptor> = emptyList()
) : ToolchainResolver {

    override fun resolve(
        operation: FirmwareOperation,
        firmware: FirmwareProfile,
        device: DeviceProfile
    ): ToolchainPlan {
        val requirements = requirementsFor(operation, firmware)
        val required = requirements.filter { it.required }
        val optional = requirements.filterNot { it.required }

        val selectedAbi = selectAbi(device, requirements)
        val available = manifest.map { it.name }.toSet()

        val warnings = mutableListOf<ToolchainWarning>()

        // 16 KB page size check.
        if (device.isAndroid15Plus || device.supports16KbPageSize == true) {
            val incompatible16Kb = manifest.filter { descriptor ->
                descriptor.supports16KbPageSize == false &&
                    required.any { it.name == descriptor.name }
            }
            for (desc in incompatible16Kb) {
                warnings.add(
                    ToolchainWarning(
                        code = CODE_16KB_INCOMPATIBLE,
                        message = "Tool ${desc.name} does not support 16 KB page size " +
                            "but is required on this device.",
                        severity = ToolchainWarning.Severity.ERROR
                    )
                )
            }
        }

        // Compression checks.
        val caps = firmware.capabilities
        if (caps.usesCompressionZstd && !available.any { it.startsWith("zstd") }) {
            warnings.add(
                ToolchainWarning(
                    code = CODE_MISSING_COMPRESSION_TOOL,
                    message = "Firmware uses zstd compression but no zstd tool is in the manifest",
                    severity = ToolchainWarning.Severity.WARNING
                )
            )
        }
        if (caps.usesCompressionBr && !available.any { it.startsWith("brotli") }) {
            warnings.add(
                ToolchainWarning(
                    code = CODE_MISSING_COMPRESSION_TOOL,
                    message = "Firmware uses brotli compression but no brotli tool is in the manifest",
                    severity = ToolchainWarning.Severity.WARNING
                )
            )
        }

        // ABI mismatch check.
        if (selectedAbi.isEmpty()) {
            warnings.add(
                ToolchainWarning(
                    code = CODE_NO_COMPATIBLE_ABI,
                    message = "No common ABI between device (${device.abiList.joinToString(",")}) " +
                        "and required tools",
                    severity = ToolchainWarning.Severity.ERROR
                )
            )
        }

        val missingRequired = required
            .filter { req -> req.name !in available }
            .map { it.name }

        val ready = missingRequired.isEmpty() &&
            selectedAbi.isNotEmpty() &&
            warnings.none { it.severity == ToolchainWarning.Severity.ERROR }

        val profile = ToolchainProfile(
            requiredTools = required,
            optionalTools = optional,
            selectedAbi = selectedAbi,
            toolsDir = "", // populated at runtime by ToolchainInstaller (future stage)
            verifiedChecksums = false
        )

        return ToolchainPlan(
            profile = profile,
            availableTools = available,
            missingRequired = missingRequired,
            warnings = warnings,
            ready = ready
        )
    }

    /**
     * RU: Возвращает список [ToolRequirement] для операции [operation] с учётом
     *     capabilities прошивки [firmware].
     *
     * EN: Returns the list of [ToolRequirement] for [operation] considering the
     *     [firmware]'s capabilities.
     */
    internal fun requirementsFor(
        operation: FirmwareOperation,
        firmware: FirmwareProfile
    ): List<ToolRequirement> {
        val caps = firmware.capabilities
        val reqs = mutableListOf<ToolRequirement>()

        when (operation) {
            FirmwareOperation.UNPACK_ROM, FirmwareOperation.INSPECT -> {
                // General ROM inspection: zip + busybox + maybe payload/super.
                reqs += tool("busybox", purpose = ToolPurpose.BUSYBOX, abi = ALL_ABIS, required = true)
                if (caps.hasPayloadBin) {
                    reqs += tool("payload-dumper", purpose = ToolPurpose.PAYLOAD_BIN, required = true)
                }
                if (caps.hasSuperImage) {
                    reqs += tool("lpunpack", purpose = ToolPurpose.SUPER_IMAGE, required = true)
                }
                if (caps.hasBootImage || caps.hasVendorBootImage || caps.hasInitBootImage) {
                    reqs += tool("magiskboot", purpose = ToolPurpose.BOOT_IMAGE, required = true)
                }
                if (caps.usesAvb) {
                    reqs += tool("avbtool", purpose = ToolPurpose.VBMETA, required = false)
                }
            }
            FirmwareOperation.UNPACK_SUPER, FirmwareOperation.PACK_SUPER -> {
                reqs += tool("lpunpack", purpose = ToolPurpose.SUPER_IMAGE, required = operation == FirmwareOperation.UNPACK_SUPER)
                reqs += tool("lpmake", purpose = ToolPurpose.SUPER_IMAGE, required = operation == FirmwareOperation.PACK_SUPER)
                reqs += tool("simg2img", purpose = ToolPurpose.SPARSE, required = false)
                reqs += tool("img2simg", purpose = ToolPurpose.SPARSE, required = false)
            }
            FirmwareOperation.UNPACK_PAYLOAD_BIN -> {
                reqs += tool("payload-dumper", purpose = ToolPurpose.PAYLOAD_BIN, required = true)
            }
            FirmwareOperation.UNPACK_BOOT_IMAGE,
            FirmwareOperation.UNPACK_VENDOR_BOOT_IMAGE,
            FirmwareOperation.UNPACK_INIT_BOOT_IMAGE,
            FirmwareOperation.PACK_BOOT_IMAGE -> {
                reqs += tool("magiskboot", purpose = ToolPurpose.BOOT_IMAGE, required = true)
                reqs += tool("mkbootimg", purpose = ToolPurpose.BOOT_IMAGE, required = operation.name.startsWith("PACK"))
            }
            FirmwareOperation.UNPACK_DTBO_IMAGE -> {
                reqs += tool("magiskboot", purpose = ToolPurpose.BOOT_IMAGE, required = true)
            }
            FirmwareOperation.UNPACK_FILESYSTEM_IMAGE -> {
                when {
                    caps.hasErofs -> {
                        reqs += tool("dump.erofs", purpose = ToolPurpose.EROFS, required = true)
                        reqs += tool("fsck.erofs", purpose = ToolPurpose.EROFS, required = false)
                    }
                    caps.hasExt4 -> {
                        reqs += tool("e2fsdroid", purpose = ToolPurpose.EXT4, required = false)
                        reqs += tool("resize2fs", purpose = ToolPurpose.EXT4, required = false)
                    }
                    else -> {
                        reqs += tool("dump.erofs", purpose = ToolPurpose.EROFS, required = false)
                        reqs += tool("e2fsdroid", purpose = ToolPurpose.EXT4, required = false)
                    }
                }
            }
            FirmwareOperation.PACK_FILESYSTEM_IMAGE -> {
                when {
                    caps.hasErofs -> reqs += tool("mkfs.erofs", purpose = ToolPurpose.EROFS, required = true)
                    caps.hasExt4 -> {
                        reqs += tool("mke2fs", purpose = ToolPurpose.EXT4, required = true)
                        reqs += tool("e2fsdroid", purpose = ToolPurpose.EXT4, required = true)
                    }
                    else -> {
                        // Default to ext4 for packing if filesystem is unknown.
                        reqs += tool("mke2fs", purpose = ToolPurpose.EXT4, required = true)
                        reqs += tool("e2fsdroid", purpose = ToolPurpose.EXT4, required = true)
                    }
                }
            }
            FirmwareOperation.VERIFY_VBMETA -> {
                reqs += tool("avbtool", purpose = ToolPurpose.VBMETA, required = true)
            }
            FirmwareOperation.FLASH_PREPARE -> {
                reqs += tool("busybox", purpose = ToolPurpose.BUSYBOX, required = true)
                if (caps.hasBootImage || caps.hasVendorBootImage) {
                    reqs += tool("magiskboot", purpose = ToolPurpose.BOOT_IMAGE, required = false)
                }
            }
        }

        // Compression tools.
        if (caps.usesCompressionBr) {
            reqs += tool("brotli", purpose = ToolPurpose.COMPRESSION_BROTLI, required = false)
        }
        if (caps.usesCompressionLz4) {
            reqs += tool("lz4", purpose = ToolPurpose.COMPRESSION_LZ4, required = false)
        }
        if (caps.usesCompressionZstd) {
            reqs += tool("zstd", purpose = ToolPurpose.COMPRESSION_ZSTD, required = false)
        }

        return reqs
    }

    private fun selectAbi(
        device: DeviceProfile,
        requirements: List<ToolRequirement>
    ): String {
        if (device.abiList.isEmpty()) return ""
        if (requirements.isEmpty()) return device.abiList.first()
        // Pick the device ABI supported by the maximum number of tools.
        return device.abiList
            .maxByOrNull { abi ->
                requirements.count { req -> req.abi.isEmpty() || abi in req.abi }
            } ?: ""
    }

    private fun tool(
        name: String,
        purpose: ToolPurpose,
        abi: List<String> = ALL_ABIS,
        required: Boolean = true
    ): ToolRequirement = ToolRequirement(
        name = name,
        purpose = purpose,
        abi = abi,
        required = required
    )

    companion object {
        const val CODE_16KB_INCOMPATIBLE = "16kb-incompatible"
        const val CODE_MISSING_COMPRESSION_TOOL = "missing-compression-tool"
        const val CODE_NO_COMPATIBLE_ABI = "no-compatible-abi"

        val ALL_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
}
