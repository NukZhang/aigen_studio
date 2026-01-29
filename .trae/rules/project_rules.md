---
description: PoC 平台的 AICoding 执行规则
alwaysApply: true
enabled: true
---

# 0. 总原则
- 本项目采用 Spec 驱动的 AICoding：Me2AI 表达意图与约束，AI2AI 负责执行计划与变更记录
- AI 不得修改 spec/Me2AI 下任何文件
- AI 的一切生成必须可回放：输入 IR + openapi + runtime_assets + 规则版本 => 输出一致
## 定位
- Me2AI 是“人类维护的意图层”，用于约束 AI 的行为边界
- Me2AI 只描述 What：目标、范围、约束、验收；不写具体实现细节

## 与 AI2AI 的边界
- AI2AI 由 AI 维护，用于任务分解、生成计划、变更记录、门禁结果
- 任何偏离 Me2AI 的生成行为都视为违规：必须停止并回报差异

## 本 PoC 的核心闭环
- 需求录入与 IR 编辑
- 触发作业并调用 iFlow 执行生成
- OpenAPI breaking 检查 + TS SDK 生成
- GitLab CI 触发与 evidence 证据链落盘
- 可选：Gravitee 灰度策略导入导出与发布联动

# 1. 目录与权威来源
- 需求与页面目标态：libraries/needs/<project>/ir.json 为权威
- 接口契约：libraries/needs/<project>/openapi.yaml 为权威
- 运行时资产：deliverables/runtime/runtime_assets.json 为权威
- evidence：libraries/versions/evidence/<runId>/manifest.json 必须生成

# 2. 门禁
- 生成前必须校验 IR 与 runtime_assets（scripts/validate_*.py）
- 生成后必须：
  - 更新 openapi.yaml
  - 执行 OpenAPI breaking-change 检查并写入 evidence.gates
  - 生成 TS SDK 并写入 evidence.outputs
  - 触发 GitLab pipeline 并写入 evidence.gates.release

# 3. iFlow 执行安全策略
- 默认：
  - permissionMode = MANUAL
  - fileAccess = false
- PoC 可切换为 SELECTIVE/AUTO，但必须满足：
  - fileAccess = true 时，必须设置 allowedDirs 仅包含工作目录
  - 所有外部调用必须通过 MCP（gitlab/gravitee/pipeline），不得裸写脚本去访问敏感系统
  - token 仅从环境变量读取，禁止写入仓库

# 4. 变更策略
- 每次作业只允许修改：
  - apps/frontend
  - apps/backend
  - libraries/needs/<project>
  - deliverables/runtime
  - sdks/ts
  - libraries/versions/evidence/<runId>
- 禁止修改 spec/Me2AI 与 schemas

# 5. 提交策略
- 必须使用分支：feature/<runId>
- 每次作业最少两次提交：
  - commit 1: 生成代码与契约
  - commit 2: evidence 与报告
