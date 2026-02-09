# AIGen Studio 实现总结

## 项目概述

AIGen Studio 是一个基于 iFlow SDK 实现的 AI 驱动开发平台，通过自然语言对话完成应用开发。项目已实现从需求理解、代码生成到实时预览的完整开发流程。

## 核心架构

### 对话驱动的开发流程

平台采用对话驱动的开发模式，通过以下阶段完成应用开发：

1. **NEED_INPUT** - 用户输入需求
2. **UNDERSTANDING** - AI 理解需求
3. **UNDERSTANDING_CONFIRMED** - 用户确认理解
4. **CODE_GENERATING** - AI 生成代码
5. **READY_TO_START** - 等待确认启动
6. **SERVICE_STARTING** - 启动服务
7. **PREVIEWING** - 实时预览
8. **COMPLETED** - 生成完成
9. **FAILED** - 生成失败

### 实体设计

#### Conversation（对话实体）
- `id` - 对话 ID
- `projectName` - 项目名称
- `status` - 对话状态（ACTIVE, COMPLETED, CANCELLED, FAILED）
- `stage` - 对话阶段（ConversationStage 枚举）
- `userRequirement` - 用户需求描述
- `aiUnderstanding` - AI 理解的需求（IR 格式）
- `understandingConfirmed` - 是否确认理解
- `generatedCodePath` - 生成的代码路径
- `serviceStatus` - 服务状态
- `previewUrl` - 预览 URL
- `errorMessage` - 错误信息

#### Message（消息实体）
- `id` - 消息 ID
- `conversation` - 关联的对话
- `role` - 角色（user, assistant）
- `content` - 消息内容
- `senderName` - 发送者名称
- `timestamp` - 时间戳

## 后端实现

### 控制器层（5个）

1. **ConversationController** - 对话管理 API
   - 创建新对话
   - 获取对话详情
   - 发送消息
   - 确认理解

2. **PreviewController** - 预览管理 API
   - 启动预览
   - 停止预览
   - 重启预览
   - 获取预览状态
   - 获取预览日志

3. **FileController** - 文件管理 API
   - 获取文件树
   - 读取文件内容
   - 保存文件内容
   - 创建文件/目录
   - 重命名/删除
   - 上传文件

4. **ModelController** - 模型管理 API
   - 获取可用模型列表

5. **TutorialController** - 教程管理 API
   - 获取教程目录树
   - 获取教程内容
   - 提取教程标题树

### 服务层（13个）

#### 核心服务

1. **ConversationService** - 对话服务
   - 创建新对话
   - 发送消息到对话
   - 确认理解
   - 获取活跃对话列表

2. **PromptTaskService** - 提示任务服务
   - 需求理解
   - 代码生成
   - 通过 ICodingService 接口与 SDK 交互

3. **PreviewService** - 预览服务
   - 启动前后端服务
   - 停止服务
   - 进程管理
   - 日志记录
   - 端口检测

4. **FileService** - 文件服务
   - 读取文件树
   - 读取/保存文件内容
   - 创建/重命名/删除文件和目录
   - 文件上传

#### 辅助服务

5. **FileWorkspaceService** - 文件工作区服务
   - 文本读写
   - 文件创建
   - 目录操作
   - 文件上传

6. **PreviewConfigResolver** - 预览配置解析器
   - 解析项目配置文件
   - 自动检测前端和后端配置
   - 端口分配

7. **PreviewScriptService** - 预览脚本服务
   - 生成启动脚本
   - 确保依赖安装
   - 路由配置

8. **TutorialService** - 教程服务
   - 读取教程目录
   - 解析 Markdown 内容
   - 提取标题树

9. **GitLabService** - GitLab 集成服务
   - 项目创建
   - 代码提交
   - Pipeline 触发

#### 进程管理服务

10. **ProcessLauncher** - 进程启动器接口
11. **DefaultProcessLauncher** - 默认进程启动器实现
12. **ProcessTerminator** - 进程终止器接口
13. **SystemProcessTerminator** - 系统进程终止器实现

### SDK 层

#### ICodingService - 编码服务接口
- `getAvailableModels()` - 获取可用模型列表
- `executeTask()` - 执行任务（对话方式）
- `MessageHandler` - 消息处理器接口

#### ModelService - 模型服务
- 实现模型列表获取
- 提供模型选择功能

#### iFlow SDK 集成
- 具体的 iFlow SDK 实现位于 `sdk/iflow/` 目录
- 支持对话式代码生成
- 消息流处理（AssistantMessage, ToolCallMessage, ToolResultMessage, TaskFinishMessage）

### 配置类（7个）

1. **AsyncConfig** - 异步任务配置
2. **ConversationHandler** - 对话处理器
3. **GitLabProperties** - GitLab 配置属性
4. **OpenApiConfig** - Swagger API 文档配置
5. **OutputDirConfig** - 输出目录配置
6. **SchemaCleanupConfig** - Schema 清理配置
7. **SecurityConfig** - 安全配置
8. **SSLConfig** - SSL 配置（处理自签名证书）
9. **WebSocketConfig** - WebSocket 配置

## 前端实现

### 页面组件（4个）

1. **Workspace.vue** - 主工作区
   - 对话面板（聊天界面）
   - 开发者面板（多标签页）
   - 对话历史列表
   - 对话阶段指示器
   - 理解确认界面
   - 模型选择器
   - 文件上传

2. **PreviewPanel.vue** - 预览面板
   - 实时预览嵌入
   - 预览控制按钮（启动/停止/重启）
   - 状态显示
   - URL 展示

