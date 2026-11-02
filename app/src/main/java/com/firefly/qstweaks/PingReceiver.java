package com.firefly.qstweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 模块每次 hook 成功后发一次，设置页据此显示「LSPosed 模块：已激活」 */
public class PingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        if (!TileCommandReceiver.ACTION_PING.equals(intent.getAction())) return;
        XposedProbe.markActive(ctx);
    }
}