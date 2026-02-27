# AIGen Studio Agent 能力增强改造计划

> 本文档为渐进式改造的详细执行计划，用于指导后续开发会话

## 一、项目背景

### 1.1 当前状态

| 维度 | 现状 | 问题 |
|------|------|------|
| **Agent 框架** | 仅 iFlow SDK | 缺乏主流框架（LangChain/LangGraph） |
| **RAG 能力** | 无 | 无法利用知识库增强生成 |
| **多智能体** | 单 Agent | 无协作能力 |
| **记忆机制** | SQL 存储 | 无语义检索 |
| **岗位匹配** | ⭐⭐⭐ | 需提升至 ⭐⭐⭐⭐⭐ |

### 1.2 改造目标

1. **引入 LangChain4j**：Java 生态标准 Agent 框架
2. **实现 RAG 能力**：Qdrant 向量库 + 阿里云 Embedding
3. **多智能体协作**：Supervisor + Workers 架构
4. **统一技术栈**：Chat + Embedding 统一使用阿里云 DashScope

### 1.3 改造原则

- **渐进式**：分阶段实施，每阶段可独立验证
- **模块隔离**：新增模块独立，不影响现有功能
- **能力开关**：通过配置切换新旧能力
- **降级兼容**：新功能不可用时降级到现有 iFlow 实现

### 1.4 技术栈选型

| 类别 | 技术选型 | 说明 |
|------|----------|------|
| **Agent 框架** | LangChain4j 0.36.0 | Java 生态标准，OpenAI 兼容 |
| **Chat Model** | 阿里云 qwen-plus | 128K 上下文，支持 Function Calling |
| **Embedding Model** | 阿里云 text-embedding-v2 | 1536 维度，中文能力强 |
| **向量数据库** | Qdrant | 高性能、易部署、开源免费 |
| **API Provider** | 阿里云 DashScope | OpenAI 兼容模式，国内稳定 |

**成本优势**：
- ✅ Chat 模型免费额度：100万 tokens
- ✅ Embedding 模型免费额度：50万 tokens
- ✅ 统一 API Key，无需多账号管理
- ✅ 国内访问稳定，无需代理

---

## 二、总体架构

### 2.1 目标架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    AIGen Studio 增强架构                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   ConversationService                     │   │
│  │                         │                                 │   │
│  │            ┌────────────┴────────────┐                   │   │
│  │            ▼                         ▼                   │   │
│  │   ┌────────────────┐       ┌────────────────┐            │   │
│  │   │   iFlow SDK    │       │  LangChain4j   │            │   │
│  │   │   (复杂代码)    │       │   (新增能力)    │            │   │
│  │   │                │       │                │            │   │
│  │   │ - 代码生成     │       │ - RAG 检索     │            │   │
│  │   │ - UI 原型      │       │ - 多智能体     │            │   │
│  │   │ - 长时任务     │       │ - 记忆管理     │            │   │
│  │   └────────────────┘       └────────────────┘            │   │
│  │            │                         │                   │   │
│  │            └────────────┬────────────┘                   │   │
│  │                         ▼                                 │   │
│  │               ┌────────────────┐                         │   │
│  │               │  阿里云 API    │                         │   │
│  │               │  DashScope     │                         │   │
│  │               └────────────────┘                         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                     新增模块                              │   │
│  │                                                          │   │
│  │   ┌──────────┐   ┌──────────┐   ┌──────────┐            │   │
│  │   │   rag/   │   │  agent/  │   │ memory/  │            │   │
│  │   │          │   │          │   │          │            │   │
│  │   │ - RAG    │   │ - Multi  │   │ - Vector │            │   │
│  │   │ - Embed  │   │ - Super  │   │ - Conv   │            │   │
│  │   │ - Doc    │   │ - Worker │   │ - Long   │            │   │
│  │   └──────────┘   └──────────┘   └──────────┘            │   │
│  │                                                          │   │
│  │   ┌──────────────────────────────────────────────┐       │   │
│  │   │                 Qdrant 向量库                  │       │   │
│  │   └──────────────────────────────────────────────┘       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 API 调用架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    阿里云 DashScope API                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Base URL: https://dashscope.aliyuncs.com/compatible-mode/v1   │
│                                                                 │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │                    OpenAI 兼容模式                        │  │
│   │                                                          │  │
│   │   Chat API:        /chat/completions                     │  │
│   │   Embedding API:   /embeddings                           │  │
│   │                                                          │  │
│   │   支持模型：                                              │  │
│   │   - qwen-plus (Chat)                                     │  │
│   │   - qwen-turbo (Chat, 更快)                              │  │
│   │   - qwen-max (Chat, 更强)                                │  │
│   │   - text-embedding-v2 (Embedding, 1536维)                │  │
│   │   - text-embedding-v3 (Embedding, 可调维度)              │  │
│   └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│   环境变量配置：                                                 │
│   DASHSCOPE_API_KEY=sk-xxxxxxxx                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 文件结构规划

