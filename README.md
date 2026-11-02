# QS Tweaks · v3.0

Android 11+（目标 ColorOS 17）· 需 **Root（Magisk / KernelSU）+ LSPosed**

[![最新版本](https://img.shields.io/github/v/release/check3372/QS-Tweaks)](https://github.com/check3372/QS-Tweaks/releases/latest)

> **[下载最新版](https://github.com/check3372/QS-Tweaks/releases/latest)**

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

## 关于本项目的由来

**本项目代码完全由 AI 生成。**

从需求拆解、SystemUI 逆向分析、LSPosed 钩子设计到每一行 Java、每个资源文件，均由 AI 编写，
人类参与的部分是：提出需求、在真机上测试、反馈现象与日志（包括那四个坑里的 logcat 和截图）、
以及决定最终方案。

项目在 ColorOS 17 / Android 17 + KernelSU + LSPosed 真机上通过实测，
但请把它理解为一个"能跑通的实现"，而不是经过长期打磨、代码审查的成熟工程。

## 免责声明

**使用本软件的风险由你自行承担。**

- 本项目需要 **Root 权限**，并会修改系统设置（首选网络类型、息屏超时）。修改系统网络与显示
  设置存在导致通话异常、无法上网、系统行为异常的可能。
- 本项目通过 **LSPosed 钩入 SystemUI 进程**。钩子失效、版本不匹配或与其他模块冲突时，
  可能导致系统界面反复崩溃（SystemUI 崩溃循环）。**请提前准备好救砖手段**：
  LSPosed 的「音量键紧急停用」功能、Recovery 下删除模块目录、或 ADB 停用模块。
- 本项目**仅在 ColorOS 17 (Android 17) 一款设备上验证过**。其他机型、其他 ROM、
  其他 Android 版本**未经测试**，直接安装可能出现不可预期的问题。
- 本项目由 AI 生成，**不提供任何明示或暗示的担保**，包括但不限于适销性、特定用途适用性
  和不侵权的担保。作者与贡献者不对任何直接、间接、偶然、特殊或后果性损害负责，
  包括但不限于数据丢失、设备损坏、运营商合约问题或经济损失。
- 修改首选网络类型可能影响**运营商网络策略**（如某些套餐对 5G 接入的限制）。
  请自行确认你的 SIM 卡与套餐允许该操作。
- 本项目**不收集任何数据**，不联网，不上传任何信息。所有操作均在本机完成。
- 本项目与 OPPO / ColorOS / Google / LSPosed / Magisk / KernelSU 均无关联，
  未获其认可或支持。
- 使用本项目即表示你已阅读、理解并接受上述全部内容。如果你不理解其中任何一条，
  **请不要安装**。

本项目以 MIT 许可证开源，详见 [LICENSE](LICENSE)。