# Editor Layout Refinements Design

## Goal

根据最新页面截图反馈，微调首页的顶部栏、Agent mode 文案、左右分栏宽度控制和用户指令输入框行为。

## Confirmed Requirements

- 顶部栏不是 Figma 外壳，不应保留左右 icon 或分享按钮。
- 顶部栏只显示 `Agent Editor` 标题。
- Agent mode 下拉展示四个选项：`ReAct`、`Planning`、`Reflexion`、`Supervisor`。
- 下拉 value 继续使用后端枚举：`REACT`、`PLANNING`、`REFLEXION`、`SUPERVISOR`。
- 左右分栏之间可以拖动分界线调整比例。
- 用户指令文本框按内容自动调整高度，并限制最大高度，超过后滚动。

## Design

顶部栏保留深色横条和居中标题，移除 `brand-dot`、`ai-badge`、右侧 icon 与 `Share` 按钮。这样页面不再像带着 Figma 浏览器外壳的截图，而是应用自己的标题栏。

主体继续使用 Flexbox 双栏。在 `.document-column` 与 `.assistant-panel` 之间插入 `workspace-resizer`。拖动时通过 JavaScript 更新 CSS 变量 `--assistant-width`，控制右侧固定栏宽度。桌面端允许宽度在 320px 到 560px 之间变化，并保证左侧文档区至少保留 520px。移动端布局改为上下堆叠，隐藏拖拽手柄。

composer 中的 `instructionInput` 仍是 textarea。新增 `autoResizeInstructionInput()`，在输入、初始化和发送清空后运行：先把高度设回 auto，再按 `scrollHeight` 计算目标高度，最大高度限制为 140px。超过上限后 textarea 内部滚动，避免撑坏右侧对话区布局。

## Testing

更新 `DemoPageTemplateTest` 覆盖：

- header 只暴露 `Agent Editor`，不再包含 `brand-dot`、`ai-badge`、`share-button`。
- Agent mode 展示 `ReAct`、`Planning`、`Reflexion`、`Supervisor`，且不再展示 `Editor`。
- 模板包含 `workspace-resizer`、`initWorkspaceResizer` 和 `--assistant-width`。
- 模板包含 `autoResizeInstructionInput` 和 `max-height: 140px;`。