```
backend/src/main/java/com/aigen/studio/
├── config/
│   ├── AgentProperties.java          # 🆕 Agent 能力配置
│   └── LangChainConfig.java          # 🆕 LangChain4j 配置
│
├── rag/                               # 🆕 RAG 模块
│   ├── RAGService.java               # RAG 核心服务
│   ├── DocumentIngestionService.java # 文档摄入服务
│   ├── EmbeddingService.java         # 向量化服务
│   └── VectorStoreService.java       # 向量存储服务
│
├── agent/                             # 🆕 多智能体模块
│   ├── AgentOrchestrator.java        # 智能体编排器
│   ├── SupervisorAgent.java          # 主管智能体
│   ├── AnalystAgent.java             # 分析智能体
│   ├── DesignerAgent.java            # 设计智能体
│   └── DeveloperAgent.java           # 开发智能体
│
├── memory/                            # 🆕 记忆模块
│   ├── VectorMemoryService.java      # 向量记忆服务
│   ├── ConversationMemoryService.java# 对话记忆服务
│   └── MemoryManager.java            # 记忆管理器
│
├── langchain/                         # 🆕 LangChain 集成
│   ├── AIServiceRegistry.java        # AI 服务注册
│   └── tools/                        # 自定义工具
│       ├── CodeSearchTool.java       # 代码搜索工具
│       ├── DocumentQueryTool.java    # 文档查询工具
│       └── HistoryRecallTool.java    # 历史召回工具
│
└── service/                           # 现有服务（小改）
    ├── ConversationService.java      # 扩展状态机
    └── UnderstandingService.java     # 🆕 理解服务（路由）
```

---

## 三、分阶段实施计划

### 阶段一：基础设施搭建（Week 1-2）

#### 目标
- 引入 LangChain4j 依赖
- 部署 Qdrant 向量库
- 配置阿里云 DashScope API
- 验证 Chat 和 Embedding 模型可用

#### 任务清单

```
Phase 1: 基础设施搭建
│
├── 1.1 依赖配置
│   ├── 修改 pom.xml，添加 LangChain4j 依赖
│   ├── 添加 Qdrant Java SDK
│   └── 配置 application.yml（阿里云 DashScope）
│
├── 1.2 Qdrant 部署
│   ├── 创建 docker-compose.yml
│   ├── 启动 Qdrant 容器
│   └── 验证连接
│
├── 1.3 核心配置类
│   ├── 创建 AgentProperties.java
│   ├── 创建 LangChainConfig.java
│   └── 创建 QdrantConfig.java
│
├── 1.4 API 连接测试
│   ├── 测试 qwen-plus Chat 模型
│   ├── 测试 text-embedding-v2 Embedding 模型
│   └── 编写连接测试用例
│
└── 1.5 基础服务
    ├── 创建 EmbeddingService.java
    ├── 创建 VectorStoreService.java
    └── 编写单元测试
```

#### 详细任务

##### 1.1 pom.xml 依赖

```xml
<!-- LangChain4j 核心 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.36.0</version>
</dependency>

<!-- Spring Boot 集成 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>0.36.0</version>
</dependency>

<!-- OpenAI 兼容（用于连接阿里云 DashScope） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    <version>0.36.0</version>
</dependency>

<!-- Qdrant 向量库 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-qdrant</artifactId>
    <version>0.36.0</version>
</dependency>

<!-- Qdrant Java SDK -->
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>client</artifactId>
    <version>1.7.0</version>
</dependency>
```

##### 1.2 docker-compose.yml

```yaml
version: '3.8'
services:
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - ./data/qdrant:/qdrant/storage
    environment:
      - QDRANT__SERVICE__GRPC_PORT=6334
```

