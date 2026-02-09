# 图片预览 UI 提示词模板（迁移说明）

提示词已统一改为后端模板文件维护，请维护以下文件：

- `backend/src/main/resources/prompt-templates/ui-prototype-prompt.md`
- `backend/src/main/resources/prompt-templates/understanding-prompt.md`
- `backend/src/main/resources/prompt-templates/code-generation-prompt.md`

说明：
- `UIPrototypeService` 从 UI 模板读取 `PRIMARY/COMPACT/LEGACY` 三段提示词。
- `PromptTaskService` 从 `understanding/code-generation` 模板读取对应提示词。
- 支持变量替换：
  - UI：`{{PROJECT_NAME}}`、`{{USER_REQUIREMENT}}`、`{{AI_UNDERSTANDING}}`
  - 理解：`{{USER_REQUIREMENT}}`
  - 代码生成：`{{IR_CONTENT}}`、`{{OUTPUT_PATH}}`
