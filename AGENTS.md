# AIGen Studio - AI 代码生成平台

> 本文档为 iFlow CLI 代理提供项目上下文和开发指导

## 项目概述

AIGen Studio 是一个基于 Me2AI 规范实现的 AI 代码生成平台 PoC（概念验证），集成了 iFlow SDK 实现自动化代码生成。平台支持从需求管理到代码生成、产物交付的完整闭环。

**核心价值**：通过 IR（中间表示）规范定义项目结构，利用 iFlow AI 生成前端 Vue 3、后端 Spring Boot 工程及相关文档，并与 GitLab 深度集成实现代码提交和 CI/CD 触发。

**项目状态**：Phase 1-3 核心能力已完成并通过验证；当前处于 Phase 4（整合与优化）阶段，重点推进监控告警与文档收敛。

## 技术架构

### 后端技术栈
- **框架**：Spring Boot 3.2.0
- **数据持久化**：Spring Data JPA + H2 数据库（开发环境，文件存储：`data/aigendb.mv.db`）
- **安全**：Spring Security（开发模式开放）
- **API 文档**：OpenAPI 3.0 (Swagger UI: `/api/swagger-ui.html`)
- **AI 集成**：iFlow SDK 1.0.4-fix（cn.iflow:iflow-cli-sdk）
- **响应式编程**：Project Reactor（用于 iFlow 消息流处理）

### 前端技术栈
- **框架**：Vue 3.4.0 + TypeScript 5.3
- **构建工具**：Vite 5.0
- **UI 组件库**：Element Plus 2.4
- **状态管理**：Pinia 2.1
- **路由**：Vue Router 4.2
- **代码编辑器**：Monaco Editor 0.45（IR 编辑器）
- **HTTP 客户端**：Axios 1.6

### 项目结构

```
aigen_studio/
├── backend/                              # Spring Boot 后端
│   ├── src/main/java/com/aigen/studio/
│   │   ├── AigenStudioApplication.java   # 应用入口
│   │   ├── config/                       # 配置类
│   │   │   ├── AsyncConfig.java          # 异步任务配置
│   │   │   ├── GitLabProperties.java     # GitLab 配置属性
│   │   │   ├── OpenApiConfig.java        # Swagger API 文档配置
│   │   │   ├── SecurityConfig.java       # 安全配置
│   │   │   └── SSLConfig.java            # SSL 配置（处理自签名证书）
│   │   ├── controller/                   # REST API 控制器
│   │   │   ├── ArtifactController.java   # 产出物管理 API
│   │   │   ├── GenerationJobController.java  # 作业管理 API
│   │   │   ├── IRDocumentController.java     # IR 文档管理 API
│   │   │   ├── KnowledgeController.java      # 知识库/RAG API
│   │   │   └── RequirementController.java    # 需求管理 API
│   │   ├── dto/                          # 数据传输对象
│   │   │   ├── ArtifactDTO.java
│   │   │   ├── GenerationJobDTO.java
│   │   │   ├── IRDocumentDTO.java
│   │   │   ├── RequirementDTO.java
│   │   │   ├── CreateRequirementRequest.java
│   │   │   └── UpdateRequirementRequest.java
│   │   ├── entity/                       # JPA 实体类
│   │   │   ├── Artifact.java             # 产出物实体
│   │   │   ├── GenerationJob.java        # 生成作业实体
│   │   │   ├── IRDocument.java           # IR 文档实体
│   │   │   └── Requirement.java          # 需求实体
│   │   ├── repository/                   # JPA 数据访问层
│   │   │   ├── ArtifactRepository.java
│   │   │   ├── GenerationJobRepository.java
│   │   │   ├── IRDocumentRepository.java
│   │   │   └── RequirementRepository.java
│   │   └── service/                      # 业务逻辑层
│   │       ├── ArtifactService.java      # 产出物管理服务
│   │       ├── GenerationJobService.java # 作业管理服务（含异步执行）
│   │       ├── GitLabService.java        # GitLab 集成服务
│   │       ├── IFlowGenerationService.java  # iFlow SDK 集成核心服务
│   │       ├── IRDocumentService.java    # IR 文档管理服务
│   │       └── RequirementService.java   # 需求管理服务
│   ├── src/main/resources/
│   │   └── application.yml               # 应用配置文件
│   └── pom.xml                           # Maven 配置
├── frontend/                             # Vue 3 前端
│   ├── src/
│   │   ├── views/                        # 页面组件
│   │   │   ├── Requirements.vue          # 需求列表
│   │   │   ├── RequirementDetail.vue     # 需求详情（含 IR 编辑器）
│   │   │   ├── Jobs.vue                  # 作业列表
│   │   │   ├── JobDetail.vue             # 作业详情（含日志和产出物）
│   │   │   └── Artifacts.vue             # 产出物管理
│   │   ├── api/                          # API 服务
│   │   │   ├── index.ts                  # Axios 实例配置
│   │   │   ├── requirement.ts            # 需求管理 API
│   │   │   ├── irDocument.ts             # IR 文档管理 API
│   │   │   ├── job.ts                    # 作业管理 API
│   │   │   └── artifact.ts               # 产出物管理 API
│   │   ├── router/index.ts               # Vue Router 配置
│   │   ├── App.vue                       # 根组件
│   │   └── main.ts                       # 入口文件
│   ├── package.json                      # npm 配置
│   └── vite.config.ts                    # Vite 配置
├── scripts/                              # 启动脚本
│   ├── start-all.sh                      # 一键启动前后端
│   ├── start-backend.sh                  # 后端启动脚本
│   └── start-frontend.sh                 # 前端启动脚本
├── docs/                                 # 项目文档
│   ├── QUICKSTART.md                     # 快速入门指南
│   ├── DEPLOYMENT.md                     # 部署指南
│   └── IR_TEMPLATE.md                    # IR 文档模板
├── spec/                                 # 需求和规范文档
│   ├── Me2AI/
│   │   ├── 需求描述.md                   # 项目需求描述
│   │   ├── 技术约束.md                   # 技术约束文档
│   │   └── 蓝图对齐.md                   # 蓝图对齐文档
│   └── AI2AI/
│       └── 第二阶段执行计划.md            # 第二阶段执行计划
├── data/                                 # H2 数据库文件
│   ├── aigendb.mv.db                     # 主数据库文件
│   └── aigendb.trace.db                  # 追踪日志文件
└── generated-code/                       # 代码生成输出目录
```

