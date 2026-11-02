package com.firefly.qstweaks;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 诊断页：显示关键状态，方便排查
 *  - Android / ColorOS 版本
 *  - allowedNetworkTypes 原始值（NR 位在不在）
 *  - screen_off_timeout 当前值
 *  - 模块是否激活
 */
public class DebugActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("诊断");
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        TextView tv = new TextView(this);
        tv.setTextIsSelectable(true);
        tv.setText("正在检测…");
        final TextView out = tv;
        root.addView(tv);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        // su 调用可能慢，放后台线程，避免 ANR
        new Thread(() -> {
            final String text = diag();
            runOnUiThread(() -> out.setText(text));
        }, "qstweaks-diag").start();
    }

    private String diag() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" / API ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        sb.append("设备: ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append('\n');
        try {
            sb.append("ROM: ").append(android.os.Build.DISPLAY).append('\n');
        } catch (Throwable ignored) {}

        try {
            android.telephony.TelephonyManager tm =
                    (android.telephony.TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            long m = tm.getAllowedNetworkTypesForReason(
                    android.telephony.TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
            long nr = (long) android.telephony.TelephonyManager.class
                    .getField("NETWORK_TYPE_BITMASK_NR").get(null);
            sb.append("allowedNetworkTypes = ").append(m)
                    .append("  (NR 位=").append((m & nr) != 0 ? "有" : "无").append(")\n");
        } catch (Throwable t) {
            sb.append("allowedNetworkTypes 读取失败: ").append(t).append('\n');
        }

        int ms = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT, -1);
        sb.append("screen_off_timeout = ").append(ms)
                .append("  → ").append(ScreenStep.fromTimeout(ms).label).append('\n');
        ScreenStep saved = Executor.savedScreen(this);
        sb.append("用户选择的档位 = ")
                .append(saved == null ? "（从未选过）" : saved.label + " (" + saved.timeoutMs + "ms)")
                .append('\n');

        sb.append("is5gOn = ").append(Executor.is5gOn(this)).append('\n');
        sb.append("LSPosed 模块 = ").append(XposedProbe.isActive(this) ? "已激活" : "未激活").append('\n');

        // 权限自查：这两条是息屏能不能改的关键
        sb.append("\n--- 权限 ---\n");
        sb.append("WRITE_SECURE_SETTINGS = ").append(checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED).append('\n');
        sb.append("MODIFY_PHONE_STATE = ").append(checkCallingOrSelfPermission(
                "android.permission.MODIFY_PHONE_STATE")
                == android.content.pm.PackageManager.PERMISSION_GRANTED).append('\n');

        // 写一条测试值再读回来，直接验证 Settings 通道
        sb.append("\n--- 写入测试 ---\n");
        int test = (ms == 45000) ? 30000 : 45000;   // 随便换一个值
        boolean putOk = false;
        try {
            putOk = android.provider.Settings.System.putInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_OFF_TIMEOUT, test);
        } catch (Throwable t) {
            sb.append("putInt 异常: ").append(t.getClass().getSimpleName()).append('\n');
        }
        int back = android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.SCREEN_OFF_TIMEOUT, -1);
        sb.append("写 ").append(test).append(" → putInt=").append(putOk)
                .append("，读回 ").append(back)
                .append(back == test ? "（成功）" : "（失败！）").append('\n');
        // 还原成原值
        try {
            android.provider.Settings.System.putInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_OFF_TIMEOUT, ms);
        } catch (Throwable ignored) {}
        sb.append("已还原为 ").append(ms).append('\n');

        // root 诊断：对 KSU 用户很关键
        sb.append("\n--- Root ---\n");
        sb.append("su 可执行 = ").append(Executor.suBinaryFound()).append('\n');
        String id = Executor.shCapture("id");
        sb.append("su -c sh -c id => ").append(id == null ? "执行失败" : id.trim()).append('\n');
        sb.append("hasRoot = ").append(Executor.hasRoot()).append('\n');
        return sb.toString();
    }
}