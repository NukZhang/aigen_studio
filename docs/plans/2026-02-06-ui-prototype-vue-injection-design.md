# UI Prototype Vue Injection Design

**Goal**
确保“UI 原型 HTML 自动转换并写入 `frontend/src/App.vue` 作为初始页面”，并在代码生成不完整时自动补齐 Vue 3 骨架，使前端始终可运行。

**Problem Summary**
当前代码生成阶段完全依赖模型输出，导致可能只生成 `ui-prototype/index.html`，而缺失 `frontend/index.html` 和 Vue 入口文件。即使生成了 Vue 项目，`App.vue` 也可能只是空壳（`router-view`），UI 原型未被注入，最终预览无法体现设计稿。

**Solution Overview**
在 `CodeGenerationService.generateCodeForConversationAsync` 中，iFlow 生成完成后新增“前端骨架修复 + UI 原型注入”步骤。该步骤由一个独立服务 `FrontendScaffoldService` 实现，负责：
- 校验并补齐 Vue 3 最小可运行骨架（`index.html`、`src/main.ts`、`src/App.vue`、`vite.config.ts`、`package.json`）。
- 将 UI 原型 HTML 转换为 Vue SFC（`App.vue`），以原型为初始页面。
- 若 UI 原型为空，则保留默认 `App.vue`。

**Architecture**
新增服务层组件，不改动 iFlow SDK 集成逻辑。后置步骤保证最终产物一致性，降低对模型输出的依赖。

```
PromptTaskService.generateCode(...)  ->  FrontendScaffoldService.ensureVueScaffoldAndInjectPrototype(...)
                                                      |
                                                      -> ensurePreviewScripts(...)
```

**Components**
- `FrontendScaffoldService`
  - `ensureVueScaffoldAndInjectPrototype(frontendDir, uiPrototypeHtml, projectName)`
  - `ensureIndexHtml(frontendDir, title)`
  - `ensureMainTs(frontendDir)`
  - `ensureViteConfig(frontendDir)`
  - `ensurePackageJson(frontendDir)`
  - `writeAppVueFromPrototype(frontendDir, uiPrototypeHtml)`

**HTML → Vue 转换规则**
- 从 UI 原型 HTML 中提取 `<body>...</body>` 作为 `<template>` 内容。
- 若存在 `<style>`，提取其内容写入 `<style>` 块。
- 若无法提取 `<body>`，则回退为默认模板（`<div class="app">`）。
- 不写入原型中的 `<head>`（由 `index.html` 提供基本 meta/title）。

**Error Handling**
- 若文件系统操作失败，记录错误并抛出 `RuntimeException`，由 `CodeGenerationService` 捕获并更新对话状态为 `FAILED`。
- 在注入阶段只覆盖 `App.vue` 当 `uiPrototypeHtml` 非空，确保需求明确时必定生效。

**Testing Strategy**
新增 `FrontendScaffoldServiceTest`（JUnit + `@TempDir`）：
- 验证缺失 `index.html` / `main.ts` / `App.vue` 时能自动创建。
- 验证注入后 `App.vue` 的 `<template>` 包含原型 `<body>` 内容，`<style>` 包含原型样式。
- 验证 `index.html` 含 `#app` 且引用 `/src/main.ts`。

**Trade-offs**
此方案在 UI 原型存在时覆盖 `App.vue`，确保初始页面与设计稿一致，但可能覆盖模型生成的更复杂 Vue 页面。若未来需要保留复杂页面，可添加“仅在 App.vue 为占位符时覆盖”的策略。
