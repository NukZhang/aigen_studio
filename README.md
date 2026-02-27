# AIGen Studio

基于 iFlow SDK 实现的 AI 驱动开发平台，通过自然语言对话完成应用开发，支持实时预览和代码编辑。

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
- H2 控制台: http://localhost:8080/api/h2-console

## 📋 核心功能

- ✅ AI 对话：通过自然语言描述需求，AI 自动理解并生成代码
- ✅ 实时预览：自动启动前后端服务，实时查看应用运行效果
- ✅ 代码编辑：在线查看和编辑生成的代码文件
- ✅ 多模型支持：支持选择不同的 AI 模型进行代码生成
- ✅ 教程系统：内置开发教程，帮助快速上手
- ✅ 知识增强：支持知识摄入、RAG 检索、缓存指标与告警状态查询

## 📖 文档

- [项目详细文档](./PROJECT_README.md)
- [实现总结](./IMPLEMENTATION_SUMMARY.md)
- [快速入门](./docs/QUICKSTART.md)
- [部署指南](./docs/DEPLOYMENT.md)
- [Knowledge / RAG API](./docs/API_KNOWLEDGE_RAG.md)

## 🏗️ 技术栈

**后端：** Spring Boot 3 + JPA + H2 + iFlow SDK

**前端：** Vue 3 + TypeScript + Element Plus + Vite + Monaco Editor

## 📦 项目结构

```
aigen_studio/
├── backend/                      # Spring Boot 后端
│   ├── src/main/java/com/aigen/studio/
│   │   ├── controller/           # REST API 控制器
│   │   ├── service/              # 业务逻辑层
│   │   ├── entity/               # 实体类
│   │   ├── dto/                  # 数据传输对象
│   │   ├── sdk/                  # SDK 集成层
│   │   └── config/               # 配置类
│   └── src/main/resources/
│       └── application.yml       # 应用配置
├── frontend/                     # Vue 3 前端
│   ├── src/
│   │   ├── views/                # 页面组件
│   │   ├── api/                  # API 服务
│   │   ├── components/           # 组件
│   │   └── router/               # 路由配置
│   └── package.json
├── scripts/                      # 启动脚本
├── docs/                         # 项目文档
├── spec/                         # 需求和规范
└── generated-code/               # 代码生成输出目录
```

## 🔧 配置

在 `backend/src/main/resources/application.yml` 中配置：

```yaml
iflow:
  sdk:
    endpoint: https://platform.iflow.cn
    api-key: ${IFLOW_API_KEY}
    output-dir: ${IFLOW_OUTPUT_DIR:../../generated-code}

gitlab:
  url: ${GITLAB_URL}
  token: ${GITLAB_TOKEN}

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
```

常用知识库接口：

- `POST /api/knowledge/upload`
- `POST /api/knowledge/text`
- `GET /api/knowledge/search`
- `GET /api/knowledge/cache/stats`
- `GET /api/knowledge/cache/alerts`
- `GET /api/knowledge/perf/stats`

## 📝 使用流程

1. 创建新对话
2. 输入需求描述（自然语言）
3. AI 理解需求并生成 IR
4. 确认理解内容
5. AI 自动生成代码
6. 启动预览服务
7. 查看实时预览效果
8. 编辑和优化代码

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License
