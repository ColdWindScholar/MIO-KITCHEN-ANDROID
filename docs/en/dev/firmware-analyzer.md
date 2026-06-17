# Stage 7 — Firmware analyzer

> Date: 2026-06-17
> Status: completed
> Related stages: after Stage 6 (shell runtime), before Stage 8 (UI modernization)

---


Before this stage the project had no typed representation of "what is this
firmware?". The shell scripts in `tool.sh` did ad-hoc detection (`file`,
`hexdump`, `magiskboot -l`, …) every time, with results buried in stdout
strings. This made it impossible to:

- show a structured firmware summary in the UI before any action is taken;
- decide which tools are needed (lpunpack? mkfs.erofs? avbtool?);
- emit capability-driven warnings (16 KB page size, AVB chaining, …);
- test firmware detection in plain JVM tests without a device.

Stage 7 introduces a typed `FirmwareAnalyzer` API that produces a
`FirmwareProfile` — a data class with capabilities, partitions, compression
info, Android-version hints, and warnings.

### New API

```text
common/firmware/
  FirmwareProfile.kt              # FirmwareSource, FirmwarePackageType,
                                  # AndroidVersionHint, CompressionType,
                                  # PartitionInfo, FirmwareWarning,
                                  # FirmwareCapabilities, FirmwareProfile
  FirmwareAnalyzer.kt             # interface + CompositeFirmwareAnalyzer
                                  # + FirmwareAnalysisException
  ZipFirmwareAnalyzer.kt          # walks OTA zip (payload.bin, super.img,
                                  # boot.img, vbmeta.img, META-INF/metadata, …)
  ImageAnalyzers.kt               # BootImageAnalyzer, SuperImageAnalyzer,
                                  # VbmetaAnalyzer, FilesystemImageAnalyzer
  FirmwareAnalyzerRegistry.kt     # composes all analyzers in the right order
```

### Detection rules

```text
file name ends with .zip:
  -> ZipFirmwareAnalyzer
     payload.bin detected      -> packageType = PAYLOAD_BIN, usesAB = true,
                                  androidVersion >= 10
     super.img detected        -> packageType = SUPER_IMAGE, dynamic partitions
     boot.img / vendor_boot.img / init_boot.img detected
                                -> boot images; init_boot hints Android 13+
     vbmeta*.img detected      -> usesAvb = true
     system.img / vendor.img / ... detected
                                -> ext4 filesystem image
     META-INF/com/android/metadata
                                -> tries to detect A/B and Android version

file name == boot.img / vendor_boot.img / init_boot.img / recovery.img:
  -> BootImageAnalyzer
     reads first 4 KB
     ANDROID! magic at offset 0, header version at offset 40
     warns if header version is 4 (GKI compatibility)
     warns if magic is missing (proprietary header)

file name == super.img:
  -> SuperImageAnalyzer
     reads first 4 bytes
     0x3aff26ed -> sparse super image
     otherwise  -> raw super image

file name starts with vbmeta:
  -> VbmetaAnalyzer
     reads first 4 bytes
     AVB0 magic -> usesAvb = true
     otherwise  -> warns "vbmeta-bad-magic"

other .img files:
  -> FilesystemImageAnalyzer
     reads first 2 KB
     0xef53 at offset 1080 -> ext4
     0xe0f5e1e2 at offset 1024 -> EROFS
     0xf2f52010 at offset 1024 -> F2FS
     otherwise -> warns "unknown-filesystem"
```

### Architectural rules

```text
FirmwareAnalyzer
  -> fun supports(source): Boolean   -- cheap check (filename/magic)
  -> fun analyze(source): FirmwareProfile
  -> MUST NOT execute shell
  -> MUST NOT depend on android.app.* / android.widget.*
  -> safe to run on any thread
  -> all analyzers are pure JVM-testable
```

### CI gate

```bash
python3 tools/check-firmware-analyzer.py
```

Expected output:

```text
PASS: Firmware analyzer typed API is in place
PASS: FirmwareProfile/FirmwareCapabilities/PartitionInfo are declared
PASS: ZipFirmwareAnalyzer walks zip contents
PASS: BootImageAnalyzer detects ANDROID! magic and header version
PASS: SuperImageAnalyzer detects sparse format
PASS: VbmetaAnalyzer detects AVB0 magic
PASS: FilesystemImageAnalyzer detects ext4/EROFS/F2FS magic
PASS: FirmwareAnalyzerRegistry composes all analyzers
PASS: Firmware analyzer JVM unit tests are present
PASS: analyzers are pure — no shell, no Android imports
```

### What is intentionally NOT done here

- `payload.bin` manifest parsing (update_engine protobuf) — deferred to a
  later stage; we currently only flag its presence.
- `lpunpack` metadata parsing for super.img — we detect sparse vs raw, but
  do not enumerate logical partitions.
- AVB chain descriptor parsing — we only check the AVB0 magic.
- Integration with `ToolchainResolver` is done in Stage 10
  (`CapabilityBasedToolchainResolver` consumes `FirmwareProfile.capabilities`
  to build a `ToolchainPlan`). The future work is parsing the
  payload.bin / super.img internals listed above.

---
