# AIGen Studio - AI 代码生成平台

基于 Me2AI 规范实现的 AI 代码生成平台 PoC，支持需求管理、IR 编辑、代码生成和产出物管理。

## 项目架构

### 技术栈

**后端**
- Spring Boot 3.2.0
- Spring Data JPA
- H2 数据库（开发环境）
- Spring Security
- OpenAPI 3.0（Swagger）
- iFlow SDK（代码生成）

**前端**
- Vue 3.4.0
- TypeScript 5.3
- Vite 5.0
- Element Plus 2.4
- Pinia（状态管理）
- Vue Router 4.2

### 项目结构

```
aigen_studio/
├── backend/                 # 后端 Spring Boot 项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/aigen/studio/
│   │   │   │   ├── controller/     # REST API 控制器
│   │   │   │   ├── service/        # 业务逻辑层
│   │   │   │   ├── repository/     # 数据访问层
│   │   │   │   ├── entity/         # 实体类
│   │   │   │   ├── dto/            # 数据传输对象
│   │   │   │   └── config/         # 配置类
│   │   │   └── resources/
│   │   │       └── application.yml # 应用配置
│   │   └── test/
│   └── pom.xml
├── frontend/                # 前端 Vue 项目
│   ├── src/
│   │   ├── views/           # 页面组件
│   │   ├── api/             # API 服务
│   │   ├── router/          # 路由配置
│   │   ├── App.vue          # 根组件
│   │   └── main.ts          # 入口文件
│   ├── index.html
│   ├── package.json
│   └── vite.config.ts
└── spec/                    # 需求和规范文档
    └── Me2AI/
```

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+

### 后端启动

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动

API 文档访问: `http://localhost:8080/api/swagger-ui.html`

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端服务将在 `http://localhost:3000` 启动

## 核心功能

### 1. 需求管理

- 创建、编辑、删除需求
- 需求状态管理（草稿、进行中、已完成、已取消）
- 需求列表查看和筛选

### 2. IR 文档编辑

- 在线编辑 IR（中间表示）文档
- JSON 格式验证
- IR 状态管理（草稿、有效、无效、锁定）

### 3. 代码生成作业

- 创建代码生成作业
- 异步执行代码生成
- 实时日志查看
- 作业状态跟踪

### 4. 产出物管理

- 查看生成的代码产出物
- 产出物预览和下载
- 支持多种产出物类型（前端代码、后端代码、OpenAPI 规范、SDK 等）

## API 接口

### 需求管理

- `POST /api/requirements` - 创建需求
- `GET /api/requirements` - 获取需求列表
- `GET /api/requirements/{id}` - 获取需求详情
- `PUT /api/requirements/{id}` - 更新需求
- `PATCH /api/requirements/{id}/status` - 更新需求状态
- `DELETE /api/requirements/{id}` - 删除需求

### IR 文档管理

- `POST /api/ir-documents` - 创建 IR 文档
- `GET /api/ir-documents/{id}` - 获取 IR 文档
- `PUT /api/ir-documents/{id}` - 更新 IR 文档
- `POST /api/ir-documents/{id}/validate` - 验证 IR 文档
- `DELETE /api/ir-documents/{id}` - 删除 IR 文档

### 作业管理

- `POST /api/generation-jobs` - 创建作业
- `GET /api/generation-jobs` - 获取作业列表
- `GET /api/generation-jobs/{id}` - 获取作业详情
- `POST /api/generation-jobs/{id}/execute` - 执行作业

### 产出物管理

- `GET /api/artifacts/job/{jobId}` - 获取作业产出物
- `GET /api/artifacts/{id}` - 获取产出物详情

## IR 文档格式

IR 文档采用 JSON 格式，定义项目结构和功能模块：

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

## 开发计划

### 第一阶段（已完成）
- ✅ 项目架构设计
- ✅ 后端 Spring Boot 项目骨架
- ✅ 前端 Vue 项目骨架
- ✅ 需求管理功能
- ✅ IR 文档编辑功能
- ✅ 作业管理功能
- ✅ iFlow SDK 集成

### 第二阶段（计划中）
- GitLab MCP 集成
- OpenAPI 生成和 breaking-change 检测
- TypeScript SDK 生成
- GitLab CI/CD 触发
- Evidence manifest 落盘

## 配置说明

### 后端配置（application.yml）

```yaml
server:
  port: 8080

iflow:
  sdk:
    endpoint: https://platform.iflow.cn
    api-key: ${IFLOW_API_KEY}

gitlab:
  url: ${GITLAB_URL}
  token: ${GITLAB_TOKEN}
  project-id: ${GITLAB_PROJECT_ID}
```

### 环境变量

- `IFLOW_API_KEY` - iFlow SDK API 密钥
- `GITLAB_URL` - GitLab 服务器地址
- `GITLAB_TOKEN` - GitLab 访问令牌
- `GITLAB_PROJECT_ID` - GitLab 项目 ID

## 注意事项

1. 本项目为 PoC 验证版本，部分功能为模拟实现
2. iFlow SDK 需要配置有效的 API 密钥才能正常工作
3. GitLab 集成功能需要配置 GitLab 访问权限
4. 建议使用 IntelliJ IDEA 或 VS Code 进行开发

## 许可证

MIT License