## 构建和运行

### 环境要求
- Java 17+
- Node.js 18+
- Maven 3.8+

### 启动命令

**一键启动（推荐）**
```bash
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

**分别启动**
```bash
# 后端（端口 8080）
cd backend
mvn spring-boot:run

# 前端（端口 3000）
cd frontend
npm install
npm run dev
```

**访问地址**
- 前端界面：http://localhost:3000
- 后端 API：http://localhost:8080/api
- API 文档：http://localhost:8080/api/swagger-ui.html
- H2 控制台：http://localhost:8080/api/h2-console

### 测试命令

**后端测试**
```bash
cd backend
mvn test
```

**前端代码检查**
```bash
cd frontend
npm run lint
```

**前端构建**
```bash
cd frontend
npm run build
```

## 开发规范

### 后端开发规范

**编码风格**
- 使用 Lombok 简化实体类和 DTO（`@Data`, `@RequiredArgsConstructor`, `@Slf4j`）
- 使用 Spring 注解驱动开发
- 服务层使用异步执行（`@Async`）处理耗时操作（如代码生成）
- 使用 Slf4j 进行日志记录，日志级别：INFO（业务），ERROR（异常）

**数据层规范**
- 使用 Spring Data JPA 进行数据访问
- 实体类必须包含审计字段（`createdBy`, `createdAt`, `updatedAt`）
- 使用 `@CreationTimestamp` 和 `@UpdateTimestamp` 自动管理时间戳
- Repository 接口继承 `JpaRepository`

**API 设计规范**
- 使用 RESTful 风格设计 API
- 统一使用 `ResponseEntity` 返回响应
- 使用 DTO 进行数据传输，不直接暴露实体类
- Controller 层只处理 HTTP 请求/响应，业务逻辑在 Service 层

**异常处理**
- Service 层抛出 `RuntimeException` 异常
- Controller 层捕获异常并返回适当的 HTTP 状态码
- 使用日志记录异常详情

### 前端开发规范

**组件开发**
- 使用 Composition API（`<script setup>`）编写组件
- 使用 TypeScript 进行类型安全开发
- 使用 Element Plus 组件库构建 UI
- 组件文件命名使用 PascalCase（如 `JobDetail.vue`）

**状态管理**
- 使用 Pinia 进行全局状态管理（当前项目主要依赖 API 响应式数据）
- 使用 `ref` 和 `reactive` 管理组件状态

**API 调用**
- 使用 Axios 进行 HTTP 请求
- API 服务封装在 `src/api/` 目录下
- 统一错误处理和消息提示（使用 Element Plus 的 `ElMessage`）

**路由规范**
- 使用 Vue Router 4.2
- 路由配置在 `src/router/index.ts`
- 使用编程式导航（`router.push`, `router.back`）

### 数据库规范

**表命名**
- 使用蛇形命名法（snake_case）：`requirements`, `ir_documents`, `generation_jobs`, `artifacts`

**字段命名**
- 使用蛇形命名法
- 主键统一命名为 `id`
- 外键使用 `*_id` 后缀
- 时间字段使用 `*_at` 后缀

**索引设计**
- 为常用查询字段添加索引
- 唯一约束：`job_code` 在 `generation_jobs` 表中

## 核心功能模块

### 1. 需求管理（Requirement）
**功能**：创建、编辑、删除需求，管理需求状态

**API 端点**
- `POST /api/requirements` - 创建需求
- `GET /api/requirements` - 获取需求列表
- `GET /api/requirements/{id}` - 获取需求详情
- `PUT /api/requirements/{id}` - 更新需求
- `PATCH /api/requirements/{id}/status` - 更新需求状态
- `DELETE /api/requirements/{id}` - 删除需求

**状态枚举**：DRAFT, IN_PROGRESS, COMPLETED, CANCELLED

**关键实体**：`Requirement`（`backend/src/main/java/com/aigen/studio/entity/Requirement.java`）

### 2. IR 文档管理（IRDocument）
**功能**：创建、编辑、验证 IR（中间表示）文档

**API 端点**
- `POST /api/ir-documents` - 创建 IR 文档
- `GET /api/ir-documents/{id}` - 获取 IR 文档
- `PUT /api/ir-documents/{id}` - 更新 IR 文档
- `POST /api/ir-documents/{id}/validate` - 验证 IR 文档
- `DELETE /api/ir-documents/{id}` - 删除 IR 文档

**IR 文档格式**（JSON）
```json
{
  "projectName": "MyProject",
  "modules": [
    {
      "name": "frontend",
      "type": "vue3",
      "features": []
    },
    {
      "name": "backend",
      "type": "springboot",
      "features": []
    }
  ]
}
```

**状态枚举**：DRAFT, VALID, INVALID, LOCKED

**关键实体**：`IRDocument`（`backend/src/main/java/com/aigen/studio/entity/IRDocument.java`）

**前端编辑器**：Monaco Editor（`frontend/src/views/RequirementDetail.vue`）

### 3. 代码生成作业（GenerationJob）
**功能**：创建和执行代码生成作业，跟踪执行状态和日志

**API 端点**
- `POST /api/generation-jobs` - 创建作业
- `POST /api/generation-jobs/{id}/execute` - 执行作业
- `GET /api/generation-jobs/{id}` - 获取作业详情
- `GET /api/generation-jobs/code/{jobCode}` - 通过作业编号获取作业
- `GET /api/generation-jobs/requirement/{requirementId}` - 获取需求的所有作业
- `GET /api/generation-jobs` - 获取作业列表（支持状态筛选）
- `GET /api/generation-jobs/{id}/delivery-logs` - 获取交付日志

**状态枚举**：PENDING, RUNNING, SUCCESS, FAILED, CANCELLED

**核心服务**：
- `GenerationJobService`：作业管理和异步执行
- `IFlowGenerationService`：iFlow SDK 集成，实现代码生成逻辑（`backend/src/main/java/com/aigen/studio/service/IFlowGenerationService.java`）

**执行流程**
1. 创建作业（关联需求和 IR 文档）
2. 执行作业（异步调用 iFlow SDK）
3. iFlow SDK 连接到 iFlow 平台
4. 发送代码生成任务（基于 IR 内容）
5. 接收消息流（AssistantMessage, ToolCallMessage, ToolResultMessage, TaskFinishMessage）
6. 扫描生成的文件
7. 保存产出物到数据库
8. 集成 GitLab（创建项目、上传代码、触发 Pipeline）
9. 更新作业状态为 SUCCESS 或 FAILED

**GitLab 集成流程**
1. 查找或创建 AIGen 群组
2. 为每个作业创建独立项目（`job-{id}`）
3. 创建分支（当前固定为 `v.1.0.0`）
4. 上传生成的代码到 GitLab
5. 更新所有产出物的 Git 信息（项目 ID、分支、URL）
6. 触发 GitLab Pipeline（待实现）

**关键实体**：`GenerationJob`（`backend/src/main/java/com/aigen/studio/entity/GenerationJob.java`）

### 4. 产出物管理（Artifact）
**功能**：查看、预览、下载生成的代码产出物

**API 端点**
- `GET /api/artifacts/job/{jobId}` - 获取作业的所有产出物
- `GET /api/artifacts/{id}` - 获取产出物详情
- `POST /api/artifacts/{id}/deliver` - 交付产出物到 GitLab

**产出物类型**：
- `PROJECT` - 整个项目
- `FRONTEND_CODE` - 前端代码
- `BACKEND_CODE` - 后端代码
- `OPENAPI_SPEC` - OpenAPI 规范
- `SDK` - TypeScript SDK

**关键实体**：`Artifact`（`backend/src/main/java/com/aigen/studio/entity/Artifact.java`）

### 5. 知识库与 RAG（Knowledge / RAG）
**功能**：知识文档摄入、语义检索、会话记忆检索、缓存指标与告警状态观测

**API 端点**
- `POST /api/knowledge/upload` - 上传知识文件（Markdown/PDF）
- `POST /api/knowledge/text` - 摄入文本知识
- `GET /api/knowledge/search` - 检索知识（支持 `conversationId`）
- `GET /api/knowledge/cache/stats` - 查询缓存指标
- `GET /api/knowledge/cache/alerts` - 查询缓存告警状态
- `GET /api/knowledge/perf/stats` - 查询检索性能统计

**核心服务**
- `DocumentIngestionService`：文档解析、分块与向量化入库
- `RAGService`：相似度检索、RAG 增强、缓存与告警判定
- `ConversationVectorService`：会话历史向量化与语义检索

## 配置说明

### 后端配置（`backend/src/main/resources/application.yml`）

**服务器配置**
```yaml
server:
  port: 8080
  servlet:
    context-path: /api