##### 1.3 application.yml 配置

```yaml
# 阿里云 DashScope API 配置
# 环境变量方式（推荐）:
# export DASHSCOPE_API_KEY="sk-xxxxxxxx"

# Agent 能力开关
aigen:
  agent:
    enable-rag: true
    enable-multi-agent: false
    use-langchain: true

# LangChain4j 配置（OpenAI 兼容模式连接阿里云）
langchain4j:
  open-ai:
    # Chat Model - qwen-plus
    chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}
      model-name: qwen-plus
      temperature: 0.7
      max-tokens: 4096
    
    # Embedding Model - text-embedding-v2
    embedding-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}
      model-name: text-embedding-v2

# Qdrant 向量库配置
qdrant:
  host: localhost
  port: 6334
  collection-name: aigen_knowledge
```

##### 1.4 环境变量配置

```bash
# 阿里云 DashScope API Key
export DASHSCOPE_API_KEY="sk-xxxxxxxx"

# 或者在 IDEA 中配置环境变量
# Run/Debug Configurations -> Environment variables
```

##### 1.5 AgentProperties.java

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "aigen.agent")
public class AgentProperties {
    /**
     * 是否启用 RAG 能力
     */
    private boolean enableRag = false;
    
    /**
     * 是否启用多智能体
     */
    private boolean enableMultiAgent = false;
    
    /**
     * 是否使用 LangChain（否则降级到 iFlow）
     */
    private boolean useLangChain = false;
}
```

##### 1.6 API 连接测试

```java
@SpringBootTest
class DashScopeConnectionTest {
    
    @Autowired
    private ChatLanguageModel chatModel;
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Test
    void testChatModel() {
        String response = chatModel.generate("你好，请介绍一下自己");
        assertNotNull(response);
        System.out.println("Chat Response: " + response);
    }
    
    @Test
    void testEmbeddingModel() {
        Embedding embedding = embeddingModel.embed("测试文本向量化");
        assertNotNull(embedding);
        assertEquals(1536, embedding.vector().length);  // text-embedding-v2 维度
        System.out.println("Embedding dimension: " + embedding.vector().length);
    }
}
```

#### 验收标准

- [ ] LangChain4j 依赖成功引入，无冲突
- [ ] Qdrant 容器成功启动，可访问 http://localhost:6333/dashboard
- [ ] qwen-plus Chat 模型连接测试通过
- [ ] text-embedding-v2 Embedding 模型连接测试通过
- [ ] EmbeddingService 单元测试通过
- [ ] VectorStoreService 可成功写入和查询向量

---

### 阶段二：RAG 能力实现（Week 3-4）

#### 目标
- 实现文档摄入 Pipeline
- 实现历史对话向量化
- 实现 RAG 增强的需求理解

#### 任务清单

```
Phase 2: RAG 能力实现
│
├── 2.1 文档摄入服务
│   ├── 创建 DocumentIngestionService.java
│   ├── 实现 PDF/Markdown 解析
│   ├── 实现智能分块策略
│   └── 实现元数据提取
│
├── 2.2 RAG 核心服务
│   ├── 创建 RAGService.java
│   ├── 实现相似度检索
│   ├── 实现上下文构建
│   └── 实现 RAG 增强生成
│
├── 2.3 历史对话向量化
│   ├── 创建 ConversationVectorService.java
│   ├── 实现对话分块
│   ├── 实现向量化存储
│   └── 实现语义检索
│
├── 2.4 理解服务改造
│   ├── 创建 UnderstandingService.java（路由层）
│   ├── 实现 iFlow/LangChain 切换
│   └── 集成 RAG 增强
│
└── 2.5 API 接口
    ├── 创建 KnowledgeController.java
    ├── 实现知识上传 API
    └── 实现知识检索 API
