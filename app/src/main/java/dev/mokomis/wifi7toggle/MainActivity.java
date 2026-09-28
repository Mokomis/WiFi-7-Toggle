// SPDX-License-Identifier: GPL-3.0-only
package dev.mokomis.wifi7toggle;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String CONFIG = "/mnt/vendor/persist/wlan/WCNSS_qcom_cfg.ini";
    private static final String LEGACY_BACKUP = CONFIG + ".bak";
    private static final String APP_BACKUP = CONFIG + ".wifi7toggle.stock";
    private static final String KSUD = "/data/adb/ksu/bin/ksud";
    private static final String POLICY =
            "allow ksu mnt_vendor_file dir { search read open getattr write add_name remove_name }; " +
            "allow ksu mnt_vendor_file file { read open getattr map write create setattr rename unlink };";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView details;
    private ProgressBar progress;
    private Button enable;
    private Button restore;
    private Button refresh;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView title = text("Wi-Fi 7 Toggle", 30, Color.rgb(20, 25, 34));
        root.addView(title);

        TextView subtitle = text(
                "Root utility for supported Qualcomm tablets. Changes take effect after reboot.",
                15, Color.rgb(83, 91, 105));
        subtitle.setPadding(0, dp(6), 0, dp(24));
        root.addView(subtitle);

        status = text("Checking…", 22, Color.rgb(40, 48, 60));
        root.addView(status);

        details = text("", 14, Color.rgb(90, 98, 112));
        details.setPadding(0, dp(8), 0, dp(22));
        root.addView(details);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(38), dp(38));
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.setMargins(0, 0, 0, dp(18));
        root.addView(progress, pp);

        enable = button("Enable Wi-Fi 7");
        restore = button("Restore stock Wi-Fi setting");
        refresh = button("Refresh status");
        root.addView(enable);
        root.addView(restore);
        root.addView(refresh);

        TextView warning = text(
                "The app refuses to write unless it finds exactly one recognized BandCapability setting and a matching stock backup. It does not change the regulatory country code.",
                13, Color.rgb(105, 78, 50));
        warning.setPadding(0, dp(24), 0, 0);
        root.addView(warning);

        setContentView(root);
        enable.setOnClickListener(v -> confirm(true));
        restore.setOnClickListener(v -> confirm(false));
        refresh.setOnClickListener(v -> refresh());
        refresh();
    }

    private void confirm(boolean turnOn) {
        String action = turnOn ? "enable Wi-Fi 7" : "restore the original Wi-Fi configuration";
        new AlertDialog.Builder(this)
                .setTitle(turnOn ? "Enable Wi-Fi 7?" : "Restore stock setting?")
                .setMessage("This will " + action + ". The tablet must reboot afterward.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> apply(turnOn))
                .show();
    }

    private void refresh() {
        setBusy(true);
        executor.execute(() -> {
            Result result = runRoot(inspectScript());
            runOnUiThread(() -> {
                setBusy(false);
                showResult(result);
            });
        });
    }

    private void apply(boolean turnOn) {
        setBusy(true);
        executor.execute(() -> {
            Result result = runRoot(changeScript(turnOn));
            runOnUiThread(() -> {
                setBusy(false);
                showResult(result);
                if (result.exitCode == 0) {
                    new AlertDialog.Builder(this)
                            .setTitle("Change saved")
                            .setMessage("Reboot now to reload the Wi-Fi driver?")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Reboot", (d, w) -> executor.execute(
                                    () -> runRoot("reboot")))
                            .show();
                }
            });
        });
    }

    private String policyPrefix() {
        String quoted = shellQuote(POLICY);
        return "if [ -x " + KSUD + " ]; then " + KSUD + " sepolicy patch " + quoted + "; fi; ";
    }

    private String inspectScript() {
        return policyPrefix()
                + "f=" + CONFIG + "; "
                + "[ -f \"$f\" ] || { echo 'ERROR|Unsupported: configuration file not found'; exit 20; }; "
                + "count=$(grep -c '^[[:space:]]*BandCapability=[37][[:space:]]*$' \"$f\"); "
                + "[ \"$count\" = 1 ] || { echo 'ERROR|Unsupported or ambiguous BandCapability setting'; exit 21; }; "
                + "value=$(sed -n 's/^[[:space:]]*BandCapability=\\([37]\\)[[:space:]]*$/\\1/p' \"$f\"); "
                + "backup=missing; [ -f " + APP_BACKUP + " ] && backup=app; "
                + "[ \"$backup\" = missing ] && [ -f " + LEGACY_BACKUP + " ] && backup=legacy; "
                + "echo STATUS\\|$value\\|$backup";
    }

    private String changeScript(boolean turnOn) {
        String sourceValue = turnOn ? "7" : "3";
        String backupSelection =
                "stock=''; [ -f " + APP_BACKUP + " ] && stock=" + APP_BACKUP + "; " +
                "[ -z \"$stock\" ] && [ -f " + LEGACY_BACKUP + " ] && stock=" + LEGACY_BACKUP + "; ";
        String validateBackup =
                "[ -n \"$stock\" ] || { echo 'ERROR|No stock backup is available'; exit 31; }; " +
                "[ \"$(grep -c '^[[:space:]]*BandCapability=3[[:space:]]*$' \"$stock\")\" = 1 ] " +
                "|| { echo 'ERROR|Stock backup is invalid'; exit 32; }; ";
        String establishBackup =
                "if [ ! -f " + APP_BACKUP + " ]; then " +
                "cp -p \"$stock\" " + APP_BACKUP + " || { echo 'ERROR|Could not preserve stock backup'; exit 33; }; " +
                "chcon u:object_r:mnt_vendor_file:s0 " + APP_BACKUP + "; fi; ";
        String selectInput = turnOn ? "input=\"$f\"; " : "input=" + APP_BACKUP + "; ";

        return policyPrefix()
                + "f=" + CONFIG + "; tmp=/data/local/tmp/wifi7toggle.$$; trap 'rm -f \"$tmp\"' EXIT; "
                + "[ -f \"$f\" ] || { echo 'ERROR|Unsupported: configuration file not found'; exit 30; }; "
                + backupSelection + validateBackup + establishBackup + selectInput
                + "sed 's/^[[:space:]]*BandCapability=[37][[:space:]]*$/BandCapability=" + sourceValue + "/' \"$input\" > \"$tmp\" "
                + "|| { echo 'ERROR|Could not prepare patched configuration'; exit 34; }; "
                + "[ \"$(grep -c '^BandCapability=" + sourceValue + "$' \"$tmp\")\" = 1 ] "
                + "|| { echo 'ERROR|Patched configuration failed validation'; exit 35; }; "
                + "cat \"$tmp\" > \"$f\" || { echo 'ERROR|Could not write configuration'; exit 36; }; "
                + "chown 1000:1010 \"$f\"; chmod 0666 \"$f\"; chcon u:object_r:mnt_vendor_file:s0 \"$f\"; sync; "
                + "echo STATUS\\|" + sourceValue + "\\|app";
    }

    private void showResult(Result result) {
        String line = result.lastTaggedLine();
        if (result.exitCode != 0 || line == null) {
            status.setText("Unable to change Wi-Fi configuration");
            status.setTextColor(Color.rgb(175, 48, 48));
            details.setText(line != null && line.startsWith("ERROR|")
                    ? line.substring(6) : "Check KernelSU permission and device compatibility.\n" + result.output);
            enable.setEnabled(false);
            restore.setEnabled(false);
            return;
        }
        String[] parts = line.split("\\|", -1);
        boolean on = parts.length > 1 && "7".equals(parts[1]);
        String backup = parts.length > 2 ? parts[2] : "missing";
        status.setText(on ? "Wi-Fi 7 is enabled" : "Stock Wi-Fi setting is active");
        status.setTextColor(on ? Color.rgb(22, 128, 82) : Color.rgb(48, 78, 124));
        details.setText("BandCapability=" + (on ? "7" : "3") + "\nStock backup: "
                + ("missing".equals(backup) ? "not found" : "verified"));
        enable.setEnabled(!on && !"missing".equals(backup));
        restore.setEnabled(on && !"missing".equals(backup));
    }

    private Result runRoot(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            return new Result(process.waitFor(), output.toString().trim());
        } catch (Throwable error) {
            return new Result(-1, error.toString());
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? ProgressBar.VISIBLE : ProgressBar.GONE);
        enable.setEnabled(!busy);
        restore.setEnabled(!busy);
        refresh.setEnabled(!busy);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        String lastTaggedLine() {
            String found = null;
            for (String line : output.split("\\n")) {
                if (line.startsWith("STATUS|") || line.startsWith("ERROR|")) found = line;
            }
            return found;
        }
    }
}
