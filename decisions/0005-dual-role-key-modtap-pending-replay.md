# ADR-0005: 双角色键 Mod-Tap 采用「事件驱动挂起-重放」，仅候选显示时生效

- 状态：accepted（2026-07-21，feature `20260721-macrotap-modtap-chord-conflict`）
- 关联：ADR-0002（浮动候选面）、ADR-0003（修饰键防御）、`specs/20260721-macrotap-modtap-chord-conflict/`

## 背景

底排候选槽位键常同时是和弦修饰键（Sym/Fn/Alt_L/Alt_R/Shift_L/Shift_R）。裸按在
keyDown 立即派发选字，导致候选显示期间 `修饰键+字母` 和弦先误敲一个候选字。
代码库已有三套裁决机制：符号键 tap-hold（挂起-松手补发）、Shift 双击/长按锁大写
（自带 300ms 延迟补发 + 和弦作废）、Alt 双击锁（第一击 fall-through、第二击消费）。
候选选字不在任何仲裁内。

## 决策

1. **事件驱动，无时钟**：挂起由 keyDown 触发，裁决只由「另一 keyDown」或
   「pending 键 keyUp」两个物理事件完成。无超时、无新调参项（AC6：长按任意
   时长再按字母仍是和弦）。
2. **仅候选显示时挂起**：`candidatesView.isShowingCandidates()` 为假时一切键
   即时派发，行为零变化。最小化行为变化面，也把"无候选时修饰键的既有语义"
   （转发引擎、键帽符号等）完全排除在外。
3. **重放 = 抽取的局部派发段**：`dispatchTapReplay` 只含「picker 选择 → 动作
   快捷键 → 候选/符号面 → 转发」，**不重入** latch 状态机 / 长按符号 / IME 唤起，
   防止 Alt 双击计时器被重放二次触发（备选 D 否决）。
4. **挂起状态留在服务层**（`pendingModTap: Pair<Int, KeyEvent>?`）：需要持有
   KeyEvent 且属派发层职责；`HardwareChord` 保持 data/prefs 层纯状态机，其
   `pendingSymbolTapKeyCode` 语义不动，两挂起并存互不干扰（同键双身份时符号键
   判定在前，与现状一致）。
5. **第二键裁决表 = 动作快捷键 ∪ 翻页键**（用户确认：翻页 Sym+O/P 必须共存）；
   未绑定和弦的第二键 → 先重放选字再处理新键（与旧行为同序）。
6. **latch 共存约定**：
   - Alt-latch：第一击 fall-through 后挂起生效；双击第二击/解锁击被状态机消费
     时**同步作废 pending**（防"锁成功还补选字"）。
   - Caps-latch：状态机自带延迟补发 → `capsLockEnabled && isCapsLatchKeyCode`
     时跳过新挂起（零双重延迟）；状态机未启用时走通用挂起路径。
7. **旧 pending 被新 pending 顶替时立即重放旧的**（不许静默丢失）。
8. 总开关 `modTapEnabled`（默认开）直接 `getValue()` 读取，不进 resolver 缓存
   （顺带规避 ADR 待修的 B1 缓存失效缺陷，无额外逻辑）。

## 备选与否决

- 时间阈值（hold N ms 才算修饰）：和弦白等 N ms，手感变肉。
- 逐键开关：默认值应由规则推出，开关泛滥。
- 重放重入整个 `onKeyDown`：副作用不可控（见决策 3）。
- 挂起塞进 `HardwareChord`：层职责错位（见决策 4）。
- 无候选也挂起：行为变化面扩大，无收益（见决策 2）。

## 后果

- 派发链（`FcitxInputMethodService`）复杂度上升：新增 pending 状态 + 裁决分支 +
  重放函数；缓解：T3 先做纯重构抽取（行为不变），T4 再接逻辑，各自独立过 CI。
- 双角色键选字时机从"按下"移到"抬指"（仅候选显示时）：抬指本身需数十 ms，
  补发同帧，体感无感（AC4 真机确认）。
- 依赖 B1（缓存失效）修复前的临时纪律：真机测试改配置后强停应用。