```

**数据库配置**
```yaml
spring:
  datasource:
    url: jdbc:h2:file:/Users/admin/IdeaProjects/aigen_studio/data/aigendb;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  jpa:
    hibernate:
      ddl-auto: update
```

**iFlow SDK 配置**
```yaml
iflow:
  sdk:
    endpoint: https://platform.iflow.cn
    api-key: ${IFLOW_API_KEY:sk-53b6922f314b9738c8083aabb2f7ceda}
    timeout: 60000  # 1 分钟
    output-dir: ${IFLOW_OUTPUT_DIR:../../generated-code}
    permission-mode: AUTO
```

**GitLab 配置**
```yaml
gitlab:
  url: https://git.longhu.net
  token: ***REMOVED***
  base-path: AIGen
  timeout: 600000  # 10 分钟
```

**AIGen RAG 配置**
```yaml
aigen:
  rag:
    search-cache-enabled: true
    search-cache-ttl-seconds: 120
    search-cache-max-size: 500
    alert-enabled: true
    alert-min-requests: 20
    alert-max-miss-rate: 0.60
    alert-log-cooldown-seconds: 300
    perf-latency-threshold-ms: 100
    perf-sample-size: 200
    perf-min-samples: 30
    embedding-batch-size: 16
    conversation-index-timeout-ms: 1500