```

#### 核心代码模板

##### RAGService.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {
    
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel chatModel;
    private final AgentProperties properties;
    
    /**
     * 文档摄入
     */
    public void ingestDocument(String content, Map<String, Object> metadata) {
        // 1. 智能分块
        DocumentSplitter splitter = new DocumentByParagraphSplitter(500, 100);
        List<TextSegment> segments = splitter.split(new Document(content));
        
        // 2. 添加元数据
        segments.forEach(seg -> seg.metadata().putAll(metadata));
        
        // 3. 向量化并存储
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        
        log.info("Ingested {} segments", segments.size());
    }
    
    /**
     * RAG 增强生成
     */
    public String generateWithRAG(String query, Long conversationId) {
        // 1. 检索相关文档
        List<EmbeddingMatch<TextSegment>> matches = search(query, 5);
        
        // 2. 构建增强提示词
        String context = buildContext(matches);
        String augmentedPrompt = """
            基于以下上下文回答问题：
            
            ## 上下文
            %s
            
            ## 问题
            %s
            
            请基于上下文提供准确、详细的回答。如果上下文中没有相关信息，请明确说明。
            """.formatted(context, query);
        
        // 3. 调用 LLM 生成
        return chatModel.generate(augmentedPrompt);
    }
    
    /**
     * 相似度检索
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return embeddingStore.findRelevant(queryEmbedding, maxResults);
    }
    
    private String buildContext(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
            .map(match -> match.embedded().text())
            .collect(Collectors.joining("\n\n---\n\n"));
    }
}
```

##### UnderstandingService.java（路由层）

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UnderstandingService {
    
    private final PromptTaskService promptTaskService;  // 现有 iFlow 实现
    private final RAGService ragService;                // 新增 RAG 实现
    private final ChatLanguageModel chatModel;          // LangChain Chat
    private final AgentProperties properties;
    
    /**
     * 需求理解（路由到不同实现）
     */
    public UnderstandingResult understand(String requirement, Long conversationId) {
        if (properties.isUseLangChain() && properties.isEnableRag()) {
            log.info("Using LangChain + RAG for understanding");
            return understandWithRAG(requirement, conversationId);
        } else if (properties.isUseLangChain()) {
            log.info("Using LangChain for understanding");
            return understandWithLangChain(requirement);
        } else {
            log.info("Using iFlow for understanding");
            return understandWithIFlow(requirement);
        }
    }
    
    private UnderstandingResult understandWithRAG(String requirement, Long conversationId) {
        // RAG 增强的理解
        String enhanced = ragService.generateWithRAG(requirement, conversationId);
        return UnderstandingResult.from(enhanced);
    }
    
    private UnderstandingResult understandWithLangChain(String requirement) {
        // 纯 LangChain 理解
        String prompt = """
            请分析以下需求，提取关键信息：
            
            %s
            
            请输出：
            1. 核心功能点
            2. 技术要求
            3. 澄清问题（如有）
            """.formatted(requirement);
        
        String response = chatModel.generate(prompt);
        return UnderstandingResult.from(response);
    }
    
    private UnderstandingResult understandWithIFlow(String requirement) {
        // 降级到现有实现
        return promptTaskService.understand(requirement);
    }
}
```

#### 验收标准

- [ ] 文档摄入 API 可成功上传并解析文档
- [ ] 向量检索返回相似度排序结果
- [ ] RAG 增强的理解结果比纯 iFlow 更准确
- [ ] 历史对话可被语义检索召回

---

### 阶段三：多智能体协作（Week 5-6）

#### 目标
- 实现 Supervisor + Workers 架构
- 实现 Agent 间通信
- 实现任务分解与编排

#### 任务清单

```
Phase 3: 多智能体协作
│
├── 3.1 智能体基础架构
│   ├── 创建 AgentOrchestrator.java
│   ├── 定义 AgentState 状态类
│   └── 实现工作流图构建
│
├── 3.2 智能体节点实现
│   ├── 创建 SupervisorAgent.java
│   ├── 创建 AnalystAgent.java
│   ├── 创建 DesignerAgent.java
│   └── 创建 DeveloperAgent.java
│
├── 3.3 通信机制
│   ├── 实现共享状态
│   ├── 实现消息传递
│   └── 实现任务交接
│
├── 3.4 编排服务
│   ├── 创建 MultiAgentService.java
│   ├── 集成到 ConversationService
│   └── 实现异步执行
│
└── 3.5 监控与调试
    ├── 添加执行日志
    ├── 实现状态追踪
    └── 可视化流程图
