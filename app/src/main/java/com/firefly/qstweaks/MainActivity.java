package com.firefly.qstweaks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 主界面：权限一键授予 + 5G 开关 + 息屏两档 + 诊断入口
 */
public class MainActivity extends Activity {

    private Switch sw5g;
    private Switch swGuard;
    private TextView tvA11y;
    private TextView tvScreen;
    private LinearLayout stepBox;
    private volatile boolean rootOk = false;
    private volatile boolean suppressSwitch = false;
    private volatile boolean suppressGuard = false;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle(R.string.app_name);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        root.addView(h1("状态"));
        stepBox = new LinearLayout(this);
        stepBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(stepBox);

        root.addView(h1("5G"));
        sw5g = new Switch(this);
        sw5g.setText("允许 5G（NR）");
        sw5g.setOnCheckedChangeListener((v, checked) -> {
            if (suppressSwitch) return;
            runCmd(Cmd.g5(checked ? LteTarget.G5_ON : LteTarget.G5_OFF));
        });
        root.addView(sw5g);
        root.addView(button("切到 5G 优先", () -> runCmd(Cmd.g5(LteTarget.G5_ON))));
        root.addView(button("切到 4G 优先（关 5G）", () -> runCmd(Cmd.g5(LteTarget.G5_OFF))));

        root.addView(h1("息屏时间"));
        tvScreen = new TextView(this);
        root.addView(tvScreen);
        for (final ScreenStep s : ScreenStep.values()) {
            root.addView(button(s.label, () -> runScreen(s)));
        }

        root.addView(h1("权限 / 系统"));
        root.addView(button("用 Root 授予全部权限", this::grantAll));
        root.addView(button("打开控制中心编辑（加磁贴）", this::openQsEdit));
        root.addView(button("系统设置写入权限（备用）", this::openWriteSettings));
        root.addView(button("诊断（查看网络类型 / 排查问题）",
                () -> startActivity(new Intent(this, DebugActivity.class))));

