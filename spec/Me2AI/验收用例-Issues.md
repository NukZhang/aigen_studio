# Issue 验收用例（Given / When / Then）

## I1：新增 UI_DESIGNING / UI_CONFIRMED
- Given：UNDERSTANDING_CONFIRMED
  When：调用 ui/design
  Then：stage=UI_DESIGNING
- Given：UNDERSTANDING（未确认）
  When：调用 ui/design
  Then：409（REQ Gate 未通过）

## I2：解析门控协议（nextAction/questions/contract）
- Given：信息不足
  When：understanding/parse
  Then：ASK_CLARIFICATION + questions(3-6) + options(2-4)
- Given：信息充分
  When：understanding/parse
  Then：READY_FOR_CONFIRM + contract(必填块齐全)

## I3：Conversation 扩展字段落库
- Given：confirm 理解
  When：understanding/confirm
  Then：me2aiContractJson 落库 + me2aiConfirmedAt 非空
- Given：verify 完成（pass/fail）
  When：implementation/verify
  Then：evidenceManifestPath 落库 + gateStatusJson 更新

## I4：澄清表单渲染
- Given：ASK_CLARIFICATION
  When：打开 Workspace
  Then：结构化问题表单可提交

## I5：契约卡确认 UI
- Given：READY_FOR_CONFIRM
  When：用户确认需求
  Then：REQ=PASS 且 stage=UNDERSTANDING_CONFIRMED

## I6：UI 设计 API + Prototype 预览
- Given：UNDERSTANDING_CONFIRMED
  When：ui/design
  Then：返回 UI_Spec(符合 schema) + prototypePath/url

## I7：UI 确认 Gate
- Given：UI 未确认
  When：implementation/plan
  Then：409（UI Gate 未通过）
- Given：UI 确认
  When：ui/confirm
  Then：stage=UI_CONFIRMED

## I8：Implementation Plan
- Given：UI_CONFIRMED
  When：implementation/plan
  Then：返回 plan(scope/nonGoals/filesToChange/verifications)

## I9：Evidence Manifest（成功/失败都要）
- Given：plan 已存在
  When：implementation/verify 验证失败
  Then：result=FAIL 仍生成 manifest（含失败点）

## I10：Preview Gate（必须绑定 evidence）
- Given：无 evidence
  When：preview/start（未开启 allowUnverifiedPreview）
  Then：409（Preview Gate 未通过）
