# 设计说明（已合并进 README.md / TESTING.md，此文件保留给需要看细节的人）

## 为什么是两层

ColorOS 17 的 SystemUI 经过大量定制，离线无法确认：

1. `QSTileHost` 的类名、`getTiles()` 的返回类型（`String[]` 还是 `List<Tile>`）是否和 AOSP 一致
2. 控制中心「编辑」页是否会列出第三方 `TileService`
3. 移动数据磁贴长按弹出的详情面板类名（AOSP 是 `QSDetail`，ColorOS 很可能有自己的）

所以做成两层：

- 第一层（TileService）**一定可用**，只是需要手动在编辑页里添加
- 第二层（LSPosed 注入）让磁贴"天然"出现在控制中心，若类名对不上就自动降级

## 关键类

| 文件 | 作用 |
|---|---|
| `ModuleEntry` | LSPosed 入口，作用域 `com.android.systemui` |
| `TileInjector` | hook `QSTileHost`，注入两个 spec |
| `FakeTile` | 用真实 `QSTileImpl` 构造器造一个实例，接管行为 |
| `TileMirror` | `bindService` 到 TileService，用 `ITileService` 驱动它 |
| `MobileDataDetail` | 移动数据长按二级菜单（QSDetail 注入 / 对话框兜底） |
| `FiveGTileService` / `ScreenTileService` | 真实磁贴 |
| `Executor` | 执行动作（Telephony / Settings / su） |
| `CommandService` | 前台服务承载一次执行 |
| `TileCommandReceiver` / `PingReceiver` | 跨进程广播 |
| `BootReceiver` | 开机 root 授权 |
| `DebugActivity` | 诊断 |

## 5G 切换的 API 演进

| Android | API |
|---|---|
| ≤ 28 | `TelephonyManager#setPreferredNetworkType(int)` |
| 29 ~ 30 | `setPreferredNetworkTypeBitmask(long)` |
| 31+ | `setAllowedNetworkTypesForReason(ALLOWED_NETWORK_TYPES_REASON_USER, long)` |

Android 17 上走第三行。`ALLOWED_NETWORK_TYPES_REASON_USER` 需要 `MODIFY_PHONE_STATE`（signature 权限），root `pm grant` 即可。

位掩码（`NetworkTypeBitmap`）：

| 常量 | 值 |
|---|---|
| GSM | 1 |
| UMTS | 2 |
| LTE | 64 |
| NR | 32768 |

所以 5G 开 = 1|2|64|32768 = 32835；5G 关 = 1|2|64 = 67。
（代码里用常量名计算，不硬编码，防止不同版本位定义变化。）

`preferred_network_mode` 的 `NETWORK_MODE_*` 是另一套：

| 值 | 含义 |
|---|---|
| 9 | LTE/WCDMA/GSM |
| 33 | NR/LTE/WCDMA/GSM |

## 息屏

`screen_off_timeout` 是 `Settings.System` 的 int 毫秒值。Android 官方"永不"约定 = `Integer.MAX_VALUE / 2` = 1073741823ms（约 12.4 天）。
写这个值需要 `WRITE_SECURE_SETTINGS`。

## 跨进程通信

```
SystemUI 进程 (模块)
   │  bindService → ITileService.onClick()
   │  sendBroadcast(ACTION_RUN / ACTION_PING, package=PKG)
   ▼
本 App 主进程
   TileCommandReceiver → CommandService(前台) → Executor → Telephony / Settings / su
```

不要用 SharedPreferences 做主进程 ↔ SystemUI 的直接通道 —— 两个进程的 `getSharedPreferences` 文件位置不同，互相看不到。所以模块侧一律 sendBroadcast。