3. **FileBrowser.vue** - 文件浏览器
   - 文件树展示
   - 文件类型图标
   - 目录展开/折叠
   - 文件选择

4. **CodeEditor.vue** - 代码编辑器
   - Monaco Editor 集成
   - 语法高亮
   - 代码保存
   - 文件内容显示

### API 服务（3个）

1. **job.ts** - 核心 API
   - `conversationApi` - 对话 API
   - `previewApi` - 预览 API
   - `fileApi` - 文件 API
   - `modelApi` - 模型 API

2. **tutorial.ts** - 教程 API
   - `getTutorialTree()` - 获取教程目录
   - `getTutorialContent()` - 获取教程内容

### 组件（1个）

1. **FileTreeNode.vue** - 文件树节点组件

## 核心功能实现

### 1. 对话创建和管理

```java
// 创建新对话
@PostMapping("/conversations/new")
public ResponseEntity<ConversationDTO> createNewConversation(
        @RequestParam(required = false, defaultValue = "user") String createdBy)

// 发送消息
@PostMapping("/conversations/new/{id}/messages")
public ResponseEntity<MessageDTO> sendMessageToNewConversation(
        @PathVariable Long id,
        @RequestBody MessageDTO message)

// 确认理解
@PostMapping("/conversations/new/{id}/confirm")
public ResponseEntity<ConversationDTO> confirmUnderstanding(
        @PathVariable Long id,
        @RequestBody ConfirmUnderstandingRequest request)
```

### 2. 代码生成流程

```java
// PromptTaskService 提供任务执行逻辑
public void generateCode(
        String irContent,
        Path outputPath,
        Consumer<String> logConsumer) {
    // 构建代码生成提示词
    // 通过 ICodingService.executeTask 执行
    // 实时处理消息流
}
```

### 3. 预览服务管理

```java
// 启动预览服务
public PreviewStatusDTO startPreview(Long conversationId) {
    // 解析项目配置
    // 启动前端服务（npm run dev）
    // 启动后端服务（mvn spring-boot:run）
    // 记录进程 PID
    // 启动日志记录线程
    // 更新对话状态
}

// 端口检测
private boolean isPortInUse(int port) {
    // 通过 ServerSocket 检测端口是否被占用
}
```

### 4. 文件管理

```java
// 获取文件树
public List<FileNodeDTO> getConversationFileTree(Long conversationId) {
    // 递归构建文件树
    // 文件类型识别
    // 大小计算
}

// 文件读写
public String getConversationFileContent(Long conversationId, String filePath)
public void saveConversationFileContent(Long conversationId, String filePath, String content)
```

## 技术栈

### 后端技术栈

- **框架**: Spring Boot 3.2.0
- **数据持久化**: Spring Data JPA + H2 数据库
- **安全**: Spring Security（开发模式开放）
- **API 文档**: OpenAPI 3.0
- **AI 集成**: iFlow SDK
- **响应式编程**: Project Reactor（用于 iFlow 消息流处理）

### 前端技术栈

- **框架**: Vue 3.4.0 + TypeScript 5.3
- **构建工具**: Vite 5.0
- **UI 组件库**: Element Plus 2.4
- **状态管理**: Pinia 2.1
- **路由**: Vue Router 4.2
- **代码编辑器**: Monaco Editor 0.45
- **HTTP 客户端**: Axios 1.6

## 数据库设计

### 表结构

1. **conversations** - 对话表
   - id, project_name, status, stage
   - user_requirement, ai_understanding, understanding_confirmed
   - generated_code_path, service_status, preview_url, error_message
   - created_by, created_at, updated_at

2. **messages** - 消息表
   - id, conversation_id, role, content, sender_name
   - timestamp, created_at

## 配置说明

### 应用配置（application.yml）

```yaml
server:
  port: 8080

iflow:
  sdk:
    endpoint: https://platform.iflow.cn
    api-key: ${IFLOW_API_KEY}
    timeout: 60000
    auto-start-process: true
    file-access: true
    output-dir: ${IFLOW_OUTPUT_DIR:../../generated-code}

gitlab:
  url: https://git.longhu.net
  token: ***REMOVED***
  base-path: AIGen
  timeout: 600000

tutorial:
  base-path: tutorials
```

## 使用流程

1. 用户创建新对话
2. 输入需求描述（自然语言）
3. AI 理解需求并生成 IR
4. 用户确认理解内容
5. AI 自动生成代码（前端 Vue + 后端 Spring Boot）
6. 用户确认启动
7. 系统自动启动前后端服务
8. 实时预览应用运行效果
9. 在线编辑和优化代码
10. 发布到 GitLab（待实现）

## 已实现功能

✅ 对话创建和管理
✅ 需求理解和确认
✅ AI 代码生成（通过 iFlow SDK）
✅ 前后端服务自动启动
✅ 实时预览功能
✅ 文件浏览器
✅ 在线代码编辑器
✅ 模型选择
✅ 教程系统
✅ 进程管理（启动、停止、重启）
✅ 日志记录和查看
✅ GitLab 集成基础

## 待实现功能

- GitLab Pipeline 触发
- 发布功能完善
- 脚本执行功能
- OpenAPI breaking-change 检测
- 自动化 TS SDK 生成
- Evidence manifest 生成

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

## 总结

AIGen Studio 已实现从需求理解、代码生成到实时预览的完整开发流程。项目采用对话驱动的开发模式，通过 iFlow SDK 实现了 AI 代码生成能力，并提供了完善的前后端服务管理和代码编辑功能。

项目架构清晰，代码质量良好，文档完善，可以作为一个功能完整的 AI 驱动开发平台使用。
