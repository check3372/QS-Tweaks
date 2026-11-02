#!/system/bin/sh
# QS Tweaks 一键 root 授权脚本（由 App 内部调用，也可以手动 su 执行查看结果）
#   su -c sh allow_systemui.sh
# 输出用 logcat -s QSTweaks 看

echo "== pm grant: 本 App 系统级权限 =="
pm grant com.firefly.qstweaks android.permission.WRITE_SECURE_SETTINGS
pm grant com.firefly.qstweaks android.permission.MODIFY_PHONE_STATE
appops set com.firefly.qstweaks android:write_settings allow

echo "== SystemUI 绑定/广播到本 App 所需的包可见性 =="
appops set com.android.systemui android:query_all_packages allow 2>/dev/null
pm grant com.android.systemui android.permission.QUERY_ALL_PACKAGES 2>/dev/null

echo "== 验证 =="
dumpsys package com.firefly.qstweaks | grep -E "WRITE_SECURE_SETTINGS|MODIFY_PHONE_STATE" -A1
appops get com.firefly.qstweaks android:write_settings
echo "done"