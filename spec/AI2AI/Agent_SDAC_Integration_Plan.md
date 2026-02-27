# AIGen Studio Agent SDAC 集成改造计划

> 本文档为将现有智能体改造为 SDAC 模式的详细执行计划，用于指导后续开发会话

## 一、背景分析

### 1.1 当前状态

| 维度 | 现状 | SDAC 要求 |
|------|------|-----------|
| **Schema 验证** | ❌ 智能体产出未经验证 | 需要验证 UI Spec / Implementation Plan |
| **Evidence Manifest** | ❌ 无证据记录 | 每次执行生成 Evidence Manifest |
| **Gate 检查** | ❌ 无质量门禁 | 触发 REQ/UI/IMP/PREVIEW 门禁 |
| **AI2AI 状态更新** | ❌ 无标准化输出 | 输出 AI2AI State Update 文档 |

### 1.2 已就绪的 SDAC 基础设施

现有 `SdacResourceService` 已提供：
- `validateUiSpec()` - UI 规格验证
- `validateImplementationPlan()` - 实现计划验证
- `validateEvidenceManifest()` - Evidence 验证
- `loadDefaultGateStatuses()` - 门禁默认状态加载
- `renderAi2AiStateUpdate()` - AI2AI 状态更新模板渲染

### 1.3 改造目标

1. **Agent 执行流程 SDAC 化**：每个智能体执行后生成标准化 Evidence
2. **产出物验证**：Agent 产出经过 SDAC Schema 验证
3. **门禁集成**：在关键节点触发质量门禁检查
4. **可追溯性**：生成 AI2AI 状态更新文档，记录变更轨迹

---

## 二、改造架构

### 2.1 目标架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Agent SDAC 集成架构                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   AgentOrchestrator                      │   │
│  │                         │                                 │   │
│  │            ┌────────────┴────────────┐                   │   │
│  │            ▼                         ▼                   │   │
│  │   ┌────────────────┐       ┌────────────────┐            │   │
│  │   │  Supervisor    │       │   Workers      │            │   │
│  │   │    Agent       │       │ (Analyst/      │            │   │
│  │   │                │       │  Designer/     │            │   │
│  │   │                │       │  Developer)    │            │   │
│  │   └────────────────┘       └────────────────┘            │   │
│  │            │                         │                   │   │
│  │            └────────────┬────────────┘                   │   │
│  │                         ▼                                 │   │
│  │               ┌────────────────┐                         │   │
│  │               │ SdacAgentService │  🆕 SDAC 包装层        │   │
│  │               │                  │                        │   │
│  │               │ - Evidence 生成   │                        │   │
│  │               │ - Schema 验证     │                        │   │
│  │               │ - Gate 检查       │                        │   │
│  │               │ - AI2AI 输出      │                        │   │
│  │               └────────────────┘                         │   │
│  │                         │                                 │   │
│  │            ┌────────────┴────────────┐                   │   │
│  │            ▼                         ▼                   │   │
│  │   ┌────────────────┐       ┌────────────────┐            │   │
│  │   │SdacResourceService│    │ Evidence Store │            │   │
│  │   │ (已存在)          │    │ (文件系统)      │            │   │
│  │   └────────────────┘       └────────────────┘            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 文件结构规划

```
backend/src/main/java/com/aigen/studio/
├── agent/
│   ├── AgentOrchestrator.java        # 改造：集成 SDAC
│   ├── SupervisorAgent.java          # 改造：产出 SDAC 验证
│   ├── AnalystAgent.java             # 改造：产出 SDAC 验证
│   ├── DesignerAgent.java            # 改造：产出 UI Spec 验证
│   ├── DeveloperAgent.java           # 改造：产出 Impl Plan 验证
│   ├── AgentState.java               # 改造：添加 SDAC 字段
│   ├── AgentEvent.java               # 保持不变
│   └── SdacAgentContext.java         # 🆕 SDAC 上下文
│
├── sdac/                              # 🆕 SDAC 模块
│   ├── SdacAgentService.java         # SDAC 智能体包装服务
│   ├── EvidenceGenerator.java        # Evidence 生成器
│   ├── GateEvaluator.java            # 门禁评估器
│   └── Ai2AiReporter.java            # AI2AI 报告生成器
│
├── service/
│   ├── SdacResourceService.java      # 已存在，可能扩展
│   └── MultiAgentService.java        # 改造：集成 SDAC 输出
│
└── dto/
    ├── AgentEvidenceDTO.java         # 🆕 Agent Evidence DTO
    ├── GateResultDTO.java            # 🆕 门禁结果 DTO
    └── Ai2AiUpdateDTO.java           # 🆕 AI2AI 更新 DTO
```

