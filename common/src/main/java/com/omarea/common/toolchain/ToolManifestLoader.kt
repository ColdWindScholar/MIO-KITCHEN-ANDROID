package com.omarea.common.toolchain

import com.omarea.common.firmware.CompressionType
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.runtime.DeviceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * RU: Загружает [ToolDescriptor] из JSON-манифеста.
 *
 * Формат манифеста (см. `assets/toolchain/manifest.json`):
 *
 * ```json
 * {
 *   "tools": [
 *     {
 *       "name": "lpunpack",
 *       "version": "android-tools-r34",
 *       "abi": ["arm64-v8a", "armeabi-v7a"],
 *       "sha256": "...",
 *       "capabilities": ["super_image"],
 *       "source": "AOSP",
 *       "license": "Apache-2.0",
 *       "supports16KbPageSize": true
 *     }
 *   ]
 * }
 * ```
 *
 * EN: Loads [ToolDescriptor] from a JSON manifest.
 *
 * Manifest format (see `assets/toolchain/manifest.json`):
 *
 * ```json
 * {
 *   "tools": [ { ... } ]
 * }
 * ```
 */
class ToolManifestLoader {
    /**
     * RU: Загружает дескрипторы из [stream]. Поток НЕ закрывается.
     * EN: Loads descriptors from [stream]. The stream is NOT closed.
     */
    fun load(stream: InputStream): List<ToolDescriptor> {
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text)
    }

    /**
     * RU: Парсит манифест из строки JSON.
     * EN: Parses the manifest from a JSON string.
     */
    fun parse(text: String): List<ToolDescriptor> {
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        val toolsArray = root.optJSONArray("tools") ?: return emptyList()
        val result = mutableListOf<ToolDescriptor>()
        for (i in 0 until toolsArray.length()) {
            val obj = toolsArray.getJSONObject(i)
            result.add(parseDescriptor(obj))
        }
        return result
    }

    private fun parseDescriptor(obj: JSONObject): ToolDescriptor {
        val name = obj.getString("name")
        val version = obj.getString("version")
        val abi = obj.optJSONArray("abi")?.toStringList() ?: emptyList()
        val sha256 = obj.optString("sha256", null)
        val caps = obj.optJSONArray("capabilities")?.toStringList()?.map { parsePurpose(it) } ?: emptyList()
        val source = obj.optString("source", "unknown")
        val license = obj.optString("license", "unknown")
        val supports16Kb = if (obj.has("supports16KbPageSize")) obj.getBoolean("supports16KbPageSize") else null
        return ToolDescriptor(
            name = name,
            version = version,
            abi = abi,
            sha256 = sha256,
            capabilities = caps,
            source = source,
            license = license,
            supports16KbPageSize = supports16Kb
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until length()) list.add(getString(i))
        return list
    }

    private fun parsePurpose(value: String): ToolPurpose = try {
        ToolPurpose.valueOf(value.uppercase().replace('-', '_'))
    } catch (_: IllegalArgumentException) {
        ToolPurpose.GENERIC
    }
}
