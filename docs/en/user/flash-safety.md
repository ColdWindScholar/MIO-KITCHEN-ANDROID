# Flash safety

**Read this entire page before running any flash operation.**

Flash operations are the only destructive operations in MIO-KITCHEN. They
write firmware images to your device's partitions — a mistake can brick the
device.

## What flash does

`FLASH_PREPARE` is the only flash operation currently typed in
`FirmwareOperation`. It does **not** actually flash — it produces a
ready-to-run flash script that you can review before executing.

The actual flash is done by you, manually, using `fastboot` or `dd` from a
recovery shell. MIO-KITCHEN never writes to partitions automatically.

## Safety profile

```text
isDestructive           = true
requiresBackup          = true
requiresRoot            = true
requiresDeviceConnection= true
supportsDryRun          = true
confirmationLevel       = DESTRUCTIVE
```

Because `confirmationLevel = DESTRUCTIVE`, the UI requires you to type
`FLASH` (or equivalent) into a text field before the operation runs. There is
no accidental one-tap flash.

## Pre-flight checklist

Before running `FLASH_PREPARE`, verify:

1. **Device is connected** via ADB or fastboot. The plan's `requiresDeviceConnection`
   flag is enforced — the executor refuses to run otherwise.
2. **Root is available**. Flash without root is impossible; the executor will
   block the plan if `device.hasRoot != true`.
3. **Backup exists**. Take a full backup of the current firmware with `dd` or
   TWRP before flashing anything.
4. **Battery is charged** to at least 50%. A power loss during flash bricks
   the device.
5. **Bootloader is unlocked** (for `boot` / `vbmeta` partitions). AVB will
   reject signed images on a locked bootloader.
6. **The image matches your device**. Flashing a `system.img` from a
   different model will brick it.

## Dry-run first

Always run `FLASH_PREPARE` with `dryRun = true` first. The executor uses
`DryRunShellRuntime`, which prints the planned commands without running them.
Review the printed commands — they should match what you would type manually.

## The flash script

The flash script is a shell script (`flash.sh`) that contains:
- `fastboot flash` commands for each partition.
- `dd` fallbacks for partitions that cannot be flashed via fastboot.
- A final `fastboot reboot` (commented out by default).

The script is written to the workspace exports dir. **Review it line by line
before running it.**

## What to do if flash fails

1. **Do not reboot.** A failed flash often leaves the device in an
   intermediate state that can be recovered.
2. **Reflash the failed partition** with the original image from your backup.
3. **If the device is in a boot loop**, boot to recovery (TWRP) and restore
   the full backup.
4. **If the device does not respond at all**, you may need EDL mode
   (Qualcomm) or BROM mode (MediaTek) to recover — beyond MIO-KITCHEN's
   scope.

## What MIO-KITCHEN does NOT do

- It does not auto-flash. There is no "Flash now" button.
- It does not detect whether the image matches your device. You must verify.
- It does not modify the bootloader lock state. Use `fastboot oem unlock`
  (or vendor-specific equivalent) manually.
- It does not back up the current firmware. Use `dd` or TWRP first.

## Next steps

- [Root mode](root-mode.md) — how root is detected and used.
- [Troubleshooting](troubleshooting.md) — common flash failures.
