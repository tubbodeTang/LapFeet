# Tasks: 双角色键 Mod-Tap——巨硬选字与和弦快捷键共存

> 规则：
> - 一次只做一个任务；每个任务必须能独立提交并通过 CI
> - 每条任务的"验证"字段写明判定方式；CI 类验证 = 推送后 check-runs 全绿
> - 状态：`[ ]` 待做 / `[~]` 进行中（CI 运行中）/ `[x]` 完成（CI 绿）/ `[!]` 阻塞
> - 禁止未过 CI 就标 `[x]`
> - 临时纪律（B1 未修）：真机测试前改过任何配置 → 强停应用再测

分支：`spec/20260721-macrotap-modtap-chord-conflict`

- [ ] T1: Resolver 新增双角色判定纯函数
  - 内容: `HardwareShortcutResolver` 加 `isDualRoleBareKey(kp)`（candidate1-5 /
    pageNext / pagePrev 的裸按绑定命中 ∩ `HardwareChord.isChordModifierKeyCode`，
    复用既有解析与缓存）与 `isBoundChordSecondKey(kp)`（`resolveAction` ∪
    `resolvePaging` 命中）。可选：若 `app/src/test` 基建可用，补纯函数单测
    （CI workflow 目前不跑 test，属加固非门禁）。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/input/candidates/HardwareShortcutResolver.kt`
  - 验证: CI 全绿（4 平台矩阵）
  - 结果:

- [ ] T2: 设置项「和弦键轻点挂起（Mod-Tap）」
  - 内容: `AppPrefs.HardwareKeyboard` 加 `modTapEnabled: PBool`（默认 true）；
    「物理键盘」页（`HardwareKeyboardSettingsFragment`）加
    `SwitchPreferenceCompat` 一行；strings.xml 加标题 + 摘要两条文案。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/data/prefs/AppPrefs.kt`、
    `app/src/main/java/org/fcitx/fcitx5/android/ui/main/settings/behavior/HardwareKeyboardSettingsFragment.kt`、
    `app/src/main/res/values/strings.xml`
  - 验证: CI 全绿 + [真机] 打开「设置→物理键盘」能看到新开关、默认勾选、可切换
  - 结果:

- [ ] T3: 纯重构——抽取 `dispatchTapReplay`（行为不变）
  - 内容: 把 `FcitxInputMethodService.onKeyDown` 中「picker 选择 → 动作快捷键 →
    物理分支候选/符号面 → forwardKeyEvent」段（现约 1996-2056 行）原样抽成
    `private fun dispatchTapReplay(keyCode: Int, effectiveEvent: KeyEvent,
    allowTapHold: Boolean = true): Boolean`；原调用点以 `allowTapHold=true` 调用。
    **本任务禁止改变任何行为**（移动代码，不改逻辑）。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
  - 验证: CI 全绿 + [真机] 冒烟：打字出候选、选字、翻页、Sym 切符号窗、
    `Sym+O` 翻页、动作快捷键，均与改动前一致
  - 结果:

- [ ] T4: 挂起-裁决-重放主逻辑
  - 内容: 按计划 §2.2/§2.3 实现：
    1. `pendingModTap: Pair<Int, KeyEvent>?` 状态字段 + 会话收尾清理（与
       `HardwareChord.reset()` 同点）；
    2. keyDown 挂起点（`dispatchTapReplay` 内、`handleHardwareChordTapHold` 之后）：
       五条件全满足（物理模式/repeat0/开关开/候选显示中/`isDualRoleBareKey`）且
       非 caps 接管键（`capsLockEnabled && isCapsLatchKeyCode`）→ 存 pending、
       记 `consumedHardwareCandidateShortcutKeys`、return true；有旧 pending 先重放；
    3. 第二键裁决（onKeyDown 早期、latch 状态机之后、动作快捷键之前）：
       pending 存在时——和弦第二键（`isBoundChordSecondKey`）→ 作废 pending；
       非和弦 → 立即重放 pending 再继续；
    4. keyUp 重放：pending 键抬起 → `dispatchTapReplay(saved, allowTapHold=false)`；
    5. Alt-latch 消费点（双击第二击 / 解锁击）同步作废 pending。
  - 涉及文件: `app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt`
  - 验证: CI 全绿
  - 结果:

- [ ] T5: [真机] 全量 AC 验收（Titan2 / tt2 预设）
  - 内容: 按 spec AC1~AC10 逐条真机验证（改配置后强停应用；逐条记录 通过/不通过）：
    AC1 Sym+字母（开关类）/ AC2 Fn+字母、Alt+字母 / AC3 Sym+O/P 翻页 /
    AC4 裸点选字手感 + 空格不变 / AC5 Alt 双击锁、Shift 双击/长按锁大写回归 /
    AC6 长按 2s 再按字母 / AC7 非和弦第二键先选字 / AC8 长按自动重复 /
    AC9 关开关回退旧行为 / AC10 软键盘抽查。
  - 涉及文件: （无代码改动；结果记入 spec 复选框与 AC 报告）
  - 验证: [真机] AC1~AC10 全部通过（或列出差异项回到 T4 修）
  - 结果:

- [ ] T6: 全量 CI 绿 + 回归 AC 清单核对准备
  - 内容: 全平台矩阵 CI 最终确认全绿；spec 验收标准复选框按 T5 结果勾选；
    整理 AC 证据（截图/描述）供 sdd-review 独立核对；确认 B1 feature 立项排期。
  - 涉及文件: `specs/20260721-macrotap-modtap-chord-conflict/spec.md`（勾选）
  - 验证: CI 全绿 + AC 清单核对表就绪
  - 结果:
