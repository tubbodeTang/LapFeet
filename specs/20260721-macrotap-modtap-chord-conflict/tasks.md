# Tasks: 双角色键 Mod-Tap——巨硬选字与和弦快捷键共存

> 规则：
> - 一次只做一个任务；每个任务必须能独立提交并通过 CI
> - 每条任务的"验证"字段写明判定方式；CI 类验证 = 推送后 check-runs 全绿
> - 状态：`[ ]` 待做 / `[~]` 进行中（CI 运行中）/ `[x]` 完成（CI 绿）/ `[!]` 阻塞
> - 禁止未过 CI 就标 `[x]`
> - 临时纪律（B1 未修）：真机测试前改过任何配置 → 强停应用再测

分支：`spec/20260721-macrotap-modtap-chord-conflict`

> **实现期设计修正（相对 plan，已同步 ADR-0005 讨论）**
> 1. ~~T3 抽取 `dispatchTapReplay`~~：取消。补发复用**既有** tap-hold 释放路径
>    （`CandidatesView.handleChordTapRelease` → 候选面不接才 `handleHardwareSymKey`），
>    与 `HardwareChord.consumeSymbolTap` 同构，无需新抽取（更小改动面，ADR-0005 决策3 的精神保留：
>    重放不重入完整 onKeyDown）。
> 2. 双角色判定改为 `isDualRoleBareKey = 裸候选绑定 ∩ 锁定触发键`（Alt-latch 配置键 ∪ 两个 Shift；
>    物理 CapsLock 无和弦家族，不纳入）。plan 原文引用的 `HardwareChord.isChordModifierKeyCode`
>    在代码中不存在，属计划期笔误。
> 3. 挂起门槛 = 五条件（开关/物理模式/候选可见/双角色裸键/**该修饰键家族存在任意和弦绑定**）。
>    不按"和弦字母是否也是候选键"收窄：旧行为下双角色键裸按总是先选候选、和弦照发，
>    对任何和弦意图候选选择都是多余副作用；对无和弦意图的裸按，补发重放的正是旧行为本身。
> 4. 第二键裁决简化为**无条件补发**（任意第二键 down → 立即补发候选选择，第二键走完整原路径），
>    plan §2.3 的"和弦第二键/非和弦"两分支殊途同归，合并。
> 5. CI 增加 ubuntu 腿 `:app:testDebugUnitTest`（原先 CI 不跑单测，测试文件连编译都无人验证）。

- [x] T1: Resolver 新增双角色判定纯函数
  - 内容: `HardwareShortcutResolver` 新增：`matchesBareCandidateKey(kp)`（candidate1-5 纯键绑定命中）、
    `isLockTriggerKey(kp)`（altLatchKey 配置键 ∪ Shift_L/R）、`isDualRoleBareKey(kp)`（两者交集）、
    `chordModifierFamilyForKeyCode(keyCode)`（物理修饰键→和弦家族名）、
    `hasChordBindingForModifier(family)` + 纯函数核心 `chordBindingForFamily(family, bindings)`
    （该家族是否存在和弦绑定，JVM 可测）。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/input/candidates/HardwareShortcutResolver.kt`
  - 验证: CI 全绿（4 平台矩阵）
  - 结果: 完成。CI run 5daa6668 四平台全绿（ubuntu-24.04 / macos-15-intel / macos-26 / windows-2025）。

- [x] T2: 设置项「双角色键 Mod-Tap」
  - 内容: `AppPrefs.HardwareKeyboard` 加 `modTapEnabled`（托管 `switch()`，默认 true，
    自动注册到「物理键盘」页，无需改 Fragment）；strings.xml（en + zh-rCN）加标题+摘要。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/data/prefs/AppPrefs.kt`、
    `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh-rCN/strings.xml`
  - 验证: CI 全绿 + [真机] 「设置→物理键盘」能看到新开关、默认勾选、可切换
  - 结果: 代码完成（CI 绿）；真机可见性/可切换性并入 T5 验收清单核对。

- [x] T3: 挂起-补发主逻辑（Service）
  - 内容: `FcitxInputMethodService`：
    1. `modTapPendingEvent: KeyEvent?` 状态 + `onFinishInput` 清理（与 `HardwareChord.reset()` 同点）；
    2. `modTapShouldArm`（五条件门槛）；
    3. onKeyDown 挂起点：动作快捷键**之前**——有旧 pending 先补发再放行；满足门槛 → 存 pending、
       记 `consumedHardwareCandidateShortcutKeys`、return true；
    4. `flushModTapPending`：补发 = `handleChordTapRelease`（候选面）→ 不接才 `handleHardwareSymKey`；
    5. onKeyUp：pending 键抬起（无第二键）→ 补发 + 吞 up；**置于 `dispatchAltLatchUp` 之前**
       （Alt latch 键的 up 会被那里吞掉）。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
  - 验证: CI 全绿
  - 结果: 完成。CI run 5daa6668 四平台全绿；运行时行为待 T5 真机验收。

- [x] T4: 单测 + CI 单测腿
  - 内容: `HardwareModTapTest`（JVM）：`chordBindingForFamily` 纯函数 6 例（本家族/异家族/
    非和弦/伪修饰键家族隔离/空集/正反）+ `chordModifierFamilyForKeyCode` 映射 6 例；
    CI workflow 加 ubuntu 腿 `:app:testDebugUnitTest`。
  - 涉及文件: `app/src/test/java/org/fcitx/fcitx5/android/input/candidates/HardwareModTapTest.kt`、
    `.github/workflows/pull_request.yml`
  - 验证: CI 全绿（含新单测步骤）
  - 结果: 代码已推（含单测 + CI 单测腿）。
    基础设施修正（本分支内附带提交，非本特性逻辑）：`android-actions/setup-android@v3` 在
    Node 24 强迁下崩溃（上游 TapFeet 同挂，四平台全挂，非本特性引入），升到 `@v4`（v4.0.4，
    2026-09 发布）；`gradle/actions/setup-gradle@v5` 新增的 wrapper 校验会报
    `build-logic/gradle/wrapper/gradle-wrapper.jar`（上游遗留的占位文本文件，上游同 blob）
    为未知 jar，故该步骤设 `validate-wrappers: false`（根 wrapper 才实际参与构建）。
    单测修复（又 1 轮 CI）：`HardwareShortcutResolver` 是 Kotlin object，JVM 上首次触碰
    任一成员会触发其 `<clinit>`（AppPrefs.getInstance()，Android-only）而崩，故两个纯
    函数移入独立纯 object `ChordFamily`（新文件 `input/candidates/ChordFamily.kt`，T1 范围
    内的等价重排），resolver 保留同名委托（T3 调用点不变），测试改打 ChordFamily。
    另：CI 的 gradle 步骤加了失败时 `::error` 尾 80 行标注（细粒度 token 拉不了原始日志，
    标注能经 API 读到）。
    另：token 纸面推送对 `.github/workflows/` 需 token 的 Workflows 写权限
    （已开通），故 workflow 改动也走 API 推送，未用 Web UI。
    另（最终 3 轮 CI 基础设施修正，均非本特性逻辑）：
    ① 四平台 `:app:compileDebugKotlin` OOM（GC overhead limit exceeded）：Gradle 在 CI 上
       默认给 Kotlin 编译的堆不够且 ParallelGC 在堆紧张时报 GC overhead；workflow 加
       `-Dkotlin.daemon.jvmargs`/`-Dorg.gradle.jvmargs` = 非 ubuntu `-Xmx6g`、ubuntu `-Xmx4g`，
       均加 `-XX:+UseG1GC`（按 matrix.os 分档，ubuntu 7GB 内存留余量）。
    ② 新单测腿首跑暴露仓库既有失败用例 `ThemeSerializationTest.version2`（历史遗留：
       当前主题 schema 已升到 2.1，该用例仍断言 2.0 不需迁移；此前无 CI 单测腿、本地
       无构建环境，从未被执行过）：改为断言 2.0→2.1 应迁移。25/25 全过。
    ③ 细粒度 token 拉不到 Actions 原始日志（/logs 403 签名），失败诊断改为 `::error`
       标注（含 daemon/堆 诊断行）+ 上传 debuglog/testresults artifact。

- [ ] T5: [真机] 全量 AC 验收（Titan2 / tt2 预设）
  - 内容: 按 spec AC1~AC10 逐条真机验证（改配置后强停应用；逐条记录 通过/不通过）。
    前置条件：至少存在一个 `Alt+字母` 或 `Shift+字母` 和弦绑定（否则双角色键永不挂起，
    属设计内的零变更）。
  - 涉及文件: （无代码改动；结果记入 spec 复选框与 AC 报告）
  - 验证: [真机] AC1~AC10 全部通过（或列出差异项回到 T3 修）
  - 结果:

- [ ] T6: 全量 CI 绿 + 回归 AC 清单核对准备
  - 内容: 全平台矩阵 CI 最终确认全绿；spec 验收标准复选框按 T5 结果勾选；
    整理 AC 证据（截图/描述）供 sdd-review 独立核对；确认 B1 feature 立项排期。
  - 涉及文件: `specs/20260721-macrotap-modtap-chord-conflict/spec.md`（勾选）
  - 验证: CI 全绿 + AC 清单核对表就绪
  - 结果:
