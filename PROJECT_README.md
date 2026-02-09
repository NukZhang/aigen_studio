# AIGen Studio - AI 驱动开发平台

基于 iFlow SDK 实现的 AI 驱动开发平台，通过自然语言对话完成应用开发，支持实时预览和代码编辑。

## 项目概述

AIGen Studio 是一个创新性的 AI 驱动开发平台，彻底改变了传统应用开发的方式。用户只需通过自然语言对话描述需求，AI 就能自动理解、生成代码，并提供实时预览和在线编辑功能。

### 核心价值

- **自然语言交互**：无需编写复杂的配置文件，用对话方式描述需求
- **智能代码生成**：基于 iFlow SDK，自动生成完整的前后端代码
- **实时预览**：一键启动服务，即时查看应用运行效果
- **在线编辑**：内置代码编辑器，随时调整和优化代码
- **多模型支持**：支持选择不同的 AI 模型进行代码生成

## 技术架构

### 后端技术栈

- **框架**: Spring Boot 3.2.0
- **数据持久化**: Spring Data JPA + H2 数据库（文件存储：`data/aigendb.mv.db`）
- **安全**: Spring Security（开发模式开放）
- **API 文档**: OpenAPI 3.0
- **AI 集成**: iFlow SDK 1.0.4-fix（cn.iflow:iflow-cli-sdk）
- **响应式编程**: Project Reactor（用于 iFlow 消息流处理）
- **其他**: Lombok, Jackson

### 前端技术栈

- **框架**: Vue 3.4.0（Composition API）
- **语言**: TypeScript 5.3
- **构建工具**: Vite 5.0
- **UI 组件库**: Element Plus 2.4
- **状态管理**: Pinia 2.1
- **路由**: Vue Router 4.2
- **代码编辑器**: Monaco Editor 0.45
- **Markdown**: marked
- **HTTP 客户端**: Axios 1.6

## 项目结构