```

#### 核心代码模板

##### AgentOrchestrator.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentOrchestrator {
    
    private final SupervisorAgent supervisorAgent;
    private final AnalystAgent analystAgent;
    private final DesignerAgent designerAgent;
    private final DeveloperAgent developerAgent;
    
    /**
     * 智能体状态
     */
    public record AgentState(
        String userInput,
        String currentTask,
        Map<String, Object> analysisResult,
        String uiDesign,
        String generatedCode,
        List<String> messages,
        String nextAgent,
        int iterations
    ) {}
    
    /**
     * 执行多智能体工作流
     */
    public AgentState execute(String userInput) {
        AgentState state = new AgentState(
            userInput, null, 
            Map.of(), null, null,
            new ArrayList<>(), "supervisor", 0
        );
        
        int maxIterations = 10;
        while (!state.nextAgent().equals("end") && state.iterations() < maxIterations) {
            state = route(state);
            log.info("Agent iteration {}: nextAgent = {}", state.iterations(), state.nextAgent());
        }
        
        return state;
    }
    
    /**
     * 路由到不同智能体
     */
    private AgentState route(AgentState state) {
        return switch (state.nextAgent()) {
            case "supervisor" -> supervisorAgent.execute(state);
            case "analyst" -> analystAgent.execute(state);
            case "designer" -> designerAgent.execute(state);
            case "developer" -> developerAgent.execute(state);
            default -> state;
        };
    }
}
```

##### SupervisorAgent.java

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SupervisorAgent {
    
    private final ChatLanguageModel chatModel;
    
    public AgentOrchestrator.AgentState execute(AgentOrchestrator.AgentState state) {
        String prompt = """
            你是一个项目主管，负责协调以下智能体：
            - analyst: 需求分析智能体，负责分析用户需求
            - designer: UI 设计智能体，负责生成 UI 原型
            - developer: 代码开发智能体，负责生成代码
            
            当前状态：
            - 用户输入: %s
            - 分析结果: %s
            - UI 设计: %s
            - 代码: %s
            
            请决定下一步要执行的智能体名称，或返回 'end' 表示任务完成。
            只返回智能体名称（analyst/designer/developer/end），不要其他内容。
            """.formatted(
                state.userInput(),
                state.analysisResult().isEmpty() ? "无" : state.analysisResult().toString(),
                state.uiDesign() == null ? "无" : state.uiDesign(),
                state.generatedCode() == null ? "无" : state.generatedCode()
            );
        
        String decision = chatModel.generate(prompt).trim().toLowerCase();
        
        return new AgentOrchestrator.AgentState(
            state.userInput(), state.currentTask(),
            state.analysisResult(), state.uiDesign(),
            state.generatedCode(), state.messages(),
            decision, state.iterations() + 1
        );
    }
}
```

#### 验收标准

- [ ] Supervisor 可正确路由到对应 Worker
- [ ] Agent 间可通过共享状态传递信息
- [ ] 完整工作流可执行并返回结果
- [ ] 执行日志可追踪每个 Agent 的决策

---

### 阶段四：整合与优化（Week 7-8）

#### 目标
- 完善新旧能力切换机制
- 优化性能和资源使用
- 完善测试和文档

#### 任务清单

```
Phase 4: 整合与优化
│
├── 4.1 ConversationService 改造
│   ├── 扩展状态机支持新阶段
│   ├── 集成 UnderstandingService
│   └── 实现能力降级
│
├── 4.2 性能优化
│   ├── 向量检索缓存
│   ├── Embedding 批处理
│   └── 异步执行优化
│
├── 4.3 测试完善
│   ├── RAG 单元测试
│   ├── 多智能体集成测试
│   └── 端到端测试
│
├── 4.4 监控与告警
│   ├── 添加 Metrics
│   ├── 配置日志
│   └── 设置告警
│
└── 4.5 文档更新
    ├── 更新 README
    ├── 编写 API 文档
    └── 更新 AGENTS.md
