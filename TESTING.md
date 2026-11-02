# QS Tweaks · 真机测试清单

装好 APK 后按顺序验这几步。前面几步不依赖 hook，可以单独确认。

## 0. 前提

```bash
adb shell su -c id                          # 有 root
adb shell getprop ro.build.version.release   # Android 版本
adb shell getprop ro.build.display.id        # ColorOS 版本号
adb shell pm list packages | grep systemui   # 确认 com.android.systemui
```

## 1. App 内功能（不依赖 hook）

- 打开 QS Tweaks → 点 **用 Root 授予全部权限**
- 切 5G 开关，`adb logcat -s QSTweaks` 应看到 `run done ok=true`
- 点息屏两档，`adb shell settings get system screen_off_timeout` 应随之变化
  - 15 秒 → 15000
  - 5 分钟 → 300000

## 2. 控制中心磁贴

- 下拉控制中心 → 编辑 → 找「5G 开关」「息屏时间」
- 在列表里就直接添加；不在列表里则依赖下一节的注入
- 点一下，确认图标和副标题跟着变：5G 磁贴切到「允许 NR」时亮起，息屏磁贴切到 5 分钟时亮起

## 3. LSPosed 注入

- LSPosed → 启用模块 → 作用域勾 `com.android.systemui` → 重启系统界面
- 打开 App，「LSPosed 模块」应显示**已激活**
- `adb logcat -s XposedBridge` 应看到 `[QSTweaks] hooked createTile` / `hooked getTiles`

只看到 `QSTileHost 找不到` 说明 ColorOS 的类名与 AOSP 不同，需要重新定位。

## 4. 息屏持久性（关键回归项）

这是之前反复出问题的地方，每次改动都要重测：

1. 息屏磁贴切到 5 分钟
2. 下拉通知栏 → 从最近任务里把 QS Tweaks 划掉
3. 再点息屏磁贴，正常应在 15 秒 / 5 分钟之间切换
4. `adb shell settings get system screen_off_timeout` 跟着变才算真正生效

划掉后台后如果 5G 磁贴还能用、息屏磁贴失效，说明无障碍保活没开住。

## 5. 参数对照

| 命令 | 含义 |
|---|---|
| `adb shell settings get global preferred_network_mode0` | `33` = 允许 5G，`9` = 仅 LTE |
| `adb shell settings get system screen_off_timeout` | 15000 / 300000 |
| `adb shell dumpsys telephony.registry \| grep -i allowedNetwork` | 当前允许的 RAT 位掩码 |
| `adb logcat -s QSTweaks` | 关键日志，重点看 `effective=` |

## 6. 常见坑

- **5G 切不动**：运营商锁定首选网络类型，或 `MODIFY_PHONE_STATE` 没授上。
  用 `adb shell dumpsys package com.firefly.qstweaks | grep MODIFY_PHONE_STATE` 检查
- **息屏点了又弹回**：看 logcat 里那行的 `effective=`，`false` 就是被系统拦住了
- **重启后失效**：BootReceiver 会重新授权；如果 su 管理器限制了开机自授权，手动再点一次
- **被电池优化杀后台**：设置 → 电池 → QS Tweaks → 不限电