```
aigen_studio/
├── backend/                              # Spring Boot 后端
│   ├── src/main/java/com/aigen/studio/
│   │   ├── AigenStudioApplication.java   # 应用入口
│   │   ├── config/                       # 配置类
│   │   │   ├── AsyncConfig.java          # 异步任务配置
│   │   │   ├── ConversationHandler.java  # 对话处理器
│   │   │   ├── GitLabProperties.java     # GitLab 配置属性
│   │   │   ├── OpenApiConfig.java        # Swagger API 文档配置
│   │   │   ├── OutputDirConfig.java      # 输出目录配置
│   │   │   ├── SchemaCleanupConfig.java  # Schema 清理配置
│   │   │   ├── SecurityConfig.java       # 安全配置
│   │   │   ├── SSLConfig.java            # SSL 配置（处理自签名证书）
│   │   │   └── WebSocketConfig.java      # WebSocket 配置
│   │   ├── controller/                   # REST API 控制器
│   │   │   ├── ConversationController.java   # 对话管理 API
│   │   │   ├── PreviewController.java       # 预览管理 API
│   │   │   ├── FileController.java          # 文件管理 API
│   │   │   ├── ModelController.java         # 模型管理 API
│   │   │   └── TutorialController.java      # 教程管理 API
│   │   ├── dto/                          # 数据传输对象
│   │   │   ├── ConfirmUnderstandingRequest.java
│   │   │   ├── ConversationDTO.java
│   │   │   ├── MessageDTO.java
│   │   │   ├── ModelDTO.java
│   │   │   ├── PreviewStatusDTO.java
│   │   │   ├── StreamMessageDTO.java
│   │   │   ├── ToolCallDTO.java
│   │   │   └── FileNodeDTO.java
│   │   ├── entity/                       # JPA 实体类
│   │   │   ├── Conversation.java         # 对话实体
│   │   │   ├── ConversationStage.java    # 对话阶段枚举
│   │   │   └── Message.java              # 消息实体
│   │   ├── repository/                   # JPA 数据访问层
│   │   │   ├── ConversationRepository.java
│   │   │   └── MessageRepository.java
│   │   ├── sdk/                          # SDK 集成层
│   │   │   ├── ICodingService.java       # 编码服务接口
│   │   │   ├── ModelService.java         # 模型服务
│   │   │   ├── README.md
│   │   │   └── iflow/                    # iFlow SDK 实现
│   │   │       └── IFlowClientHelper.java
│   │   └── service/                      # 业务逻辑层
│   │       ├── ConversationService.java          # 对话服务
│   │       ├── PromptTaskService.java            # 提示任务服务
│   │       ├── PreviewService.java               # 预览服务
│   │       ├── FileService.java                  # 文件服务
│   │       ├── FileWorkspaceService.java         # 文件工作区服务
│   │       ├── PreviewConfigResolver.java        # 预览配置解析器
│   │       ├── PreviewScriptService.java         # 预览脚本服务
│   │       ├── TutorialService.java              # 教程服务
│   │       ├── GitLabService.java                # GitLab 集成服务
│   │       ├── ProcessLauncher.java              # 进程启动器接口
│   │       ├── DefaultProcessLauncher.java       # 默认进程启动器
│   │       ├── ProcessTerminator.java            # 进程终止器接口
│   │       └── SystemProcessTerminator.java      # 系统进程终止器
│   ├── src/main/resources/
│   │   └── application.yml               # 应用配置文件
│   ├── src/test/                         # 测试代码
│   │   └── java/com/aigen/studio/
│   │       ├── config/
│   │       │   └── SchemaCleanupConfigTest.java
│   │       ├── controller/
│   │       │   ├── FileControllerConversationTest.java
│   │       │   └── PreviewControllerTest.java
│   │       └── service/
│   │           ├── ConversationReadyStageTest.java
│   │           ├── FileWorkspaceServiceTest.java
│   │           ├── PreviewConfigResolverTest.java
│   │           ├── PreviewScriptServiceTest.java
│   │           ├── PreviewServiceTest.java
│   │           ├── PromptTaskServiceTest.java
│   │           └── SystemProcessTerminatorTest.java
│   ├── data/                             # 运行时数据
│   │   ├── preview-*.log                 # 预览日志
│   │   └── preview-*.pid                 # 进程 PID
│   └── pom.xml                           # Maven 配置
├── frontend/                             # Vue 3 前端
│   ├── src/
│   │   ├── views/                        # 页面组件
│   │   │   ├── Workspace.vue             # 主工作区
│   │   │   ├── PreviewPanel.vue          # 预览面板
│   │   │   ├── FileBrowser.vue           # 文件浏览器
│   │   │   └── CodeEditor.vue            # 代码编辑器
│   │   ├── components/                   # 组件
│   │   │   └── FileTreeNode.vue          # 文件树节点
│   │   ├── api/                          # API 服务
│   │   │   ├── index.ts                  # Axios 实例配置
│   │   │   ├── job.ts                    # 核心 API
│   │   │   └── tutorial.ts               # 教程 API
│   │   ├── router/                       # 路由配置
│   │   │   └── index.ts
│   │   ├── App.vue                       # 根组件
│   │   ├── main.ts                       # 入口文件
│   │   └── vite-env.d.ts
│   ├── public/                           # 静态资源
│   ├── index.html
│   ├── package.json                      # npm 配置
│   ├── tsconfig.json                     # TypeScript 配置
│   ├── vite.config.ts                    # Vite 配置
│   └── vitest.config.ts                  # Vitest 测试配置
├── scripts/                              # 启动脚本
│   ├── start-all.sh                      # 一键启动前后端
│   ├── start-backend.sh                  # 后端启动脚本
│   └── start-frontend.sh                 # 前端启动脚本
├── docs/                                 # 项目文档
│   ├── QUICKSTART.md                     # 快速入门指南
│   ├── DEPLOYMENT.md                     # 部署指南
│   ├── IR_TEMPLATE.md                    # IR 文档模板
│   └── plans/                            # 计划文档
├── spec/                                 # 需求和规范文档
│   ├── Me2AI/                            # 用户需求
│   │   ├── 需求描述.md
│   │   ├── 技术约束.md
│   │   └── 蓝图对齐.md
│   └── AI2AI/                            # AI 执行计划
│       ├── 第二阶段执行计划.md
│       ├── 第三阶段执行计划.md
│       └── 第四阶段执行计划.md
├── data/                                 # H2 数据库文件
│   ├── aigendb.mv.db                     # 主数据库文件
│   └── aigendb.trace.db                  # 追踪日志文件
├── generated-code/                       # 代码生成输出目录
│   └── job-{id}/                         # 每个对话的代码输出
├── tutorials/                            # 教程内容
│   └── README.md
├── .gitignore
├── AGENTS.md                             # iFlow 代理配置
├── README.md                             # 项目主文档
├── IMPLEMENTATION_SUMMARY.md             # 实现总结
└── PROJECT_README.md                     # 本文档
```

