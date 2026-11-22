# Plan: 双角色键 Mod-Tap——巨硬选字与和弦快捷键共存

> 依据：`spec.md`（status: spec-approved，2026-07-21 过 G1）。
> 关联 ADR：**ADR-0005**（本 feature 的核心设计决策）。

## 1. 方案概述

在物理键盘模式的派发链中，为「双角色键」（裸按绑定 = 候选 1-5 / 翻页，且该键是和弦
修饰键 Sym/Fn/Alt_L/Alt_R/Shift_L/Shift_R）引入**事件驱动的挂起-重放**（QMK Mod-Tap
泛化），完全复用代码库已验证的符号键 tap-hold 范式（`HardwareChord.armSymbolTap` +
`onKeyUp` 补发）。

核心机制（三规则，无时钟参与）：

```
双角色键 keyDown（仅当浮动候选窗正在显示候选时挂起；开关开）
  → 存 pending(keyCode, 原始事件)，本次按下不派发
  ├─ 期间另一 keyDown 命中「已绑定和弦键」（动作快捷键 / 翻页，含 Sym+O/P）
  │    → pending 作废，和弦正常执行 ✅（AC1/2/3）
  ├─ 期间另一 keyDown 未命中和弦
  │    → 立即重放 pending（补发选字），再继续处理新键（AC7，与旧行为同序）
  └─ pending 键 keyUp
       → 重放 pending：走"该键按下时本应走的完整派发链"（选字 / 符号切换 / 转发），
         选字发生在抬指瞬间（AC4）
```

**重放 = 抽取的局部派发函数**（不是重入整个 `onKeyDown`）：重放跳过 latch 状态机、
长按符号挂起、IME 唤起、二次挂起——只跑「动作快捷键 → 候选/符号面 → 转发」段，
避免 Alt 双击锁计时器被重放二次触发等副作用（ADR-0005 方案 F 否决理由）。

## 2. 关键设计点（落到具体文件/类）

### 2.1 挂起判定（谁需要挂起）
`HardwareShortcutResolver` 新增两个纯函数（走既有解析路径 + 既有缓存）：
- `isDualRoleBareKey(kp: KeyPress): Boolean`——kp 命中 candidate1-5 / pageNext /
  pagePrev 的**裸按绑定**（`HardwareChord.split` 前缀为空），且 keyCode 满足
  `HardwareChord.isChordModifierKeyCode`。**动态计算，不写死机型**（C2）。
- `isBoundChordSecondKey(kp: KeyPress): Boolean`——kp 是「已绑定和弦」的第二键：
  动作快捷键（`resolveAction`）或翻页（`resolvePaging`）命中。和弦匹配复用
  既有快照机制（Sym/Fn 靠 `symDown/fnDown`，Alt/Shift 靠 metaState 侧别位），
  挂起键物理按住期间这些状态天然就位。

挂起条件（全部满足才挂起，其余一律走旧路径，行为零变化）：
1. 物理键盘模式 + keyDown + repeatCount==0（C5）
2. 总开关开（`modTapEnabled`，默认 true）
3. `candidatesView?.isShowingCandidates() == true`（**只有候选显示时才挂起**——
   无候选时全键即时，最大限度收窄行为变化面）
4. `isDualRoleBareKey` 为真
5. 该键未被 caps-latch 状态机接管（`capsLockEnabled && isCapsLatchKeyCode`，
   见 2.3）

### 2.2 挂起状态与裁决（`FcitxInputMethodService`）
服务层新增 `private var pendingModTap: Pair<Int, KeyEvent>?`（keyCode + 原始
effectiveEvent）。`HardwareChord` **不改动**——它的 `pendingSymbolTapKeyCode` 继续
只管符号键（两状态并存、互不干扰；同键两身份时符号键判定在前，与现状一致）。

- **keyDown 挂起点**：物理分支 `handleHardwareChordTapHold` 之后、
  `candidatesView.handleHardwareCandidateShortcut` 之前。命中 2.1 五条件 → 存
  pending，记录 `consumedHardwareCandidateShortcutKeys`，return true。
  若已有旧 pending（如 Alt 未松手又按 Shift）：先重放旧 pending 再存新的
  （不许静默丢失，AC 隐含要求）。
- **第二键裁决**：keyDown 主链早期（latch 状态机之后、动作快捷键之前）：
  有 pending 时——本键即 pending 键（双击第二下）→ 交给 latch 状态机正常处理
  （双击锁在此消费，pending 保持待作废）；否则 `isBoundChordSecondKey` →
  作废 pending；都不是 → 立即重放 pending，再继续本键派发。
  ⚠️ 双击锁第二下由 `dispatchAltLatchDown` 先行消费（return true），不会走到
  裁决分支，pending 随之在消费点作废。
- **keyUp 重放**：`onKeyUp` 中、既有 `consumeSymbolTap` 块之前：pending 键抬起 →
  取事件 → 调 `dispatchTapReplay(event)` → return true。