---

## 三、分阶段实施计划

### 阶段一：SDAC 基础模块（预计 2-3 天）

#### 目标
- 创建 SDAC 智能体包装服务
- 实现 Evidence 生成器
- 实现 AI2AI 报告生成器

#### 任务清单

```
Phase 1: SDAC 基础模块
│
├── 1.1 SDAC 上下文与 DTO
│   ├── 创建 SdacAgentContext.java
│   ├── 创建 AgentEvidenceDTO.java
│   ├── 创建 GateResultDTO.java
│   └── 创建 Ai2AiUpdateDTO.java
│
├── 1.2 Evidence 生成器
│   ├── 创建 EvidenceGenerator.java
│   ├── 实现 input hash 计算
│   ├── 实现 verification 记录
│   └── 实现 manifest 文件输出
│
├── 1.3 AI2AI 报告生成器
│   ├── 创建 Ai2AiReporter.java
│   ├── 集成 SdacResourceService.renderAi2AiStateUpdate()
│   └── 实现 MD 文件输出
│
└── 1.4 SDAC 智能体包装服务
    ├── 创建 SdacAgentService.java
    ├── 实现 wrap() 方法包装 Agent 执行
    ├── 实现 validateAndRecord() 方法
    └── 编写单元测试
```

#### 核心代码模板

##### SdacAgentContext.java

```java
@Data
@Builder
public class SdacAgentContext {
    /**
     * 执行 ID（用于 Evidence 文件命名）
     */
    private String executionId;
    
    /**
     * 对话 ID
     */
    private Long conversationId;
    
    /**
     * 输入内容（用于 hash）
     */
    private String inputContent;
    
    /**
     * 输出目录
     */
    private Path outputDir;
    
    /**
     * 开始时间
     */
    private Instant startedAt;
    
    /**
     * 智能体名称
     */
    private String agentName;
    
    /**
     * 门禁类型
     */
    private String gateType;
}
```

##### EvidenceGenerator.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EvidenceGenerator {
    
    private final SdacResourceService sdacResourceService;
    private final ObjectMapper objectMapper;
    
    @Value("${aigen.sdac.evidence-dir:./evidence}")
    private String evidenceBaseDir;
    
    /**
     * 为 Agent 执行生成 Evidence Manifest
     */
    public Path generateEvidence(SdacAgentContext context, 
                                  Map<String, Object> inputs,
                                  List<VerificationRecord> verifications,
                                  String result,
                                  List<String> artifacts) {
        
        Map<String, Object> manifest = new LinkedHashMap<>();
        
        // 1. Inputs（带 hash）
        Map<String, Object> inputsSection = new LinkedHashMap<>();
        inputsSection.put("me2aiContractHash", hashInput(inputs.get("me2aiContract")));
        inputsSection.put("uiSpecHash", hashInput(inputs.get("uiSpec")));
        inputsSection.put("planHash", hashInput(inputs.get("plan")));
        manifest.put("inputs", inputsSection);
        
        // 2. Verifications
        manifest.put("verifications", verifications);
        
        // 3. Result
        manifest.put("result", result);
        
        // 4. Artifacts
        manifest.put("artifacts", artifacts);
        
        // 5. Timestamps
        Map<String, String> timestamps = new LinkedHashMap<>();
        timestamps.put("startedAt", context.getStartedAt().toString());
        timestamps.put("finishedAt", Instant.now().toString());
        manifest.put("timestamps", timestamps);
        
        // 6. 验证 manifest 格式
        sdacResourceService.validateEvidenceManifest(manifest);
        
        // 7. 写入文件
        Path evidencePath = getEvidencePath(context);
        writeJson(evidencePath, manifest);
        
        log.info("Evidence manifest generated: {}", evidencePath);
        return evidencePath;
    }
    
    private String hashInput(Object input) {
        if (input == null) return "";
        try {
            String json = objectMapper.writeValueAsString(input);
            return DigestUtils.md5DigestAsHex(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "";
        }
    }
    
    private Path getEvidencePath(SdacAgentContext context) {
        Path dir = Paths.get(evidenceBaseDir, "conversation-" + context.getConversationId(), "evidence");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create evidence directory", e);
        }
        String filename = "manifest-" + System.currentTimeMillis() + ".json";
        return dir.resolve(filename);
    }
    
    private void writeJson(Path path, Map<String, Object> data) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write evidence file", e);
        }
    }
    
    /**
     * 验证记录
     */
    @Data
    @AllArgsConstructor
    public static class VerificationRecord {
        private String cmd;
        private String status;
        private String summary;
        private String logsRef;
    }
}
```

##### Ai2AiReporter.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class Ai2AiReporter {
    
    private final SdacResourceService sdacResourceService;
    
    @Value("${aigen.sdac.evidence-dir:./evidence}")
    private String evidenceBaseDir;
    
    /**
     * 生成 AI2AI 状态更新报告
     */
    public Path generateReport(SdacAgentContext context,
                                String changeSummary,
                                String impact,
                                List<String> commands,
                                String result,
                                List<String> artifacts,
                                List<String> knownLimits) {
        
        // 使用 SdacResourceService 渲染模板
        String content = sdacResourceService.renderAi2AiStateUpdate(
            changeSummary, impact, commands, result, artifacts, knownLimits
        );
        
        // 写入文件
        Path reportPath = getReportPath(context);
        try {
            Files.writeString(reportPath, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write AI2AI report", e);
        }
        
        log.info("AI2AI report generated: {}", reportPath);
        return reportPath;
    }
    
    private Path getReportPath(SdacAgentContext context) {
        Path dir = Paths.get(evidenceBaseDir, "conversation-" + context.getConversationId(), "evidence");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create evidence directory", e);
        }
        String filename = "ai2ai-state-update-" + System.currentTimeMillis() + ".md";
        return dir.resolve(filename);
    }
}
```