```

**环境变量**
- `IFLOW_API_KEY` - iFlow SDK API 密钥
- `DASHSCOPE_API_KEY` - LangChain Chat/Embedding API 密钥
- `GITLAB_URL` - GitLab 服务器地址
- `GITLAB_TOKEN` - GitLab 访问令牌
- `IFLOW_OUTPUT_DIR` - 代码生成输出目录

### 前端配置（`frontend/package.json`）

**脚本**
```json
{
  "dev": "vite",
  "build": "vue-tsc && vite build",
  "preview": "vite preview",
  "lint": "eslint . --fix"
}
```

**依赖版本**
- Vue 3.4.0
- TypeScript 5.3.3
- Vite 5.0.8
- Element Plus 2.4.4
- Monaco Editor 0.45.0

## 关键技术实现

### iFlow SDK 集成

**核心类**：`IFlowGenerationService`（`backend/src/main/java/com/aigen/studio/service/IFlowGenerationService.java`）

**集成要点**
1. 使用 `IFlowClient` 连接到 iFlow 平台
2. 配置 `IFlowOptions`：超时时间、权限模式、文件访问白名单
3. 使用响应式编程（Flux）处理消息流
4. 实时捕获日志并保存到数据库
5. 超时处理（默认 5 分钟）
6. 文件扫描和产出物保存

**代码生成流程**
```java
// 1. 创建输出目录
Path outputPath = Paths.get(outputDir, "job-" + job.getId());