## 核心功能模块

### 1. 对话管理（Conversation）

#### 功能描述
管理用户与 AI 的对话会话，支持需求输入、理解确认、代码生成等阶段。

#### 对话阶段

1. **NEED_INPUT** - 需求输入阶段
2. **UNDERSTANDING** - AI 理解需求阶段
3. **UNDERSTANDING_CONFIRMED** - 需求理解确认阶段（等待用户确认）
4. **CODE_GENERATING** - 代码生成阶段
5. **READY_TO_START** - 代码生成完成，等待确认启动
6. **SERVICE_STARTING** - 服务启动阶段
7. **PREVIEWING** - 预览阶段
8. **COMPLETED** - 对话完成
9. **FAILED** - 对话失败

#### API 端点

- `POST /api/conversations/new` - 创建新对话
- `GET /api/conversations/new/{id}` - 获取对话详情
- `GET /api/conversations/active` - 获取活跃对话列表
- `POST /api/conversations/new/{id}/messages` - 发送消息
- `POST /api/conversations/new/{id}/confirm` - 确认理解

#### 关键实体

**Conversation**（`backend/src/main/java/com/aigen/studio/entity/Conversation.java`）

```java
@Entity
@Table(name = "conversations")
public class Conversation {
    private Long id;
    private String projectName;
    private ConversationStatus status;
    private ConversationStage stage;
    private String userRequirement;
    private String aiUnderstanding;
    private Boolean understandingConfirmed;
    private String generatedCodePath;
    private String serviceStatus;
    private String previewUrl;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Message**（`backend/src/main/java/com/aigen/studio/entity/Message.java`）

```java
@Entity
@Table(name = "messages")
public class Message {
    private Long id;
    private Conversation conversation;
    private String role;
    private String content;
    private String senderName;
    private Instant timestamp;
    private LocalDateTime createdAt;
}
```

#### 核心服务

**ConversationService**（`backend/src/main/java/com/aigen/studio/service/ConversationService.java`）
- 创建新对话
- 发送消息到对话
- 确认理解
- 获取活跃对话列表

**PromptTaskService**（`backend/src/main/java/com/aigen/studio/service/PromptTaskService.java`）
- 理解需求（通过 iFlow SDK）
- 生成代码（通过 iFlow SDK）

### 2. 预览服务（Preview）

#### 功能描述
自动启动和管理前后端服务，提供实时预览功能。

#### API 端点

- `POST /api/preview/conversation/{conversationId}/start` - 启动预览
- `POST /api/preview/conversation/{conversationId}/stop` - 停止预览
- `POST /api/preview/conversation/{conversationId}/restart` - 重启预览
- `GET /api/preview/conversation/{conversationId}/status` - 获取预览状态
- `GET /api/preview/conversation/{conversationId}/logs` - 获取预览日志

#### 核心服务

**PreviewService**（`backend/src/main/java/com/aigen/studio/service/PreviewService.java`）

**功能特性**
- 自动检测项目配置（package.json, pom.xml）
- 智能端口分配（避免冲突）
- 进程管理（启动、停止、重启）
- 日志记录（实时写入文件）
- 端口检测（验证服务是否运行）
- PID 管理（防止僵尸进程）

**启动流程**
1. 解析项目配置（PreviewConfigResolver）
2. 检测 frontend 目录
3. 生成启动脚本（PreviewScriptService）
4. 安装依赖（npm install）
5. 启动前端服务（npm run dev）
6. 检测 backend 目录
7. 查找主类（findMainClass）
8. 启动后端服务（mvn spring-boot:run）
9. 记录 PID 和日志
10. 更新对话状态

**PreviewConfigResolver**（`backend/src/main/java/com/aigen/studio/service/PreviewConfigResolver.java`）
- 解析 package.json 获取前端配置
- 解析 pom.xml 获取后端配置
- 智能分配端口

**PreviewScriptService**（`backend/src/main/java/com/aigen/studio/service/PreviewScriptService.java`）
- 生成启动脚本
- 确保路由配置
- 确保依赖安装

### 3. 文件管理（File）

#### 功能描述
管理生成的代码文件，提供文件浏览、查看、编辑功能。

#### API 端点

- `GET /api/files/conversation/{conversationId}/tree` - 获取文件树
- `GET /api/files/conversation/{conversationId}/content` - 读取文件内容
- `POST /api/files/conversation/{conversationId}/content` - 保存文件内容
- `POST /api/files/conversation/{conversationId}/file` - 创建文件
- `POST /api/files/conversation/{conversationId}/directory` - 创建目录
- `PUT /api/files/conversation/{conversationId}/rename` - 重命名
- `DELETE /api/files/conversation/{conversationId}/path` - 删除
- `POST /api/files/conversation/{conversationId}/upload` - 上传文件

#### 核心服务

**FileService**（`backend/src/main/java/com/aigen/studio/service/FileService.java`）
- 获取文件树
- 读取/保存文件内容
- 创建/重命名/删除文件和目录
- 文件上传

**FileWorkspaceService**（`backend/src/main/java/com/aigen/studio/service/FileWorkspaceService.java`）
- 文本读写
- 文件创建
- 目录操作
- 文件上传

### 4. 模型管理（Model）

#### 功能描述
提供 AI 模型选择功能。

#### API 端点

- `GET /api/models` - 获取可用模型列表

#### 核心服务

**ModelService**（`backend/src/main/java/com/aigen/studio/sdk/ModelService.java`）
- 实现模型列表获取
- 提供模型选择功能

### 5. 教程系统（Tutorial）

#### 功能描述
内置开发教程，帮助用户快速上手。

#### API 端点

- `GET /api/tutorials/tree` - 获取教程目录树
- `GET /api/tutorials/content` - 获取教程内容

#### 核心服务

**TutorialService**（`backend/src/main/java/com/aigen/studio/service/TutorialService.java`）
- 读取教程目录
- 解析 Markdown 内容
- 提取标题树

### 6. iFlow SDK 集成

#### 功能描述
通过 iFlow SDK 实现 AI 对话和代码生成。

#### 核心接口

**ICodingService**（`backend/src/main/java/com/aigen/studio/sdk/ICodingService.java`）

```java
public interface ICodingService {
    List<ModelDTO> getAvailableModels();
    void executeTask(String prompt, Path workDir, MessageHandler handler);

