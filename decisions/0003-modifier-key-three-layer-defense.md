# ADR-0003: 修饰键可信源 = 应用自跟踪 + 权威清洗，不信任框架 metaState

- 状态: accepted（**既有决策的事后重建**，来源：`FcitxInputMethodService` 的 `withInjectedModifiers`/`updatePhysicalModifiers` 注释与 Alt 相关 bug 修复史）
- 日期: 2026-07-21

## 背景
Android 框架的修饰键处理在实体键盘 + 定制 ROM 环境下不可靠，历史上至少三类污染源：
1. 长按 Alt 触发框架原生 sticky/lock，Alt 泄漏到后续按键
2. 部分 ROM（Q25）在长按 Alt 时合成第二次 `KEYCODE_ALT` down 事件
3. 引擎拒收后回吐裸修饰键事件，编辑器的自身修饰键跟踪态被置位，
   污染后续 IME 合成的 DPAD 事件（"移动光标变成选中文字"）

## 决策（三层防线，各自独立、互相兜底）
1. **物理跟踪层**：`updatePhysicalModifiers` 从原始事件流自行维护
   `physicalAltDown/physicalShiftDown/physicalCtrlDown`，作为组合键匹配的唯一事实源
   （在捕获页早退**之前**更新，防止状态卡死 → 每个字母都变和弦）
2. **应用 latch 层**：双击 Alt 锁定 / Shift 长按大写锁定是 app 级状态机（`HardwareKeyDispatch`），
   与框架 sticky 完全脱钩——框架锁不锁无所谓，只认用户手势意图
3. **权威清洗层（最终防线）**：`withInjectedModifiers` 对每个转发事件
   剥掉框架私自附加的 `META_ALT_ON`，除非 Alt 物理按着或 app latched

配套纪律：裸修饰键拒收后**不回吐**给编辑器（只清 metaState）；
长按 Alt 释放时主动 `clearMetaKeyStates`（belt-and-suspenders）。

## 后果
- 任何新修饰键/组合键功能必须走这三层，禁止直接读 `event.metaState` 做业务判断
- 涉及 Alt 的改动真机回归清单：长按 Alt、双击 Alt、Alt+数字选词、切换 App 后首键
