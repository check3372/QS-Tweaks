# QS Tweaks · v3.0

Android 11+（目标 ColorOS 17）· 需 **Root（Magisk / KernelSU）+ LSPosed**

## 功能

| 磁贴 | 行为 |
|---|---|
| **5G** | 点击切换「允许 NR ↔ 仅 LTE」（改首选网络类型） |
| **息屏** | 点击切换「15 秒 ↔ 5 分钟」，5 分钟档亮起 |

App 里还有：一键 root 授权、5G 开关、息屏两档按钮、息屏守护开关、无障碍保活入口、诊断页。

## 安装

1. 装 APK，打开 → **用 Root 授予全部权限**（KSU 会弹授权，点允许）
2. LSPosed → 启用 **QS Tweaks** → 作用域只勾 **系统界面** → 重启
3. 控制中心 → 编辑 → 添加「5G 开关」「息屏时间」
4. App 里 → **开启无障碍保活**（系统设置 → 无障碍 → QS Tweaks 打开）

第 4 步很重要：息屏守护是无通知的后台服务，没有无障碍撑着，划掉后台后它就停了。

## 四个坑（都踩过、都已解决）

### 1. ColorOS 会"假成功"

```
D QSTweaks: screen 15 秒 java=true su=null readback=300000 effective=false
```

`Settings.System.putInt` 返回 `true`，值却是 300000。**不能信返回值**，必须读回；读回不对就强制 `su` 再写一次。

### 2. `putInt` 成功后要立刻读回

读取用 `Settings.System.getInt(SCREEN_OFF_TIMEOUT)`，判断时允许 ±2 秒误差（系统会取整）。

### 3. `su -c` 不能套 shell

`su -c "sh -c \"...\""` 这种三层引号在部分 su 上会静默失败（退出码 0 但没执行）。
用单条命令：`su -c "settings put system screen_off_timeout 15000"`。

### 4. KernelSU 的 root 检测

只看退出码会误判。读 `su -c sh -c "id"` 的 stdout，判断含不含 `uid=0`。

## 代码结构

```
app/src/main/java/com/firefly/qstweaks/
├─ ModuleEntry.java              LSPosed 入口（作用域 com.android.systemui）
├─ TileInjector.java             往 QSTileHost 注入两个磁贴
├─ FakeTile.java                 伪造 QSTileImpl，接管点击/渲染
├─ TileMirror.java               bindService 到 TileService，驱动其执行
├─ FiveGTileService.java         5G 磁贴
├─ ScreenTileService.java        息屏磁贴
├─ ScreenGuardService.java       息屏守护（无通知后台服务）
├─ GuardAccessibilityService.java 无障碍保活
├─ ScreenChangedReceiver.java    亮屏/解锁时补写
├─ Executor.java                 TelephonyManager / Settings / su
├─ CommandService.java           执行一次命令的前台服务
├─ TileCommandReceiver / PingReceiver / ActionReceiver / BootReceiver
├─ MainActivity.java             设置界面
└─ DebugActivity.java            诊断页
```

## 编译

```bat
cd QSTweaks
.\_build_debug.bat
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

当前是 **debug 签名**，可以自用，但发给别人建议换正式 keystore。

## 排错

诊断页会显示：`allowedNetworkTypes`、`screen_off_timeout`、用户选的档位、两项权限状态、一次写入测试、`su` 的实际输出。

日志：

```
adb logcat -s QSTweaks XposedBridge
```

重点看：

```
screen 15 秒 java=? su=? readback=? effective=?
```

| 现象 | 处理 |
|---|---|
| 磁贴不出现 | LSPosed 作用域没勾 / 没重启 SystemUI；直接用编辑页手动添加 |
| 5G 切不动 | 看诊断页 `allowedNetworkTypes` 有没有变；没变多半是运营商锁网 |
| 息屏点了又弹回 | 看 logcat 那行 `effective=`；`false` 说明被系统拦住 |
| 划后台后息屏失效 | 无障碍保活没开，或者被 ColorOS 清了 |

## 附带的工具

`tools/icon_gen.html` —— 想换成文字版图标时，用浏览器打开它，会把 "5G" 用系统字体渲染成 PNG，
下载后放到 `app/src/main/res/drawable-nodpi/ic_tile_5g.png` 再编译即可。