    interface MessageHandler {
        void onAssistantMessage(String text);
        void onToolCall(String toolName, String status);
        void onToolResult(String content);
        void onTaskFinish(String stopReason);
        void onError(Throwable error);
        void onComplete();
    }
}
```

#### 消息流处理

iFlow SDK 支持以下消息类型：
- **AssistantMessage** - AI 助手消息
- **ToolCallMessage** - 工具调用消息
- **ToolResultMessage** - 工具结果消息
- **TaskFinishMessage** - 任务完成消息

## 前端架构

### 页面组件

#### Workspace.vue - 主工作区

**功能**
- 对话面板（聊天界面）
- 开发者面板（多标签页）
- 对话历史列表
- 对话阶段指示器
- 理解确认界面
- 模型选择器
- 文件上传

**标签页**
1. **预览** - 实时预览应用
2. **文件** - 浏览和编辑代码文件
3. **发布** - 发布到 GitLab（待实现）
4. **脚本** - 执行构建和运行脚本（待实现）
5. **教程** - 查看开发教程

#### PreviewPanel.vue - 预览面板

**功能**
- 实时预览嵌入（iframe）
- 预览控制按钮（启动/停止/重启）
- 状态显示
- URL 展示

#### FileBrowser.vue - 文件浏览器

**功能**
- 文件树展示
- 文件类型图标
- 目录展开/折叠
- 文件选择

#### CodeEditor.vue - 代码编辑器

**功能**
- Monaco Editor 集成
- 语法高亮
- 代码保存
- 文件内容显示

### API 服务

#### job.ts - 核心 API

```typescript
export const conversationApi = {
  createNewConversation,
  getNewConversation,
  getActiveConversations,
  sendMessageToNewConversation,
  confirmUnderstanding
}

