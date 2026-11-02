package com.firefly.qstweaks;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** PendingIntent 工具：TileService 长按打开 App（API 34+ 只接受 PendingIntent） */
public final class Actions {
    private Actions() {}

    public static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // 启动 Activity 的 PendingIntent 在部分 ROM 上要求 MUTABLE；
        // API < 31 没有 FLAG_MUTABLE 常量，按版本区分
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getActivity(ctx, 0x10, i, flags);
    }
}