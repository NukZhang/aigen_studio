# UI Prototype AST Conversion + Backend Fix Design

**Goal**
将 UI 原型 HTML 完整转换为可运行的 Vue SFC：把所有 `on*` 事件转换为 Vue 事件绑定，脚本从 `<script>` 迁移到 `<script setup>` 并在 `onMounted` 中初始化。同步修复后端生成中 `Character` 命名冲突与 `Long`→`int` 类型错误。

**Problem Summary**
当前生成会把原型 HTML（含 `<script>`）直接注入 `App.vue`，导致 Vue 编译报错（模板中出现 `<script>`）。同时生成的后端代码使用实体名 `Character`，与 `java.lang.Character` 产生歧义；并存在 `Long`→`int` 强转编译错误。

**Architecture**
新增 `HtmlToVueTransformer` 与 `JsAstRewriter`：
- `HtmlToVueTransformer` 用 jsoup 解析 HTML，抽取 `<body>` 为模板，抽取 `<style>` 为 SFC 样式，所有 `on*` 属性转换为 Vue 事件（如 `onclick`→`@click`）。
- `JsAstRewriter` 用 Rhino 解析脚本 AST，拆分“声明语句”和“初始化语句”。声明（函数/变量/类）置于 `<script setup>` 顶层，初始化语句放入 `onMounted(() => {...})`。如变量声明含 initializer，会拆成 `let x;` + `x = ...`（必要时从 `const` 降级为 `let`）。
- `FrontendScaffoldService` 负责调用转换器并写入 `App.vue`，同时补齐 Vue 入口骨架。

后端修复两层：
1) 更新 `PromptTaskService` 生成提示词，禁止实体命名为 `Character`，要求 ID 类型一致（实体/Mapper/Service 层一致）。
2) 生成后可选自动修补：扫描后端代码，将 `Character` 命名冲突改为 `PersonalityCharacter` 或生成显式 import；修复 `selectCount` 的 Long→int 强转错误。

**Components & Data Flow**
```
PromptTaskService.generateCode(...)
  -> FrontendScaffoldService.ensureVueScaffoldAndInjectPrototype(...)
       -> HtmlToVueTransformer.transform(html)
            -> JsAstRewriter.rewrite(js)
       -> write App.vue
  -> BackendGenerationFixer (optional)
```

**Error Handling**
- HTML 解析失败：回退到最小可运行 App.vue 并记录日志。
- JS AST 解析失败：回退为静态模板（不注入脚本），保证页面可渲染。
- 生成后修补失败：记录错误但不中断生成流程。

**Testing Strategy**
- `HtmlToVueTransformerTest`: 验证 `on*` 转换为 Vue 事件，模板无 `<script>/<style>`。
- `JsAstRewriterTest`: 验证函数/变量声明位于顶层，初始化语句进入 `onMounted`。
- `FrontendScaffoldServiceTest`: 端到端输出 `App.vue` 结构稳定。
- `PromptTaskServiceTest`: 提示词包含 `Character` 命名禁用与类型一致性约束。

**Dependencies**
- `org.jsoup:jsoup`
- `org.mozilla:rhino`

**Trade-offs**
AST 转换能最大限度保留交互，但对复杂脚本仍可能需要人工修正；为可靠性，提供静态回退。