export const previewApi = {
  startPreview,
  stopPreview,
  restartPreview,
  getStatus,
  getLogs
}

export const fileApi = {
  getConversationFileTree,
  getConversationFileContent,
  saveConversationFileContent,
  createConversationFile,
  createConversationDirectory,
  renameConversationPath,
  deleteConversationPath,
  uploadConversationFile
}

export const modelApi = {
  getAvailableModels
}
```

#### tutorial.ts - 教程 API

```typescript
export const getTutorialTree = () => { ... }
export const getTutorialContent = (path: string) => { ... }
```

## 数据库设计

### 表结构

#### conversations - 对话表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| project_name | VARCHAR(255) | 项目名称 |
| status | VARCHAR(20) | 对话状态（ACTIVE, COMPLETED, CANCELLED, FAILED） |
| stage | VARCHAR(50) | 对话阶段（ConversationStage 枚举） |
| user_requirement | TEXT | 用户需求描述 |
| ai_understanding | TEXT | AI 理解的需求（IR 格式） |
| understanding_confirmed | BOOLEAN | 是否确认理解 |
| generated_code_path | VARCHAR(500) | 生成的代码路径 |
| service_status | VARCHAR(50) | 服务状态 |
| preview_url | VARCHAR(500) | 预览 URL |
| error_message | TEXT | 错误信息 |
| created_by | VARCHAR(100) | 创建人 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### messages - 消息表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| conversation_id | BIGINT | 关联的对话 ID |
| role | VARCHAR(20) | 角色（user, assistant） |
| content | TEXT | 消息内容 |
| sender_name | VARCHAR(100) | 发送者名称 |
| timestamp | TIMESTAMP | 时间戳 |
| created_at | TIMESTAMP | 创建时间 |

## 配置说明

### 后端配置（application.yml）

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  application:
    name: aigen-studio-backend

  datasource:
    url: jdbc:h2:file:/Users/admin/IdeaProjects/aigen_studio/data/aigendb;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: password

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        dialect: org.hibernate.dialect.H2Dialect

  h2:
    console:
      enabled: true
      path: /h2-console
      settings:
        web-allow-others: true

  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

# iFlow Configuration
iflow:
  sdk:
    endpoint: https://platform.iflow.cn
    api-key: ${IFLOW_API_KEY:sk-53b6922f314b9738c8083aabb2f7ceda}
    timeout: 60000  # 1 minute
    auto-start-process: true
    file-access: true
    file-read-only: false
    file-max-size: 104857600  # 100MB
    output-dir: ${IFLOW_OUTPUT_DIR:../../generated-code}
    permission-mode: AUTO

# GitLab Configuration
gitlab:
  url: https://git.longhu.net
  token: ***REMOVED***
  base-path: AIGen
  timeout: 600000  # 10 minutes

# Tutorial Configuration
tutorial:
  base-path: tutorials

# Logging
logging:
  level:
    com.aigen.studio: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: ERROR
    org.hibernate.type.descriptor.sql.BasicBinder: ERROR
```

