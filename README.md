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

## After enabling: channel width is not fixed

Once the tablet is on a 6 GHz Wi-Fi 7 network, expect its reported link speed to move around a great deal. This is the Wi-Fi firmware's own behaviour, not a fault in the connection and not something this app changes. It was measured on one OPD2515 on a TP-Link Deco mesh on October 5, 2026, using the firmware's diagnostic log.

**What the firmware does.** On a multi-link (MLO) association, even one that uses a single 6 GHz link, the firmware's multi-link power-save module chooses the receive width every 100 ms from how much of the channel's airtime the tablet's own traffic fills. It then asks the access point to follow, and the access point complies.

| Traffic to the tablet | Width chosen |
|---|---|
| 0 to 100 Mb/s | 80 MHz |
| 200 Mb/s | 80 MHz, asking for more about a fifth of the time |
| 400 Mb/s | flips between 80 and 320 MHz about twice a second |
| about 1,100 Mb/s | 320 MHz, steady |

**What follows from that.**

- A link speed of 600 to 1,200 Mbps at 80 MHz on an idle tablet is normal. The width only shows under sustained load. The uplink figure stays at 320 MHz throughout.
- The firmware also rests one antenna when nearly idle, so the two antennas can report signal levels 10 dB or more apart. That is power saving, not a weak antenna.
- A steady load of roughly 300 to 700 Mb/s is the awkward range: the link changes width constantly. Either well below it or well above it is steadier.
- A short speed test can under-read. Bursts shorter than the firmware's 100 ms decision interval finish before the link has widened.

**Settings tried in the same configuration file, none of which stops it.** Each was added before the `END` line of `/mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini`, followed by a reboot, then removed again.

| Setting | Result |
|---|---|
| `gEnableBmps=0`, `gEnableImps=0` | Both antennas stay on at idle; the width still switches |
| `gDtim1ChRxEnable=0`, `enable_dynamic_nss_chain_config=0`, `gRuntimePM=0` | No visible effect |
| `mlo_support_link_band=0x33` | **Breaks the connection.** The access point rejected every association attempt on 6 GHz |
| `gDot11Mode=10` | **Breaks 6 GHz.** The tablet stopped seeing 6 GHz networks at all |

`dynamic_bw_switch` looks like the obvious candidate and is not: it governs how the tablet transmits when a neighbouring network is busy. Asking Android for low-latency Wi-Fi does not stop the switching either; the firmware was already in its highest latency mode while it happened. The firmware's message catalogue shows no enable or disable control for the width decision.

On the router side, turning the Deco's separate MLO network off removed that network but left the 6 GHz network multi-link, so it changed nothing here. Other access points may differ.

**If the tablet is on a mesh.** It sometimes joined the far node at a weak signal (4 of about 21 connections), and an app holding a low-latency Wi-Fi lock kept it there. Setting the tablet's preferred node on the router fixed that: in the Deco app, **Online Clients**, the tablet, **Specified Connection**. After that, 22 of 22 connections went to the near node.

**Not explained.** On one day the link stayed at 160 MHz under a 1.1 Gb/s load for six connections in a row, on the right node at a strong signal. It did not recur the next day. The module also weighs other networks' airtime on the channel, which may be related; that was not established.

## Scope

This project exposes an already present Qualcomm capability. It does not guarantee that every access point, region, firmware version, or nominally similar tablet will permit 6 GHz operation. Users remain responsible for complying with applicable radio regulations.

## Other tools for this tablet

Separate root utilities for the OPPO Pad Mini OPD2515. Each works by itself.

- [Refresh Manager](https://github.com/Mokomis/opd2515-refresh-manager): lets any app use 144 Hz, or locks an app to 60, 120 or 144 Hz.
- [GPU Clock Floor](https://github.com/Mokomis/adreno-clock-floor): holds the Adreno GPU clock at a chosen minimum, for steadier GPU work such as video decode while streaming.
- [Low-latency audio](https://github.com/Mokomis/opd2515-low-latency-audio): documents how ColorOS keeps apps off the low-latency audio paths, with a script to allow chosen apps.

## License

Copyright 2026 Mokomis. Licensed under the [GNU General Public License v3.0](LICENSE). You may use, study, modify, and redistribute the software; distributors of modified versions must provide the corresponding source under GPLv3. Release APKs link to the complete source for their tagged version in this repository.