##### SdacAgentService.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SdacAgentService {
    
    private final EvidenceGenerator evidenceGenerator;
    private final Ai2AiReporter ai2AiReporter;
    private final SdacResourceService sdacResourceService;
    
    /**
     * 包装 Agent 执行，自动添加 SDAC 能力
     */
    public AgentState wrapAndExecute(SdacAgentContext context, 
                                      Supplier<AgentState> agentExecution,
                                      Consumer<Map<String, Object>> outputValidator) {
        
        Instant start = Instant.now();
        context.setStartedAt(start);
        
        List<EvidenceGenerator.VerificationRecord> verifications = new ArrayList<>();
        List<String> artifacts = new ArrayList<>();
        String result = "FAIL";
        AgentState finalState = null;
        
        try {
            // 1. 执行 Agent
            finalState = agentExecution.get();
            
            // 2. 验证产出
            Map<String, Object> outputs = extractOutputs(finalState);
            if (outputValidator != null) {
                try {
                    outputValidator.accept(outputs);
                    verifications.add(new EvidenceGenerator.VerificationRecord(
                        "schema-validation", "PASS", "Output schema validation passed", null
                    ));
                } catch (Exception e) {
                    verifications.add(new EvidenceGenerator.VerificationRecord(
                        "schema-validation", "FAIL", e.getMessage(), null
                    ));
                    throw e;
                }
            }
            
            result = "PASS";
            
        } catch (Exception e) {
            log.error("Agent execution failed: {}", context.getAgentName(), e);
            verifications.add(new EvidenceGenerator.VerificationRecord(
                "execution", "FAIL", e.getMessage(), null
            ));
            throw new RuntimeException("Agent execution failed", e);
        } finally {
            // 3. 生成 Evidence
            Map<String, Object> inputs = Map.of(
                "me2aiContract", context.getInputContent(),
                "uiSpec", finalState != null ? finalState.getUiDesign() : null,
                "plan", finalState != null ? finalState.getGeneratedCode() : null
            );
            
            Path evidencePath = evidenceGenerator.generateEvidence(
                context, inputs, verifications, result, artifacts
            );
            artifacts.add(evidencePath.toString());
            
            // 4. 生成 AI2AI 报告
            ai2AiReporter.generateReport(
                context,
                context.getAgentName() + " 执行完成",
                "影响对话 " + context.getConversationId(),
                List.of("agent-execute", "schema-validate"),
                result,
                artifacts,
                List.of()
            );
        }
        
        return finalState;
    }
    
    private Map<String, Object> extractOutputs(AgentState state) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        if (state.getAnalysisResult() != null) {
            outputs.put("analysisResult", state.getAnalysisResult());
        }
        if (state.getUiDesign() != null) {
            outputs.put("uiDesign", state.getUiDesign());
        }
        if (state.getGeneratedCode() != null) {
            outputs.put("generatedCode", state.getGeneratedCode());
        }
        return outputs;
    }
}
```

#### 验收标准

- [ ] `SdacAgentContext` 可正确初始化
- [ ] `EvidenceGenerator` 可生成符合 schema 的 JSON 文件
- [ ] `Ai2AiReporter` 可生成 MD 格式的 AI2AI 报告
- [ ] `SdacAgentService.wrapAndExecute()` 可包装 Agent 执行
- [ ] 单元测试覆盖核心场景

---

### 阶段二：Agent SDAC 集成（预计 2-3 天）

#### 目标
- 改造现有 Agent 集成 SDAC
- 为不同 Agent 配置不同的验证策略
- 实现门禁检查

#### 任务清单

```
Phase 2: Agent SDAC 集成
│
├── 2.1 AgentState 扩展
│   ├── 添加 sdacContext 字段
│   ├── 添加 evidencePath 字段
│   └── 添加 ai2aiReportPath 字段
│
├── 2.2 DesignerAgent 改造
│   ├── 集成 SdacAgentService
│   ├── 输出 UI Spec 格式
│   └── 调用 validateUiSpec()
│
├── 2.3 DeveloperAgent 改造
│   ├── 集成 SdacAgentService
│   ├── 输出 Implementation Plan 格式
│   └── 调用 validateImplementationPlan()
│
├── 2.4 AgentOrchestrator 改造
│   ├── 注入 SdacAgentService
│   ├── 为每个 Agent 执行添加 SDAC 包装
│   └── 汇总所有 Evidence
│
└── 2.5 门禁评估器
    ├── 创建 GateEvaluator.java
    ├── 实现门禁状态计算
    └── 集成到 MultiAgentService