### 环境变量

- `IFLOW_API_KEY` - iFlow SDK API 密钥
- `GITLAB_URL` - GitLab 服务器地址
- `GITLAB_TOKEN` - GitLab 访问令牌
- `IFLOW_OUTPUT_DIR` - 代码生成输出目录

### 前端配置（package.json）

```json
{
  "name": "aigen-studio-frontend",
  "version": "1.0.0",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint . --fix"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.0",
    "pinia": "^2.1.0",
    "element-plus": "^2.4.0",
    "axios": "^1.6.0",
    "marked": "^9.0.0",
    "@monaco-editor/loader": "^1.4.0",
    "monaco-editor": "^0.45.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^4.5.0",
    "typescript": "^5.3.0",
    "vite": "^5.0.0",
    "vue-tsc": "^1.8.0",
    "eslint": "^8.50.0"
  }
}
```

## 使用流程

### 完整开发流程

1. **创建对话**
   - 点击"新建对话"按钮
   - 系统创建新的对话会话

2. **输入需求**
   - 在聊天框中输入需求描述（自然语言）
   - 支持上传图片、文档等附件
   - 选择 AI 模型（可选）

3. **AI 理解需求**
   - AI 分析需求内容
   - 生成结构化的理解结果（IR 格式）
   - 展示理解结果

4. **确认理解**
   - 用户查看 AI 的理解结果
   - 如果正确，点击"确认"
   - 如果有误，点击"不正确"并补充说明

5. **代码生成**
   - AI 自动生成完整代码
   - 生成前端 Vue 3 项目
   - 生成后端 Spring Boot 项目
   - 生成 OpenAPI 规范
   - 实时显示生成进度

6. **确认启动**
   - 代码生成完成后，点击"确认启动"
   - 系统自动启动前后端服务

7. **实时预览**
   - 在预览面板中查看应用运行效果
   - 支持重启预览
   - 查看服务日志

8. **代码编辑**
   - 切换到"文件"标签页
   - 浏览生成的代码文件
   - 在线编辑代码
   - 实时保存修改

9. **查看教程**
   - 切换到"教程"标签页
   - 浏览内置教程
   - 学习最佳实践

### 示例对话

**用户**: 我想创建一个简单的任务管理应用，可以添加、编辑、删除任务，并且支持任务状态标记（完成/未完成）。

**AI**: 我理解您的需求：

1. **项目目标**：创建一个任务管理应用，支持任务的基本 CRUD 操作和状态管理

2. **核心功能**：
   - 添加新任务
   - 编辑现有任务
   - 删除任务
   - 标记任务状态（完成/未完成）

3. **技术栈**：
   - 前端：Vue 3 + TypeScript + Element Plus
   - 后端：Spring Boot 3 + JPA + H2

4. **项目结构**：
   - frontend/src/views/Tasks.vue - 任务列表页面
   - frontend/src/components/TaskForm.vue - 任务表单组件
   - backend/src/main/java/com/taskapp/entity/Task.java - 任务实体
   - backend/src/main/java/com/taskapp/controller/TaskController.java - 任务控制器
   - backend/src/main/java/com/taskapp/service/TaskService.java - 任务服务

5. **其他说明**：使用本地 H2 数据库，支持 RESTful API

**用户**: 确认，开始生成代码。

**AI**: 正在生成代码...