```

#### 验收标准

- [ ] 所有单元测试通过
- [ ] 集成测试通过
- [ ] 性能指标达标（RAG 检索 < 100ms）
- [ ] 文档更新完整

---

## 四、技术选型汇总

| 类别 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| **Agent 框架** | LangChain4j | 0.36.0 | Java 生态标准 |
| **Chat Model** | 阿里云 qwen-plus | - | 128K 上下文，免费 100万 tokens |
| **Embedding Model** | 阿里云 text-embedding-v2 | - | 1536 维度，免费 50万 tokens |
| **向量数据库** | Qdrant | latest | 高性能、易部署 |
| **API Provider** | 阿里云 DashScope | - | OpenAI 兼容模式 |

### 阿里云 DashScope 模型详情

| 模型 | 类型 | 上下文/维度 | 价格 | 免费额度 |
|------|------|-------------|------|----------|
| **qwen-plus** | Chat | 128K | ¥0.0008/千tokens(输入) | 100万 tokens |
| qwen-turbo | Chat | 128K | ¥0.0003/千tokens | 100万 tokens |
| qwen-max | Chat | 32K | ¥0.02/千tokens | 100万 tokens |
| **text-embedding-v2** | Embedding | 1536 | ¥0.0007/千tokens | 50万 tokens |
| text-embedding-v3 | Embedding | 可调(64-1024) | ¥0.0005/千tokens | 100万 tokens |

---

## 五、风险控制

### 5.1 风险矩阵

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 依赖冲突 | 中 | 高 | 使用 dependencyManagement |
| Qdrant 不稳定 | 低 | 中 | 添加重试机制、降级到内存存储 |
| API 调用失败 | 中 | 高 | 添加降级逻辑、重试机制 |
| 性能下降 | 中 | 中 | 添加缓存、异步处理 |
| 阿里云 API 限流 | 低 | 中 | 添加限流控制、队列机制 |

### 5.2 降级策略

```java
@Service
public class ResilientUnderstandingService {
    
    public UnderstandingResult understand(String requirement) {
        // 优先使用 LangChain + RAG
        if (properties.isEnableRag()) {
            try {
                return ragService.understand(requirement);
            } catch (Exception e) {
                log.warn("RAG understanding failed, falling back", e);
            }
        }
        
        // 降级到纯 LangChain
        if (properties.isUseLangChain()) {
            try {
                return langChainService.understand(requirement);
            } catch (Exception e) {
                log.warn("LangChain understanding failed, falling back to iFlow", e);
            }
        }
        
        // 最终降级到 iFlow
        return iFlowService.understand(requirement);
    }
}
```

---

## 六、里程碑与交付物

| 阶段 | 时间 | 交付物 | 验收标准 |
|------|------|--------|----------|
| Phase 1 | Week 1-2 | 基础设施 | Qdrant 运行、阿里云 API 连通 |
| Phase 2 | Week 3-4 | RAG 能力 | 文档摄入、检索、增强生成 |
| Phase 3 | Week 5-6 | 多智能体 | Supervisor + Workers 可运行 |
| Phase 4 | Week 7-8 | 整合优化 | 全功能可用、测试通过 |

---

## 七、后续会话执行指南

### 7.1 环境准备

在开始执行前，请确保已配置以下环境变量：

```bash
# 阿里云 DashScope API Key
export DASHSCOPE_API_KEY="sk-xxxxxxxx"