// 2. 配置 iFlow 选项
IFlowOptions options = IFlowOptions.builder()
    .autoStartProcess(true)
    .timeout(Duration.ofMillis(timeoutMillis))
    .permissionMode(PermissionMode.AUTO)
    .approvalMode(ApprovalMode.YOLO)
    .fileAccess(true)
    .fileReadOnly(false)
    .fileAllowedDirs(List.of(outputPath.toAbsolutePath().toString()))
    .cwd(outputPath.toAbsolutePath().toString())
    .build();

// 3. 创建客户端并连接
try (IFlowClient client = IFlowClient.create(options)) {
    client.connect().block();

    // 4. 发送任务
    String taskPrompt = buildTaskPrompt(irContent, outputPath);
    client.sendMessage(taskPrompt).block();

    // 5. 接收消息流
    client.receiveMessages()
        .doOnNext(message -> { /* 处理各类消息 */ })
        .blockLast();
}

// 6. 扫描生成的文件
scanGeneratedFiles(outputPath, generatedFiles);

// 7. 保存产出物
saveArtifacts(job, outputPath, generatedFiles);

// 8. 集成 GitLab
integrateWithGitLab(job, outputPath, generatedFiles);
```

### GitLab 集成

**核心类**：`GitLabService`（`backend/src/main/java/com/aigen/studio/service/GitLabService.java`）

**功能**
- 查找群组（findGroupByName）
- 创建或获取项目（createOrGetProject）
- 上传代码（pushCode）
- 创建分支（createBranch）
- 触发 Pipeline（triggerPipeline - 待实现）

**SSL 配置**
由于 GitLab 使用自签名证书，需要配置 SSL 忽略（`SSLConfig.java`）

### 异步任务执行

**配置类**：`AsyncConfig`（`backend/src/main/java/com/aigen/studio/config/AsyncConfig.java`）

**执行方式**
```java
@Async
public void executeJobAsync(Long jobId) {
    // 异步执行代码生成
    IFlowGenerationService.generateCode(job);
}
```

## 第二阶段开发计划

### GitLab 集成增强
- [x] GitLab MCP 集成
- [x] GitLab 分支创建和管理
- [x] GitLab 代码提交和 Push
- [ ] GitLab Pipeline 触发
- [ ] Pipeline 状态跟踪
- [ ] Pipeline 结果展示

### OpenAPI 增强
- [ ] OpenAPI breaking-change 检测
- [ ] 自动化 TS SDK 生成
- [ ] OpenAPI 文档更新

### Evidence 管理
- [ ] Evidence manifest 生成
- [ ] 输入输出记录
- [ ] 质量门禁记录
- [ ] 审计日志

### 可选功能
- [ ] Gravitee 4.0 灰度策略导入导出
- [ ] API 网关配置
- [ ] 发布联动

## Git 工作流

**当前分支**：`v.1.0.0`

**提交规范**
- 功能提交：`[AI] feat: 描述`
- 修复提交：`[AI] fix: 描述`
- 文档提交：`[AI] docs: 描述`
- 重构提交：`[AI] refactor: 描述`

**Git 远程仓库**：`http://git.longhu.net/zhangkun7/aigen_studio.git`

