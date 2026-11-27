# ADR-0002: 物理键盘模式下保留 InputView 只显示顶栏，候选面交给悬浮窗（而非整体隐藏）

- 状态: accepted（**既有决策的事后重建**，来源：`InputDeviceManager.setupInputViewEvents` 代码注释 + 走读推断）
- 日期: 2026-07-21

## 背景
物理键盘模式下需要隐藏软键盘。最直觉的做法是把 InputView 整体 `GONE`。
但 InputView 上挂着顶栏（状态图标、设置入口等），整体隐藏会把顶栏一起藏掉；
且候选展示面在物理模式下应该是悬浮窗（不占键盘区），而不是键盘栏内的横向候选条。

## 决策
- 物理模式下 InputView 保持 VISIBLE，只按预设路由键盘面：Passport 显示自定义键盘行，其余预设藏软键盘面、留顶栏（`applyPhysicalKeyboardPresetState`）
- 候选面二选一由 `isVirtualKeyboard` 决定：虚拟=InputView 内横向候选栏；物理=悬浮 `CandidatesView`
- 两面的事件开关互斥（`handleEvents`），由 InputDeviceManager 集中管理

## 后果
- 好处：顶栏常可用；悬浮窗不依赖键盘区布局，适配 1:1 屏
- 代价：同一时刻存在两个候选面候选者，**物理模式下的按键探测顺序必须"悬浮窗优先"**
  （详见 CODE_MAP 链路①第 12 步），否则绑定冲突的键（如 Elite 的 Fn 兼 candidate3）会被错面吃掉
- 附带脆弱点：InputView 的 `noActiveInput` 守卫在物理模式下恒真，符号键切换必须走无守卫路径（`handleHardwareSymKey`）