        swGuard = new Switch(this);
        swGuard.setText("息屏守护（后台核对并纠正息屏时间，无通知）");
        swGuard.setChecked(ScreenGuardService.isEnabled(this));
        swGuard.setOnCheckedChangeListener((v, on) -> {
            if (suppressGuard) return;
            ScreenGuardService.setEnabled(getApplicationContext(), on);
            // 开着守护却没开无障碍 → 自己去开一下成功率更高
            if (on && !GuardAccessibilityService.isEnabled(this)) {
                android.widget.Toast.makeText(this,
                        "建议同时开启「无障碍保活」，否则划掉后台后仍会被杀",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(h1("息屏守护"));
        root.addView(swGuard);

        tvA11y = new TextView(this);
        root.addView(tvA11y);
        root.addView(button("开启无障碍保活", () -> {
            if (!GuardAccessibilityService.openSettings(this)) {
                android.widget.Toast.makeText(this,
                        "请手动：系统设置 → 无障碍 → 已下载的服务 → QS Tweaks",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }));

        TextView tip = new TextView(this);
        tip.setPadding(0, dp(20), 0, dp(8));
        tip.setText("使用说明\n"
                + "1. 先在 LSPosed 里启用本模块，作用域勾选「系统界面(SystemUI)」，重启手机或 SystemUI。\n"
                + "2. 下拉控制中心 → 编辑，把「5G 开关」「息屏时间」拖到顺手的位置。\n"
                + "3. 5G 开关改的是「首选网络类型」，2~5 秒生效。\n"
                + "4. 息屏只有 15 秒 / 5 分钟两档，磁贴点一下切换。\n"
                + "   ColorOS 会在后台把值改回默认，所以开着「息屏守护」自动纠正，\n"
                + "   它本身不弹通知；但要有「无障碍保活」托着才能在划掉后台后继续工作。\n"
                + "5. 磁贴不出现/不生效时先看「诊断」页。");
        root.addView(tip);

        setContentView(sv);

        if (android.os.Build.VERSION.SDK_INT >= 35) {
            final View content = root;
            content.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
                return insets;
            });
            content.requestApplyInsets();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 隐藏后台卡片：离开界面就把本任务从最近任务里移除，进程本身继续活着
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<android.app.ActivityManager.AppTask> tasks = am.getAppTasks();
                if (tasks != null && !tasks.isEmpty()) {
                    tasks.get(0).setExcludeFromRecents(true);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        new Thread(() -> {
            final boolean g5 = Executor.is5gOn(getApplicationContext());
            final ScreenStep s = ScreenStep.current(getContentResolver());
            final boolean root = Executor.hasRoot();
            final boolean canWrite = canWriteSettings();
            rootOk = root;
            runOnUiThread(() -> {
                suppressSwitch = true;
                if (sw5g != null) sw5g.setChecked(g5);
                suppressSwitch = false;
                if (tvScreen != null) tvScreen.setText("当前：" + s.label);
                if (tvA11y != null) tvA11y.setText("无障碍保活："
                        + (GuardAccessibilityService.isEnabled(this)
                        ? "已开启" : "未开启（划掉后台后进程会被杀）"));

                stepBox.removeAllViews();
                stepBox.addView(line("5G：" + (g5 ? "允许 NR（5G 开）" : "仅 LTE（5G 关）")));
                stepBox.addView(line("息屏：" + s.label));
                stepBox.addView(line("Root：" + (root ? "可用" : "不可用（su 授权被拒或未生效）")));
                stepBox.addView(line("修改系统设置权限：" + (canWrite ? "已授予" : "未授予 → 点下面按钮去开")));
                stepBox.addView(line("LSPosed 模块：" + (XposedProbe.isActive(this)
                        ? "已激活" : "未激活（去 LSPosed 启用）")));
            });
        }, "qstweaks-refresh").start();
    }

    /** 「修改系统设置」这项特殊权限是否已开（对应应用详情里看到的那一项） */
    private boolean canWriteSettings() {
        try {
            return android.provider.Settings.System.canWrite(this);
        } catch (Throwable t) {
            return false;
        }
    }

    private void runCmd(String cmd) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            Executor.runLocal(app, cmd);
            runOnUiThread(MainActivity.this::refresh);
        }, "qstweaks-cmd").start();
    }

    /** 息屏档位：直接写，并记下选择以便进程重启后补写 */
    private void runScreen(ScreenStep s) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            Executor.setScreenTimeout(app, s);
            ScreenGuardService.start(app);   // 开了守护就确保服务在跑
            ScreenTileService.requestUpdate(app);
            runOnUiThread(MainActivity.this::refresh);
        }, "qstweaks-screen").start();
    }

    /** 一次性把用到的权限用 su 授给自己 */
    private void grantAll() {
        final android.content.Context app = getApplicationContext();
        android.widget.Toast.makeText(this, "正在授权（su 可能弹窗，请允许）…",
                android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String pkg = app.getPackageName();
            boolean root = Executor.hasRoot();
            // 不再套 `sh -c`，用最简单的单条命令，KernelSU / Magisk 都稳
            Executor.pmGrant(pkg, "android.permission.WRITE_SECURE_SETTINGS");
            Executor.pmGrant(pkg, "android.permission.WRITE_SETTINGS");
            Executor.appopsSet(pkg, "android:write_settings", "allow");
            Executor.pmGrant(pkg, "android.permission.MODIFY_PHONE_STATE");
            // 允许 SystemUI 绑定我们的磁贴（部分 ROM 需要包可见性）
            Executor.appopsSet("com.android.systemui", "android:query_all_packages", "allow");

            final String result = root
                    ? "已执行授权命令（用时见 logcat）"
                    : "未拿到 root：su 没有返回 uid=0";

            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, result
                        + "\n详细日志：adb logcat -s QSTweaks", android.widget.Toast.LENGTH_LONG).show();
                refresh();
            });
        }, "qstweaks-grant").start();
    }

    private void openQsEdit() {
        // Android 没有"直接打开 QS 编辑页"的公开 Action，改用系统设置里的快捷开关入口，失败则弹提示
        try {
            startActivity(new Intent("android.settings.QUICK_SETTINGS_TILES"));
            return;
        } catch (Throwable ignored) {}
        try {
            startActivity(new Intent("com.oplus.systemui.action.EDIT_QS"));
            return;
        } catch (Throwable ignored) {}
        android.widget.Toast.makeText(this, "请手动：下拉控制中心 → 编辑",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openWriteSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable ignored) {}
    }

    // ---------------- 小工具 ----------------

    private TextView h1(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18);
        v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(20), 0, dp(6));
        return v;
    }

    private TextView line(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setPadding(0, dp(2), 0, dp(2));
        return v;
    }

    private Button button(String t, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        return b;
    }
}