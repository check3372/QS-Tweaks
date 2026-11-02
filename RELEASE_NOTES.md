> **本项目代码完全由 AI 生成。** 详见下方 README 的「关于本项目的由来」。
> **需要 Root 与 LSPosed，且仅在 ColorOS 17 / Android 17 实测通过 —— 使用前请阅读免责声明，并准备好救砖手段。**
> 使用风险由你自行承担，本项目不提供任何担保。

下载：[最新版本](https://github.com/check3372/QS-Tweaks/releases/latest)

---

ColorOS 控制中心磁贴：5G 开关 + 息屏时间切换。

## 功能

- **5G 磁贴**：一键在「允许 NR」和「仅 LTE」之间切换，不丢 VOLTE
- **息屏磁贴**：15 秒 / 5 分钟两档切换，切到 5 分钟后磁贴亮起

## 要求

- 已 Root（KernelSU / Magisk），首次使用需在应用内授予 Root 权限
- LSPosed 模块已启用，作用域勾选「系统界面」
- 需要授予「修改系统设置」权限
- 建议开启无障碍服务保活，否则从最近任务划掉后息屏磁贴会失效（5G 磁贴不受影响）

## 安装

1. 安装 APK
2. 在 LSPosed 中启用模块，作用域勾选「系统界面」，重启系统界面
3. 打开 QS Tweaks，按页面提示授予权限
4. 下拉控制中心 → 编辑 → 把两个磁贴拖出来

已在 ColorOS 17 (Android 17) + KernelSU 测试通过。

## 已知限制

- ColorOS 对 `Settings.System.putInt` 存在"假成功"行为，必须读回校验后强制 `su` 重写
- 息屏守护是无通知后台服务，依赖无障碍保活；被系统清理后 5G 磁贴仍可用，息屏磁贴会失效
- 其他 ROM / 机型未测试

## 免责声明

需要 **Root 权限** 并会 **钩入 SystemUI 进程**，存在导致 SystemUI 崩溃循环的风险。
请提前准备好 LSPosed 音量键紧急停用、Recovery 删除模块目录等救砖手段。
本项目由 AI 生成，**不提供任何担保**，不对任何损害负责。详见 README。

## 许可证

[MIT](LICENSE)