# 启动 Qdrant
docker-compose up -d qdrant
```

### 7.2 启动新会话时

请使用以下 prompt 启动：

```
请按照 spec/AI2AI/Agent_Capability_Enhancement_Plan.md 执行改造计划。
当前阶段：Phase X（X 为 1-4）
请告诉我需要完成的具体任务。
```

### 7.3 每次会话结束前

1. 更新本文档的进度状态
2. 记录已完成和待完成的任务
3. 标注遇到的问题和解决方案

### 7.4 进度追踪

```
Phase 1: [ ] 未开始 [ ] 进行中 [x] 已完成
Phase 2: [ ] 未开始 [ ] 进行中 [x] 已完成
Phase 3: [ ] 未开始 [ ] 进行中 [x] 已完成
Phase 4: [ ] 未开始 [x] 进行中 [ ] 已完成
```

### 7.5 本次会话记录（2026-02-26）

**已完成**
- Phase 1.1：后端 `pom.xml` 已引入 LangChain4j / Qdrant 依赖；`application.yml` 已补齐 DashScope、Qdrant、Agent 开关配置。
- Phase 1.2（部分）：根目录已新增 `docker-compose.yml`（qdrant 服务定义完成）。
- Phase 1.3：已新增 `AgentProperties.java`、`LangChainConfig.java`、`QdrantConfig.java`。
- Phase 1.4：已新增 `DashScopeConnectionTest`，覆盖 Chat/Embedding Bean 可用性与可选的在线连通测试。
- Phase 1.5：已新增 `EmbeddingService.java`、`VectorStoreService.java`，并补充单元测试通过。

**待完成**
- 无（Phase 1 验收项已完成）。

**问题与处理**
- Docker daemon 曾未运行导致 Qdrant 容器无法启动；已在 Docker 可用后完成启动与连通验证（`healthz` 通过，dashboard HTTP 200）。

### 7.6 本次会话记录（2026-02-26，Phase 2）

**已完成**
- Phase 2.1：新增文档摄入服务 `DocumentIngestionService`，支持 Markdown/PDF 解析、分块、元数据写入向量库。
- Phase 2.2：新增 `RAGService`，实现相似度检索、上下文构建、RAG 增强生成（支持按 `conversationId` 过滤）。
- Phase 2.3：新增 `ConversationVectorService`，实现历史对话分块向量化和语义召回。
- Phase 2.4：新增 `UnderstandingService` 路由层（LangChain+RAG / LangChain / iFlow 降级）；`ConversationService` 理解入口完成接线替换。
- Phase 2.5：新增 `KnowledgeController` 与 `KnowledgeIngestRequest`，提供知识上传、文本摄入、知识检索 API。

**测试与验证**
- 新增测试通过：`DocumentIngestionServiceTest`、`RAGServiceTest`、`ConversationVectorServiceTest`、`UnderstandingServiceTest`、`KnowledgeControllerTest`。
- 回归测试通过：`ConversationSdacFlowControllerTest`、`ConversationReadyStageTest`、`ConversationServiceTitleTest`。
- 编译验证通过：`mvn -DskipTests compile`。

**待完成**
- 无（Phase 2 任务清单已完成）。

### 7.7 本次会话记录（2026-02-26，Phase 3）

**已完成**
- Phase 3.1：新增 `agent` 模块基础架构：`AgentState`、`AgentEvent`、`AgentOrchestrator`，支持 Supervisor + Workers 工作流执行与最大迭代保护。
- Phase 3.2：新增 4 个智能体节点：`SupervisorAgent`、`AnalystAgent`、`DesignerAgent`、`DeveloperAgent`，完成任务分派、需求分析、UI 设计、实现方案生成。
- Phase 3.3：实现共享状态与任务交接机制（`AgentState` 统一承载分析结果、UI 方案、实现方案、消息与事件轨迹）。
- Phase 3.4：新增 `MultiAgentService`，提供同步/异步编排执行能力，并接入 `UnderstandingService` 路由（`enableMultiAgent + useLangChain` 开启后优先走多智能体）。
- Phase 3.5：补齐执行日志、状态追踪与流程可视化（`AgentEvent` 轨迹 + Mermaid 流程图渲染能力）。

**测试与验证**
- 新增测试通过：`AgentOrchestratorTest`、`SupervisorAgentTest`、`WorkerAgentsTest`、`MultiAgentServiceTest`。
- 路由测试更新通过：`UnderstandingServiceTest`（新增多智能体路径测试）。
- 回归测试通过：`ConversationSdacFlowControllerTest`、`ConversationReadyStageTest`、`ConversationServiceTitleTest`、`KnowledgeControllerTest`。
- 编译验证通过：`mvn -DskipTests compile`。

**待完成**
- 无（Phase 3 任务清单已完成）。

### 7.8 本次会话记录（2026-02-26，Phase 4 Batch A）

**已完成**
- Phase 4.1（增强）：`UnderstandingService` 已补齐多智能体失败后的降级路径测试，确保 `MultiAgent -> RAG -> LangChain -> iFlow` 路由链可验证。
- Phase 4.2（性能）：`RAGService` 新增检索缓存（TTL、容量、命中/未命中/淘汰统计）与缓存失效能力；`EmbeddingService` 新增可配置分批向量化；`ConversationVectorService` 新增异步向量化与运行态去重能力。
- Phase 4.4（监控基础）：`KnowledgeController` 新增 `GET /knowledge/cache/stats`，可查询 RAG 缓存指标（hits/misses/evictions/size）。

**测试与验证**
- 新增/更新测试通过：`RAGServiceTest`、`EmbeddingServiceTest`、`ConversationVectorServiceTest`、`DocumentIngestionServiceTest`、`UnderstandingServiceTest`、`KnowledgeControllerTest`、`VectorStoreServiceTest`。
- 回归测试通过：`AgentOrchestratorTest`、`SupervisorAgentTest`、`WorkerAgentsTest`、`MultiAgentServiceTest`、`ConversationSdacFlowControllerTest`、`ConversationReadyStageTest`、`ConversationServiceTitleTest`。
- 编译验证通过：`mvn -DskipTests compile`。

**待完成**
- Phase 4.4：告警策略（阈值告警）尚未落地。
- Phase 4.5：README / API 文档 / AGENTS.md 的系统化更新尚未完成。

### 7.9 本次会话记录（2026-02-26，Phase 4 Batch B/C）

**已完成**
- Phase 4.1（ConversationService 接线增强）：在理解链路新增会话语义记忆刷新，`runSingleUnderstanding` / `reUnderstandRequirement` / `parseUnderstanding` 统一接入 `ConversationVectorService#indexConversationAsync`，并通过 `AgentProperties` 开关与超时控制实现可降级执行。
- Phase 4.4（监控与告警）：`RAGService` 新增缓存告警策略（最小样本量 + miss rate 阈值 + 告警日志冷却）；`KnowledgeController` 新增 `GET /knowledge/cache/alerts` 查询告警状态。
- Phase 4.5（文档更新）：已新增 `docs/API_KNOWLEDGE_RAG.md`，并更新 `README.md`、`AGENTS.md`，补齐 Knowledge/RAG API 与配置说明。