```

#### 核心代码模板

##### AgentState 扩展

```java
@Data
public class AgentState {
    // ... 现有字段 ...
    
    /**
     * SDAC 上下文
     */
    private SdacAgentContext sdacContext;
    
    /**
     * Evidence 文件路径列表
     */
    private List<String> evidencePaths = new ArrayList<>();
    
    /**
     * AI2AI 报告路径列表
     */
    private List<String> ai2aiReportPaths = new ArrayList<>();
    
    /**
     * 门禁结果
     */
    private Map<String, GateResultDTO> gateResults = new LinkedHashMap<>();
    
    // 添加便捷方法
    public void addEvidence(String path) {
        this.evidencePaths.add(path);
    }
    
    public void addAi2AiReport(String path) {
        this.ai2aiReportPaths.add(path);
    }
}
```

##### DesignerAgent 改造

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class DesignerAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final SdacAgentService sdacAgentService;
    private final SdacResourceService sdacResourceService;

    public AgentState execute(AgentState state) {
        SdacAgentContext context = SdacAgentContext.builder()
            .executionId(UUID.randomUUID().toString())
            .conversationId(extractConversationId(state))
            .inputContent(state.getUserInput())
            .startedAt(Instant.now())
            .agentName("designer")
            .gateType("UI")
            .build();
        
        AgentState result = sdacAgentService.wrapAndExecute(
            context,
            () -> executeInternal(state),
            outputs -> {
                // 验证 UI Spec 格式
                if (outputs.containsKey("uiDesign")) {
                    Map<String, Object> uiSpec = parseUiSpec(outputs.get("uiDesign"));
                    sdacResourceService.validateUiSpec(uiSpec);
                }
            }
        );
        
        // 复制 SDAC 信息到 state
        state.setEvidencePaths(result.getEvidencePaths());
        state.setAi2AiReportPaths(result.getAi2AiReportPaths());
        
        return result;
    }
    
    private AgentState executeInternal(AgentState state) {
        // ... 原有逻辑 ...
    }
}
```

