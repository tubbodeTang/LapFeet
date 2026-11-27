# ADR-0001: 验证基线 — GitHub Actions PR CI 作为唯一可信验证

- 状态: accepted
- 日期: 2025-07-21
- 决策者: 用户（拍板）+ AI（探索与建议）

## 背景

LapFeet（TapFeet IME，基于 fcitx5-android 二次开发）的构建依赖链重：
JDK 17、Android SDK 35/36、NDK 25/28、CMake 3.22+/3.31、extra-cmake-modules、gettext，
且仓库含 git submodule（lib/fcitx5 等 C++ 核心）。

开发机为 Windows（git-bash），探索确认本机**无** Java / Android SDK / CMake / ninja，
从零搭建可运行验证环境预计需 2-3 天且后续维护成本高（版本漂移、磁盘占用）。
同时项目维护者对 Kotlin/Android 技术栈不熟，需要一个客观、可追溯的"完成"判据。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| A. 本机搭建完整 Android 构建环境 | 验证即时、离线可用 | 安装 2-3 天；NDK/SDK 版本漂移风险；Windows 下部分依赖（msgfmt 等）坑多 | 否决 |
| B. WSL2 + nix（仓库自带 flake.nix devShell） | 环境可复现，上游原生方案 | 仍需数天搭建；WSL2 磁盘/内存开销大 | 否决（保留为未来选项） |
| C. **GitHub Actions PR CI（仓库现成 workflow）** | 零安装；上游已维护的 4 平台矩阵；`pull_request` 触发不依赖 secret；公开 API 可匿名轮询状态 | 每轮验证有分钟级延迟；匿名 API 有 60 次/小时限流（轮询用 30s+ 间隔足够） | **采用** |

## 决策

1. 不建本地构建环境。唯一可信验证 = 推送到 `spec/<feature-id>` 分支、开 PR、
   GitHub Actions `pull_request.yml`（+ `nix.yml`）全部 check 通过。
2. 验证命令基线（CI 内执行，本地不执行）：
   - `./gradlew :app:assembleDebug`
   - `./gradlew :assembleDebugPlugins`
3. 状态轮询：GitHub 公开 API `GET /repos/tubbodeTang/LapFeet/commits/<sha>/check-runs`
   （脚本：`sdd-implement/scripts/ci_status.sh`），匿名可用；推送与建 PR 需 `GH_TOKEN`
   （存放于 `FirstLanding/sdd/secrets.env`，不入库）。
4. CI 未全绿前，任何任务不得标记完成；失败必须读取 CI 日志定位修复。
5. 纯运行时/手感类验证（真机键盘行为）CI 无法覆盖，必须在 spec 验收标准中
   显式标注"需人工真机确认"，由用户在门禁 G3/G4 执行。

## 后果

- 每个任务的验证延迟 = 推送 + CI 排队构建（分钟级），任务粒度应足够小以控制循环次数
- 网络故障/GitHub 限流时验证阻塞 → 允许标注"CI 阻塞（非代码原因）"并在恢复后补验
- 未来若建立本地环境（方案 B），需立新 ADR 取代本条