**测试与验证**
- TDD 红灯验证：`RAGServiceTest`、`KnowledgeControllerTest` 在新增告警测试后先编译失败（缺少 `CacheAlertStatus` 与 `getCacheAlertStatus`），符合预期。
- 绿灯回归通过：`RAGServiceTest`、`KnowledgeControllerTest`、`ConversationVectorServiceTest`、`DocumentIngestionServiceTest`、`EmbeddingServiceTest`、`VectorStoreServiceTest`、`UnderstandingServiceTest`、`ConversationServiceTitleTest`、`ConversationReadyStageTest`、`ConversationSdacFlowControllerTest`。
- 编译验证通过：`mvn -DskipTests compile`。

**待完成**
- Phase 4 验收中的性能量化指标（`RAG 检索 < 100ms`）尚未建立自动化基准与持续观测。

### 7.10 本次会话记录（2026-02-26，Phase 4 Batch D）

**已完成**
- Phase 4.4（Metrics 扩展）：`RAGService` 新增检索性能统计能力（`avg/p95/max`、样本窗口、阈值判定），并在每次检索自动采样。
- Phase 4.4（观测 API）：`KnowledgeController` 新增 `GET /knowledge/perf/stats`，可直接读取性能统计与阈值状态。
- Phase 4.5（文档补齐）：`docs/API_KNOWLEDGE_RAG.md`、`README.md`、`AGENTS.md` 已新增性能接口和 `aigen.rag.perf-*` 配置说明。

**测试与验证**
- TDD 红灯验证：新增 `RAGServiceTest.performanceStatsTracksLatencyAndThresholdBreach` 与 `KnowledgeControllerTest.performanceStatsEndpointReturnsLatencyMetrics` 后先失败（缺少 `PerformanceStats` / `getPerformanceStats`），符合预期。
- 绿灯验证通过：`RAGServiceTest`、`KnowledgeControllerTest`。
- 回归测试通过：`RAGServiceTest`、`KnowledgeControllerTest`、`ConversationVectorServiceTest`、`DocumentIngestionServiceTest`、`EmbeddingServiceTest`、`VectorStoreServiceTest`、`UnderstandingServiceTest`、`ConversationServiceTitleTest`、`ConversationReadyStageTest`、`ConversationSdacFlowControllerTest`。
- 编译验证通过：`mvn -DskipTests compile`。

**待完成**
- 无（Phase 4 当前计划项已完成，后续可按需要补压测脚本与长期监控面板）。

---

## 八、参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [Qdrant 官方文档](https://qdrant.tech/documentation/)
- [阿里云 DashScope API 文档](https://help.aliyun.com/zh/model-studio/)
- [阿里云 qwen-plus 模型文档](https://help.aliyun.com/zh/model-studio/qwen-api-via-dashscope)
- [阿里云 Embedding 模型文档](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api)

---

*文档版本: 2.0*
*创建时间: 2026-02-26*
*最后更新: 2026-02-26*
*主要变更: 统一使用阿里云 DashScope API（Chat + Embedding）*