##### GateEvaluator.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class GateEvaluator {
    
    private final SdacResourceService sdacResourceService;
    
    /**
     * 评估所有门禁状态
     */
    public Map<String, GateResultDTO> evaluateGates(AgentState state) {
        Map<String, String> defaults = sdacResourceService.loadDefaultGateStatuses();
        Map<String, GateResultDTO> results = new LinkedHashMap<>();
        
        // REQ 门禁
        results.put("REQ", evaluateReqGate(state, defaults));
        
        // UI 门禁
        results.put("UI", evaluateUiGate(state, defaults));
        
        // IMP 门禁
        results.put("IMP", evaluateImpGate(state, defaults));
        
        // PREVIEW 门禁
        results.put("PREVIEW", evaluatePreviewGate(state, defaults));
        
        return results;
    }
    
    private GateResultDTO evaluateReqGate(AgentState state, Map<String, String> defaults) {
        boolean hasAnalysis = state.getAnalysisResult() != null && 
                              !state.getAnalysisResult().isEmpty();
        
        return GateResultDTO.builder()
            .gate("REQ")
            .status(hasAnalysis ? "PASS" : defaults.get("REQ"))
            .message(hasAnalysis ? "需求分析完成" : "待分析")
            .build();
    }
    
    private GateResultDTO evaluateUiGate(AgentState state, Map<String, String> defaults) {
        boolean hasUiDesign = state.getUiDesign() != null && 
                              !state.getUiDesign().isBlank();
        
        return GateResultDTO.builder()
            .gate("UI")
            .status(hasUiDesign ? "PASS" : defaults.get("UI"))
            .message(hasUiDesign ? "UI 设计完成" : "待设计")
            .build();
    }
    
    // ... 其他门禁评估方法 ...
}
```

#### 验收标准

- [ ] `DesignerAgent` 执行后生成 Evidence 和 AI2AI 报告
- [ ] `DeveloperAgent` 执行后生成 Evidence 和 AI2AI 报告
- [ ] `AgentOrchestrator` 汇总所有 Evidence
- [ ] `GateEvaluator` 正确评估门禁状态
- [ ] 集成测试通过

---

### 阶段三：API 与前端集成（预计 1-2 天）

#### 目标
- 暴露 SDAC 相关 API
- 前端展示 Evidence 和门禁状态

#### 任务清单

```
Phase 3: API 与前端集成
│
├── 3.1 后端 API
│   ├── 创建 SdacController.java
│   ├── GET /api/sdac/evidence/{conversationId}
│   ├── GET /api/sdac/gates/{conversationId}
│   └── GET /api/sdac/ai2ai/{conversationId}
│
├── 3.2 MultiAgentService 改造
│   ├── 返回 SDAC 信息
│   └── 集成 GateEvaluator
│
└── 3.3 前端展示（可选）
    ├── Evidence 列表组件
    └── 门禁状态指示器
```

#### 验收标准

- [ ] API 可查询 Evidence 列表
- [ ] API 可查询门禁状态
- [ ] API 可查询 AI2AI 报告

---

## 四、完整改造示例

### DesignerAgent 完整改造示例

```java
package com.aigen.studio.agent;

