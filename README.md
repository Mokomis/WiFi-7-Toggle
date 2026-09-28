# Wi-Fi 7 Toggle for OPPO Pad Mini

Wi-Fi 7 Toggle is a small root utility for the **OPPO Pad Mini OPD2515** on ColorOS 16. It enables or restores the Qualcomm Wi-Fi band capability setting that controls access to the tablet's 6 GHz/Wi-Fi 7 support.

> [!WARNING]
> This app writes to the persistent vendor Wi-Fi configuration. It is intended for the OPPO Pad Mini OPD2515 configuration described below. Root access is mandatory. Keep the stock backup, do not reboot after an unexpected error, and use this software at your own risk.

## What it does

The tablet's Qualcomm configuration is stored at:

```text
/mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini
```

The app changes exactly one recognized line:

```text
BandCapability=3  # stock: 2.4 GHz and 5 GHz
BandCapability=7  # enables 2.4 GHz, 5 GHz, and 6 GHz capability
```

It does **not** change the Wi-Fi country code, regulatory database, firmware, kernel, or Android system partition. A reboot is required before the Wi-Fi driver loads a newly selected value.

## Safety behavior

Before allowing a change, the app:

- Requests a root shell through `su`.
- Applies a narrow KernelSU SELinux rule for the `mnt_vendor_file` label when KernelSU's `ksud` is available.
- Confirms the expected Qualcomm configuration file exists.
- Requires exactly one `BandCapability=3` or `BandCapability=7` line.
- Requires a valid stock backup containing exactly one `BandCapability=3` line.
- Preserves its own rollback copy as `WCNSS_qcom_cfg.ini.wifi7toggle.stock`.
- Writes through a temporary file and validates the result before replacing the live contents.
- Restores the tested owner (`system:wifi`), mode (`0666`), and SELinux label (`u:object_r:mnt_vendor_file:s0`).
- Offers a reboot only after a successful write. Rebooting is never automatic.

The original backup used on the tested tablet is:

```text
/mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini.bak
```

## Runtime requirements

All of the following are required:

- **OPPO Pad Mini OPD2515** or a genuinely compatible Qualcomm device with the same file, setting, ownership, permissions, and SELinux label.
- **Android 9 or newer**; tested on ColorOS 16 / Android 16.
- **An unlocked and rooted tablet.** The app cannot work on a stock, unrooted device.
- A working `su` implementation.
- **KernelSU:** tested with KernelSU Manager 3.2.5 / kernel component 32525-2. After installation, open KernelSU Manager, select **Superuser**, open **Wi-Fi 7 Toggle**, and enable **Superuser** access.
- **Magisk:** not tested. The app can invoke a standard Magisk `su`, but its KernelSU-specific SELinux helper is unavailable under Magisk. It will work only if the resulting Magisk root domain can already read and write the persistent vendor file safely.
- The exact configuration file and a valid stock backup described above.
- A reboot after enabling or restoring, so the Qualcomm Wi-Fi driver reloads the setting.

No network connection or external service is required at runtime.

## Installation and use

1. Download the APK from the GitHub release.
2. Install it on the rooted tablet. Android may ask you to allow installation from the source you used.
3. In KernelSU Manager, grant **Wi-Fi 7 Toggle** Superuser access.
4. Open the app and confirm it reports either **Wi-Fi 7 is enabled** or **Stock Wi-Fi setting is active**, with **Stock backup: verified**.
5. Choose **Enable Wi-Fi 7** or **Restore stock Wi-Fi setting**.
6. Review the confirmation, apply the change, and reboot when you are ready.

Changing the file does not immediately restart Wi-Fi. The new setting takes effect on the next reboot. If the tablet currently depends on a 6 GHz connection, restoring stock and then rebooting can disconnect it from that network.

## Recovery

If the tablet still boots but the app cannot be used, restore the validated stock backup from a root shell and reboot:

```sh
su
cp -p /mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini.bak \
  /mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini
chown 1000:1010 /mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini
chmod 0666 /mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini
chcon u:object_r:mnt_vendor_file:s0 \
  /mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini
sync
reboot
```

Do not copy these values blindly to a different device. Verify its expected metadata first.

## Verification status

Tested on an OPPO Pad Mini OPD2515:

- KernelSU root authorization: verified.
- SELinux policy injection and configuration access: verified.
- Status detection for `BandCapability=7`: verified.
- Stock-backup detection and validation: verified.
- Restore action from `7` to `3`: verified without rebooting.
- File owner, mode, SELinux label, and size preservation: verified.
- Recognition of a subsequently restored `BandCapability=7`: verified.

The in-app **Enable Wi-Fi 7** write and **Reboot** button were intentionally not exercised during the final connected test, because rebooting or leaving the tablet on the stock value could interrupt the active 6 GHz connection. Both use the same validated write/root path as the tested restore operation.

## Building from source

Build dependencies:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0 or compatible
- Gradle 8.11.1 (included through the Gradle wrapper)
- Android Gradle Plugin 8.9.1 (downloaded by Gradle)
- Internet access for the first dependency download

Build the debug APK:

```sh
./gradlew assembleDebug
```

The output is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The project uses only Android platform APIs and has no third-party runtime libraries.

## Release signing

The initial downloadable APK is the exact debug-signed build tested on the tablet. Android debug signing is suitable for testing and sideloading, but not for Play Store distribution or a long-term production update channel. Future production releases should use a securely stored project-specific signing key.

## Scope

This project exposes an already present Qualcomm capability. It does not guarantee that every access point, region, firmware version, or nominally similar tablet will permit 6 GHz operation. Users remain responsible for complying with applicable radio regulations.

## License

Copyright 2026 Mokomis. Licensed under the [GNU General Public License v3.0](LICENSE). You may use, study, modify, and redistribute the software; distributors of modified versions must provide the corresponding source under GPLv3. Release APKs link to the complete source for their tagged version in this repository.
