# 发布到 GitHub 的步骤

## 1. 本地初始化并提交

在 Android Studio 里打开 `QSTweaks` 项目后，使用 Terminal（Alt+F12）：

```bat
git init
git add .
git commit -m "feat: QS Tweaks v3.0 - 5G 与息屏时间控制中心磁贴"
```

`.gitignore` 已经写好，`local.properties`、`build/`、`.idea/`、keystore 都不会被提交。

## 2. GitHub 上建仓库

网页操作：https://github.com/new

- **Repository name**：`QS-Tweaks`
- **Description**：ColorOS 控制中心 5G / 息屏磁贴（Root + LSPosed）
- **Public**（LSPosed 模块一般公开，方便别人审查代码 —— 这类工具拿到 root 权限，公开透明度很重要）
- **不要**勾选 "Add a README"（我们已经有了）

创建后复制仓库地址，形如：
`https://github.com/你的用户名/QS-Tweaks.git`

## 3. 推送

```bat
git branch -M main
git remote add origin https://github.com/你的用户名/QS-Tweaks.git
git push -u origin main
```

第一次 push 会弹浏览器登录 GitHub，授权即可。

## 4. 发 Release（可选，但推荐）

LSPosed 用户习惯直接从 Release 下 APK：

```bat
.\_build_debug.bat
```

编译出 `app\build\outputs\apk\debug\app-debug.apk`，
在 GitHub 仓库页 → Releases → Draft a new release：

- Tag：`v3.0`
- Title：`QS Tweaks v3.0`
- 上传 `app-debug.apk`（可以重命名为 `QS-Tweaks-v3.0.apk`）
- 说明里写清楚：需要 Root + LSPosed，作用域勾选「系统界面」

## 注意事项

- **不要提交 keystore 和密码**。`.gitignore` 已排除 `*.jks` / `*.keystore`。
- `local.properties` 里有你的 SDK 路径，已排除。
- 如果之后加了正式签名，建议把 keystore 放仓库外，通过环境变量或 `keystore.properties`（同样加进 .gitignore）引用。