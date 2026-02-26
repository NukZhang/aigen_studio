# 平台流程（SDAC 融入版 v1）—— 需求理解 → UI 设计 → 代码实现 → 服务预览

更新时间：2026-02-25

> 目标：把平台的 4 个动作变成 **可执行产物 + 强制 Gate + 证据链**。
> 本文件是平台级 Me2AI（最高优先级、只读），AI 不得改写含义。

---

## 1. 目标流程（四段 + Gate）

### 1.1 需求理解（REQ Gate）
输出产物（结构化）：
- Me2AI_Contract（需求契约卡 JSON）
- Clarification_Questions（澄清问题 JSON，仅在需要澄清时）

REQ Gate 通过条件：
- nextAction=READY_FOR_CONFIRM 且用户确认契约卡（understandingConfirmed=true）

### 1.2 UI 设计（UI Gate）
输出产物：
- UI_Spec（页面/路由/组件/交互/状态/权限/错误态/空态）
- UI_Prototype（可预览原型：静态页面/iframe/截图均可）
- UI_Acceptance_Map（Contract 功能 → UI 入口映射，防漂移）

UI Gate 通过条件：
- 用户确认 UI_Spec + Prototype

### 1.3 代码实现（IMP Gate）
输出产物：
- Implementation_Plan（最小可完成集 + 非目标 + 文件清单 + 验证命令）
- Evidence_Manifest（证据链：命令、结果摘要、产物链接、失败点；成功/失败都必须）
- AI2AI_State_Update（facts only）

IMP Gate 通过条件：
- Evidence_Manifest 已生成并与本次实现绑定

### 1.4 服务预览（PREVIEW Gate）
输出产物：
- Preview_Contract（URL/端口/启动方式/版本/已知限制/测试账号与种子数据）
- Preview_Snapshot（关键日志摘要 + 运行状态快照）

PREVIEW Gate 通过条件：
- 预览必须绑定 Evidence_Manifest 版本（evidenceRef）

---

## 2. 必须新增/修改的要求（可拆 Issue）

### A) 状态机与 Gate
- R-FLOW-001：新增 `UI_DESIGNING`、`UI_CONFIRMED`，插入：UNDERSTANDING_CONFIRMED → UI_DESIGNING → UI_CONFIRMED → CODE_GENERATING。
- R-FLOW-002：所有“进入下一段”的 API 必须在后端做 Gate 校验。
- R-FLOW-003：REQ Gate：未确认契约卡不得进入 UI_DESIGNING。
- R-FLOW-004：UI Gate：未确认 UI 不得进入 CODE_GENERATING。
- R-FLOW-005：IMP Gate：未生成 Evidence_Manifest 不得进入 PREVIEWING（默认阻断）。

### B) 数据模型（Conversation 扩展）
- R-DATA-001：me2aiContractJson / me2aiConfirmedAt
- R-DATA-002：clarificationQuestionsJson
- R-DATA-003：uiSpecJson / uiPrototypePath / uiConfirmedAt
- R-DATA-004：implementationPlanJson
- R-DATA-005：evidenceManifestPath
- R-DATA-006：gateStatusJson（REQ/UI/IMP/PREVIEW）

### C) API（最小集合）
- R-API-REQ-001：POST /conversations/{id}/understanding/parse
- R-API-REQ-002：POST /conversations/{id}/understanding/confirm
- R-API-UI-001：POST /conversations/{id}/ui/design
- R-API-UI-002：POST /conversations/{id}/ui/confirm
- R-API-IMP-001：POST /conversations/{id}/implementation/plan
- R-API-IMP-002：POST /conversations/{id}/implementation/verify
- R-API-PRV-001：POST /conversations/{id}/preview/start（必须传 evidenceRef）

### D) 前端（Workspace）
- R-FE-001：UNDERSTANDING 阶段按 nextAction 渲染澄清表单/契约卡确认页
- R-FE-002：新增 UI 设计 Tab（Spec viewer + Prototype preview + Confirm）
- R-FE-003：新增 实现 Tab（Plan viewer + Verify + Evidence summary）
- R-FE-004：预览面板展示 Preview_Contract，并显示绑定 evidence；Gate 不通过则阻断

### E) 证据链与审计
- R-EVD-001：每次生成必须有 Evidence_Manifest（成功/失败都要）。
- R-EVD-002：Evidence_Manifest 必须包含 inputs hash / verifications / result / artifacts / timestamps。
- R-EVD-003：若继续使用 IR：IR 必须通过 `docs/ir-schema.json` 校验才允许进入实现。
