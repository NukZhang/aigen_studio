# AIGen Studio

基于 Me2AI 规范实现的 AI 代码生成平台 PoC，支持需求管理、IR 编辑、代码生成和产出物管理。

## 🚀 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+

### 一键启动

```bash
# 同时启动前后端
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

或分别启动：

```bash
# 启动后端（端口 8080）
cd backend && mvn spring-boot:run

# 启动前端（端口 3000）
cd frontend && npm install && npm run dev
```

### 访问地址

- 前端界面: http://localhost:3000
- 后端 API: http://localhost:8080/api
- API 文档: http://localhost:8080/api/swagger-ui.html

## 📋 核心功能

- ✅ 需求管理：创建、编辑、删除需求
- ✅ IR 编辑器：在线编辑和验证 IR 文档
- ✅ 代码生成：基于 iFlow SDK 的代码生成
- ✅ 作业管理：创建和执行代码生成作业
- ✅ 产出物管理：查看和下载生成的代码

## 📖 文档

- [项目详细文档](./PROJECT_README.md)
- [IR 文档模板](./docs/IR_TEMPLATE.md)
- [部署指南](./docs/DEPLOYMENT.md)
- [需求规范](./spec/Me2AI/需求描述.md)

## 🏗️ 技术栈

**后端：** Spring Boot 3 + JPA + H2 + iFlow SDK

**前端：** Vue 3 + TypeScript + Element Plus + Vite

## 📦 项目结构

```
aigen_studio/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 3 前端
├── scripts/          # 启动脚本
├── docs/             # 项目文档
└── spec/             # 需求和规范
```

## 🔧 配置

在 `backend/src/main/resources/application.yml` 中配置：

```yaml
iflow:
  sdk:
    endpoint: https://platform.iflow.cn
    api-key: ${IFLOW_API_KEY}

gitlab:
  url: ${GITLAB_URL}
  token: ${GITLAB_TOKEN}
  project-id: ${GITLAB_PROJECT_ID}
```

## 📝 使用流程

1. 创建需求
2. 编辑 IR 文档
3. 验证 IR 文档
4. 创建代码生成作业
5. 执行作业并查看日志
6. 下载生成的代码

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License