- **会话收尾**：与 `HardwareChord.reset()` 同点清空 pending（防僵死）。

### 2.3 latch 共存（G2）
- **Alt-latch**：`dispatchAltLatchDown` 双角色键第一击本就 fall-through → 挂起生效；
  双击第二击 / 锁定态解锁击被状态机消费 → 在消费点作废 pending（防"锁成功还补选字"）。
  有候选时的"第一击=选字、第二击=锁"结果与改动前一致（选字时机从按下移到抬指）。
- **Caps-latch（Shift）**：状态机自带 300ms 延迟补发 + 和弦作废（已是 Mod-Tap）→
  `capsLockEnabled && isCapsLatchKeyCode(keyCode)` 时**跳过新挂起**，零双重延迟
  （AC5；Q4 接受风险：状态机未启用时 Shift 键走通用挂起路径，行为仍与改动前一致）。
- **符号键**：既有 `armSymbolTap` 原样（Q1），不重复挂起。

### 2.4 重放函数
从 `onKeyDown` 抽取 `private fun dispatchTapReplay(effectiveEvent: KeyEvent): Boolean`，
内容 = 现 2009 行起至 `forwardKeyEvent` 止的段落（picker 选择 → 动作快捷键 →
物理分支候选/符号面 → 转发），入参带「抑制二次挂起」语义（重放时 2.1 条件 3 天然
不满足或显式短路，防死循环）。**T3 先做纯重构**（原函数原样搬入，行为不变，CI 验证），
T4 再接挂起逻辑——两个任务各自可独立过 CI。

### 2.5 设置项
- `AppPrefs.HardwareKeyboard` 新增 `modTapEnabled: PBool`（默认 `true`），
  key 如 `"hw_mod_tap_enabled"`。
- 「物理键盘」页（`HardwareKeyboardSettingsFragment`）新增
  `SwitchPreferenceCompat` 一行：标题「和弦键轻点挂起（Mod-Tap）」+ 摘要说明
  （strings.xml 新增两条）。
- 该布尔**每派发直接 `getValue()` 读取**（不进 resolver 缓存）——顺带不受 B1
  影响，无需任何额外逻辑（C4 的自然实现）。

## 3. 影响面

| 模块 | 改动 | 风险 |
|---|---|---|
| `input/FcitxInputMethodService.kt` | 挂起点 / 第二键裁决 / keyUp 重放 / 抽取 `dispatchTapReplay` / reset 清理（+~120 行） | 中：派发链核心；靠 T3 纯重构先行 + 全 AC 真机回归 |
| `input/candidates/HardwareShortcutResolver.kt` | +2 纯函数（复用既有解析/缓存） | 低 |
| `data/prefs/AppPrefs.kt` | +1 PBool | 极低 |
| `ui/main/settings/behavior/HardwareKeyboardSettingsFragment.kt` | +1 开关行 | 极低 |
| `res/values/strings.xml` | +2 文案 | 极低 |
| `data/prefs/HardwareChord.kt` | **不改**（符号键 tap-hold 原样） | — |
| `input/InputView.kt` / `input/CandidatesView.kt` | **不改**（重放复用既有派发链） | — |
| `lib/`（fcitx5 C++） | **不改** | — |
| 虚拟键盘模式 | 挂起条件含"物理模式"，不受影响 | 低（AC10 抽查） |

## 4. 备选方案与否决原因

| 方案 | 否决原因 |
|---|---|
| A. 时间阈值（按下等 N ms 再裁决） | 每次和弦白等 N ms，手感变肉；用户已确认不要调参项 |
| B. 逐键开关 | 7~9 个开关无必要；默认值由规则推出（G1 前讨论已定） |
| C. 排除 Shift/Alt 不做 | Shift 由 caps 状态机跳过规则覆盖；Alt 正是用户 5 号候选键，必须做 |
| D. 重放 = 重入整个 `onKeyDown` | Alt 双击锁计时器/长按符号/IME 唤起被二次触发，副作用不可控 → 改为抽取局部段（2.4） |
| E. 挂起状态塞进 `HardwareChord` | 该对象是 data/prefs 层纯状态机且只管符号键既有语义；挂起需持有 KeyEvent 且属派发层职责 → 状态留在服务层 |
| F. 无候选时也挂起 | 行为变化面扩大、纯修饰键场景多一次延迟 → 仅候选显示时挂起（2.1 条件 3） |

## 5. 与既有 ADR 的关系
- ADR-0002（物理模式浮动候选 + topbar）：本 feature 的挂起/重放都以浮动
  CandidatesView 为候选面，前提一致。
- ADR-0003（修饰键三层防御）：重放走既有 `withInjectedModifiers` 链，不新增
  metaState 来源。
- ADR-0004（键预设批量重放语义）：预设切换后双角色集合动态重算（2.1），
  与批量重放语义兼容。
- **新 ADR-0005**：事件驱动挂起-重放 的裁决规则与 latch 共存约定。