import com.aigen.studio.sdac.SdacAgentContext;
import com.aigen.studio.sdac.SdacAgentService;
import com.aigen.studio.service.SdacResourceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DesignerAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final SdacAgentService sdacAgentService;
    private final SdacResourceService sdacResourceService;
    private final ObjectMapper objectMapper;

    public AgentState execute(AgentState state) {
        // 1. 构建 SDAC 上下文
        SdacAgentContext context = SdacAgentContext.builder()
            .executionId(UUID.randomUUID().toString())
            .conversationId(state.getConversationId())
            .inputContent(state.getUserInput())
            .startedAt(Instant.now())
            .agentName("designer")
            .gateType("UI")
            .build();
        
        // 2. 使用 SDAC 包装执行
        AgentState result = sdacAgentService.wrapAndExecute(
            context,
            () -> executeInternal(state),
            outputs -> validateUiDesign(outputs)
        );
        
        // 3. 更新 state 的 SDAC 信息
        result.setSd acContext(context);
        state.setEvidencePaths(result.getEvidencePaths());
        state.setAi2AiReportPaths(result.getAi2AiReportPaths());
        
        return result;
    }

    private AgentState executeInternal(AgentState state) {
        String analysisSummary = state.getAnalysisResult() == null
                ? ""
                : String.valueOf(state.getAnalysisResult().getOrDefault("summary", ""));
        
        // 生成结构化的 UI Spec
        String prompt = """
                你是 UI 设计智能体。请基于需求分析给出 UI 规格说明。
                
                输出格式要求（JSON）：
                {
                  "pages": [
                    {
                      "name": "页面名称",
                      "route": "/路径",
                      "components": ["组件1", "组件2"],
                      "interactions": ["交互1", "交互2"]
                    }
                  ],
                  "styles": {
                    "theme": "light/dark",
                    "primaryColor": "#色值",
                    "layout": "布局类型"
                  },
                  "summary": "设计概述"
                }
                
                用户需求：
                %s
                
                需求分析摘要：
                %s
                """.formatted(state.getUserInput(), analysisSummary);

        String uiDesignJson = chatLanguageModel.generate(prompt);
        
        state.setCurrentTask("ui-design-complete");
        state.setUiDesign(uiDesignJson);
        state.setNextAgent("supervisor");
        state.addMessage("Designer 已输出 UI 设计方案。");
        state.addEvent("designer", "supervisor", "设计完成，回传主管进行下一步分派");
        
        log.info("Designer completed UI design for current workflow");
        return state;
    }
    
    private void validateUiDesign(Map<String, Object> outputs) {
        String uiDesign = (String) outputs.get("uiDesign");
        if (uiDesign == null || uiDesign.isBlank()) {
            throw new IllegalStateException("UI design output is empty");
        }
        
        try {
            Map<String, Object> uiSpec = objectMapper.readValue(
                uiDesign, 
                new TypeReference<Map<String, Object>>() {}
            );
            sdacResourceService.validateUiSpec(uiSpec);
        } catch (Exception e) {
            throw new IllegalStateException("UI design validation failed: " + e.getMessage(), e);
        }
    }
}
```

---

## 五、验收清单

### 5.1 功能验收

| 功能 | 验收标准 | 状态 |
|------|----------|------|
| Evidence 生成 | 每个 Agent 执行后生成 JSON 格式 Evidence | [ ] |
| Schema 验证 | Designer 产出经过 UI Spec 验证 | [ ] |
| Schema 验证 | Developer 产出经过 Impl Plan 验证 | [ ] |
| AI2AI 报告 | 每次执行生成 MD 格式状态更新 | [ ] |
| 门禁评估 | 正确计算 REQ/UI/IMP/PREVIEW 门禁状态 | [ ] |

### 5.2 集成验收

| 集成点 | 验收标准 | 状态 |
|--------|----------|------|
| AgentOrchestrator | 正确调用 SDAC 包装 | [ ] |
| MultiAgentService | 返回完整 SDAC 信息 | [ ] |
| API | 可查询 Evidence 和门禁状态 | [ ] |

### 5.3 测试验收

| 测试类型 | 验收标准 | 状态 |
|----------|----------|------|
| 单元测试 | 覆盖率 > 80% | [ ] |
| 集成测试 | 端到端流程通过 | [ ] |
| 回归测试 | 现有功能不受影响 | [ ] |

---

## 六、风险控制

### 6.1 风险矩阵

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| Schema 验证失败 | 中 | 中 | 提供降级逻辑，记录警告 |
| Evidence 文件写入失败 | 低 | 中 | 添加重试机制 |
| 性能影响 | 中 | 低 | 异步写入 Evidence |

### 6.2 降级策略

```java
@Service
public class ResilientSdacAgentService {
    
    public AgentState wrapAndExecute(SdacAgentContext context, 
                                      Supplier<AgentState> agentExecution,
                                      Consumer<Map<String, Object>> outputValidator) {
        try {
            // 正常 SDAC 流程
            return sdacAgentService.wrapAndExecute(context, agentExecution, outputValidator);
        } catch (Exception e) {
            log.warn("SDAC wrapping failed, executing without SDAC", e);
            // 降级：直接执行，不生成 Evidence
            return agentExecution.get();
        }
    }
}
```

---

## 七、参考资料

- SDAC Schema 文件：`backend/src/main/resources/sdac/schemas/`
- SDAC 模板文件：`backend/src/main/resources/sdac/templates/`
- 现有 SdacResourceService：`backend/src/main/java/com/aigen/studio/service/SdacResourceService.java`
- Agent 能力增强计划：`spec/AI2AI/Agent_Capability_Enhancement_Plan.md`

---

## 八、进度追踪

```
Phase 1: [ ] 未开始 [ ] 进行中 [ ] 已完成
Phase 2: [ ] 未开始 [ ] 进行中 [ ] 已完成
Phase 3: [ ] 未开始 [ ] 进行中 [ ] 已完成
```

### 本次会话记录

**日期**: 2026-02-26

**状态**: 文档创建完成，待开始执行

---

*文档版本: 1.0*
*创建时间: 2026-02-26*
*最后更新: 2026-02-26*
