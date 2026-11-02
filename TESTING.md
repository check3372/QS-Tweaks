# QS Tweaks · 测试与调参清单

（这份是给你拿到 APK 后一步步验的，跟 README 里的安装步骤配套。）

## 0. 先确认前提

```bash
adb shell su -c id                        # 确认有 root
adb shell pm list packages | grep systemui # 确认 com.android.systemui
adb shell getprop ro.build.version.release # 确认 Android 版本
adb shell getprop ro.build.display.id      # 确认 ColorOS 版本号
```

## 1. 装完后先测「App 内」功能（不依赖 hook）

- 打开 QS Tweaks
- 点 **用 Root 授予全部权限**
- 切 5G 开关；`adb logcat -s QSTweaks` 应看到 `run done ok=true`
- 点息屏四档，`adb shell settings get system screen_off_timeout` 应变化
  - 30 秒 → 30000
  - 5 分钟 → 300000
  - 10 分钟 → 600000
  - 永不 → 1073741823（Integer.MAX_VALUE/2）

## 2. 测「控制中心磁贴」（第一层，TileService）

- 下拉控制中心 → 编辑 → 找「5G 开关」「息屏时间」
- 若在列表里：直接添加，点一下看状态有没有变
- 若不在列表里：说明 ColorOS 的编辑页没有列第三方 TileService（少见），只能靠第二层

## 3. 测「LSPosed 注入」（第二层）

- LSPosed → 启用模块 → 作用域勾 `com.android.systemui` → 重启系统界面
- 打开 App，「LSPosed 模块」应显示 **已激活**（否则看 logcat）
- `adb logcat -s XposedBridge:*` 应看到 `[QSTweaks] hooked createTile` / `hooked getTiles`

如果只看到 `QSTileHost 找不到` / `CellularTile 未找到`，说明 ColorOS 的类名和 AOSP 不同，
把下面这条命令的输出发我：

```bash
adb shell "su -c 'ls /system_ext/priv-app/SystemUI/ 2>/dev/null; find / -name SystemUI.apk 2>/dev/null | head'" 
```

## 4. 测「移动数据长按二级菜单」

- 长按「移动数据」磁贴
- 情况 A：详情面板顶部多出「5G 网络」一行 → 成功
- 情况 B：弹窗「移动数据 / 5G 网络」→ 兜底成功（模块的细节面板类名没对上，但入口有了）
- 情况 C：什么都不变 → 日志里应该能看到 `CellularTile 未找到`，发日志给我调整类名

## 5. 参数对照表

| 命令 | 结果 |
|---|---|
| `adb shell settings get global preferred_network_mode0` | `33`=允许5G，`9`=仅LTE |
| `adb shell settings get system screen_off_timeout` | 见上文 |
| `adb shell dumpsys telephony.registry | grep -i allowedNetwork` | 当前允许的 RAT 位掩码 |

## 6. 常见坑

- **5G 切不动**：运营商锁定首选网络类型，或 `MODIFY_PHONE_STATE` 没授上
  → 检查 `adb shell dumpsys package com.firefly.qstweaks | grep MODIFY_PHONE_STATE`
- **重启后失效**：BootReceiver 会再授权一次；如果 su 管理器限制了开机自授权，要手动再点一次
- **CU（ColorOS 的电池优化）杀后台**：设置→电池→QS Tweaks → 不限电