[开始生成前端代码]
[生成后端代码]
[生成配置文件]

代码生成完成！点击下方按钮启动预览服务。

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+

### 一键启动

```bash
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

### 分别启动

```bash
# 启动后端（端口 8080）
cd backend
mvn spring-boot:run

# 启动前端（端口 3000）
cd frontend
npm install
npm run dev
```

### 访问地址

- 前端界面: http://localhost:3000
- 后端 API: http://localhost:8080/api
- H2 控制台: http://localhost:8080/api/h2-console

## 测试

### 后端测试

```bash
cd backend
mvn test
```

### 前端测试

```bash
cd frontend
npm run test
```

## 常见问题

### 1. iFlow SDK 连接失败

**原因**：API Key 无效或网络问题

**解决**：检查 `IFLOW_API_KEY` 环境变量和 iFlow 平台连接

### 2. 预览服务启动失败

**原因**：端口被占用或依赖未安装

**解决**：
- 检查端口是否被占用
- 手动运行 `npm install` 安装依赖
- 查看预览日志：`backend/data/preview-{id}-frontend.log`

### 3. 文件编辑后无法保存

**原因**：文件权限问题或路径错误

**解决**：
- 检查文件路径是否正确
- 确保对 `generated-code/` 目录有写权限

### 4. H2 数据库文件丢失

**原因**：数据存储在 `data/` 目录，可能被误删

**解决**：检查 `data/aigendb.mv.db` 文件是否存在

## 开发指南

### 后端开发

**编码风格**
- 使用 Lombok 简化实体类和 DTO（`@Data`, `@RequiredArgsConstructor`, `@Slf4j`）
- 使用 Spring 注解驱动开发
- 使用 Slf4j 进行日志记录，日志级别：INFO（业务），ERROR（异常）

**数据层规范**
- 使用 Spring Data JPA 进行数据访问
- 实体类必须包含审计字段（`createdAt`, `updatedAt`）
- 使用 `@CreationTimestamp` 和 `@UpdateTimestamp` 自动管理时间戳

**API 设计规范**
- 使用 RESTful 风格设计 API
- 统一使用 `ResponseEntity` 返回响应
- 使用 DTO 进行数据传输，不直接暴露实体类

**异常处理**
- Service 层抛出 `RuntimeException` 异常
- Controller 层捕获异常并返回适当的 HTTP 状态码
- 使用日志记录异常详情

### 前端开发

**组件开发**
- 使用 Composition API（`<script setup>`）编写组件
- 使用 TypeScript 进行类型安全开发
- 使用 Element Plus 组件库构建 UI
- 组件文件命名使用 PascalCase（如 `Workspace.vue`）

**状态管理**
- 使用 Pinia 进行全局状态管理
- 使用 `ref` 和 `reactive` 管理组件状态

**API 调用**
- 使用 Axios 进行 HTTP 请求
- API 服务封装在 `src/api/` 目录下
- 统一错误处理和消息提示（使用 Element Plus 的 `ElMessage`）

**路由规范**
- 使用 Vue Router 4.2
- 路由配置在 `src/router/index.ts`
- 使用编程式导航（`router.push`, `router.back`）

## 待实现功能

### 第二阶段（进行中）

- [ ] GitLab Pipeline 触发
- [ ] Pipeline 状态跟踪
- [ ] 发布功能完善
- [ ] 脚本执行功能

### 第三阶段（计划中）

- [ ] OpenAPI breaking-change 检测
- [ ] 自动化 TS SDK 生成
- [ ] OpenAPI 文档更新
- [ ] Evidence manifest 生成

### 可选功能

- [ ] Gravitee 4.0 灰度策略导入导出
- [ ] API 网关配置
- [ ] 发布联动
- [ ] 多用户协作
- [ ] 项目模板库

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

- 项目地址：http://git.longhu.net/zhangkun7/aigen_studio.git
- 文档：详见 `docs/` 目录