**当前状态**：有未提交的修改（M 标记），包括后端服务、DTO、实体、配置等多个文件的变更

## 常见问题

### 1. iFlow SDK 连接失败
**原因**：API Key 无效或网络问题
**解决**：检查 `IFLOW_API_KEY` 环境变量和 iFlow 平台连接

### 2. GitLab 上传失败
**原因**：Token 无效或自签名证书问题
**解决**：检查 `GITLAB_TOKEN` 环境变量，确保 `SSLConfig` 已正确配置

### 3. 代码生成超时
**原因**：IR 复杂或 iFlow 响应慢
**解决**：调整 `iflow.sdk.timeout` 配置（默认 5 分钟）

### 4. H2 数据库文件丢失
**原因**：数据存储在 `data/` 目录，可能被误删
**解决**：检查 `data/aigendb.mv.db` 文件是否存在

### 5. 前端启动失败
**原因**：依赖未安装或端口被占用
**解决**：运行 `npm install`，检查 3000 端口是否被占用

## 开发建议

### 后端开发
1. 使用 IntelliJ IDEA 进行开发
2. 安装 Lombok 插件
3. 启用 Spring Boot DevTools 热重载
4. 使用 H2 控制台查看数据库内容
5. 查看实时日志：`tail -f backend.log`

### 前端开发
1. 使用 VS Code 进行开发
2. 安装 Volar 插件（Vue 3 支持）
3. 使用 ESLint 进行代码检查
4. 使用 Element Plus 组件库
5. 查看浏览器控制台调试

### 调试技巧
- 后端：使用 `@Slf4j` 记录日志，查看 `backend.log`
- 前端：使用浏览器开发者工具，查看 Network 和 Console
- iFlow：查看作业详情页面的执行日志
- GitLab：查看项目的 Pipeline 和提交历史

## 重要文件路径速查

**核心配置**
- 后端配置：`backend/src/main/resources/application.yml`
- 前端配置：`frontend/package.json`, `frontend/vite.config.ts`
- Maven 配置：`backend/pom.xml`

**核心服务**
- iFlow 集成：`backend/src/main/java/com/aigen/studio/service/IFlowGenerationService.java`
- GitLab 集成：`backend/src/main/java/com/aigen/studio/service/GitLabService.java`
- 作业管理：`backend/src/main/java/com/aigen/studio/service/GenerationJobService.java`

**核心实体**
- 作业实体：`backend/src/main/java/com/aigen/studio/entity/GenerationJob.java`
- 产出物实体：`backend/src/main/java/com/aigen/studio/entity/Artifact.java`
- IR 文档实体：`backend/src/main/java/com/aigen/studio/entity/IRDocument.java`

**核心前端页面**
- 作业详情：`frontend/src/views/JobDetail.vue`
- 需求详情：`frontend/src/views/RequirementDetail.vue`
- 产出物列表：`frontend/src/views/Artifacts.vue`

**文档**
- 项目文档：`PROJECT_README.md`, `README.md`
- 实现总结：`IMPLEMENTATION_SUMMARY.md`
- 需求描述：`spec/Me2AI/需求描述.md`
- 技术约束：`spec/Me2AI/技术约束.md`

## 总结

AIGen Studio 是一个功能完整的 AI 代码生成平台 PoC，集成了 iFlow SDK 实现 AI 驱动的代码生成，并与 GitLab 深度集成实现代码管理和 CI/CD。项目采用现代化的技术栈，前后端分离架构，代码结构清晰，易于维护和扩展。

**核心特点**：
- ✅ 需求管理：完整的需求生命周期管理
- ✅ IR 编辑：在线编辑器支持 JSON 格式验证
- ✅ 代码生成：基于 iFlow SDK 的 AI 代码生成
- ✅ 作业管理：异步执行，实时日志跟踪
- ✅ 产出物管理：多种产出物类型，支持预览和下载
- ✅ GitLab 集成：自动创建项目、上传代码、触发 Pipeline（部分实现）

**下一步**：完成第二阶段 GitLab 集成增强，包括 Pipeline 触发、状态跟踪、OpenAPI breaking-change 检测等功能。
