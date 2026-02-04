# IR 文档模板 - AIGen Studio 标准架构

## 概述

IR（Intermediate Representation）文档是 AIGen Studio 的核心配置文件，用于描述要生成的项目结构、技术栈、功能模块和部署配置。本模板基于 AIGen Studio 自身的技术架构，确保生成的代码能够顺利部署运行。

## 重要说明

### JSON Schema 协议

**权威定义**：IR 配置的严格结构定义位于 `docs/ir-schema.json`

- ✅ 这是 IR 配置的**官方协议定义**
- ✅ 包含所有字段的类型、约束和验证规则
- ✅ 用于验证 IR 配置的正确性
- ✅ AI 代码生成时遵循此 Schema

**使用方式**：
```bash
# 验证 IR 配置是否符合 Schema
ajv validate -s docs/ir-schema.json -d your-ir-config.json
```

### IR 示例文件

**参考示例**：位于 `docs/ir-examples/` 目录

- `simple-todo-app.json` - 简单待办应用
- `blog-system.json` - 博客系统
- `e-commerce.json` - 电商平台

这些示例展示了不同场景下的 IR 配置，可以作为参考模板。

### 文档用途

本文档（IR_TEMPLATE.md）是**人类可读的文档**，用于：
- ✅ 理解 IR 的结构和用途
- ✅ 学习最佳实践
- ✅ 查看详细说明和示例

**注意**：机器（AI）生成 IR 时应遵循 `ir-schema.json`，而非本文档。

## 文档格式

IR 文档采用 JSON 格式，包含以下主要部分：

```json
{
  "projectName": "项目名称",
  "description": "项目描述",
  "version": "1.0.0",
  "techStack": {
    "frontend": {...},
    "backend": {...}
  },
  "architecture": {
    "frontend": {...},
    "backend": {...}
  },
  "modules": [...],
  "database": {...},
  "deployment": {...},
  "conventions": {...}
}
```

## 完整示例

```json
{
  "projectName": "MyApplication",
  "description": "基于 AIGen Studio 标准架构的应用",
  "version": "1.0.0",

  "techStack": {
    "frontend": {
      "framework": "vue3",
      "version": "3.4.0",
      "language": "typescript",
      "languageVersion": "5.3",
      "buildTool": "vite",
      "buildToolVersion": "5.0",
      "uiLibrary": "element-plus",
      "uiLibraryVersion": "2.4",
      "stateManagement": "pinia",
      "stateManagementVersion": "2.1",
      "router": "vue-router",
      "routerVersion": "4.2",
      "httpClient": "axios",
      "httpClientVersion": "1.6",
      "codeEditor": "monaco-editor",
      "codeEditorVersion": "0.45",
      "markdown": "marked",
      "markdownVersion": "9.0"
    },
    "backend": {
      "framework": "springboot",
      "version": "3.2.0",
      "language": "java",
      "languageVersion": "17",
      "buildTool": "maven",
      "buildToolVersion": "3.8",
      "dataAccess": "spring-data-jpa",
      "database": "h2",
      "databaseVersion": "2.2",
      "security": "spring-security",
      "apiDocumentation": "openapi",
      "apiDocumentationVersion": "3.0",
      "logging": "slf4j",
      "testing": "junit5"
    }
  },

  "architecture": {
    "frontend": {
      "structure": {
        "src": {
          "views": "页面组件",
          "components": "可复用组件",
          "api": "API 服务层",
          "router": "路由配置",
          "stores": "状态管理（Pinia）",
          "types": "TypeScript 类型定义",
          "utils": "工具函数",
          "assets": "静态资源"
        }
      },
      "conventions": {
        "componentNaming": "PascalCase",
        "fileNaming": "kebab-case",
        "apiNaming": "camelCase",
        "typeNaming": "PascalCase",
        "useCompositionApi": true,
        "useTypeScript": true
      }
    },
    "backend": {
      "structure": {
        "src/main/java/com/{company}/{project}": {
          "controller": "REST API 控制器层",
          "service": "业务逻辑层",
          "repository": "数据访问层",
          "entity": "实体类",
          "dto": "数据传输对象",
          "config": "配置类",
          "exception": "异常处理",
          "util": "工具类"
        },
        "src/main/resources": {
          "application.yml": "主配置文件",
          "application-{profile}.yml": "环境配置文件"
        }
      },
      "layering": {
        "controller": "接收 HTTP 请求，返回响应",
        "service": "业务逻辑处理",
        "repository": "数据库操作",
        "entity": "数据模型",
        "dto": "数据传输"
      },
      "conventions": {
        "classNaming": "PascalCase",
        "methodNaming": "camelCase",
        "fieldNaming": "camelCase",
        "constantNaming": "UPPER_SNAKE_CASE",
        "packageNaming": "lowercase",
        "useLombok": true,
        "useJpa": true
      }
    }
  },

  "modules": [
    {
      "name": "frontend",
      "type": "vue3",
      "path": "frontend",
      "features": [
        "user-interface",
        "api-integration",
        "state-management",
        "routing",
        "error-handling"
      ],
      "dependencies": {
        "vue": "^3.4.0",
        "vue-router": "^4.2.0",
        "pinia": "^2.1.0",
        "element-plus": "^2.4.0",
        "axios": "^1.6.0",
        "typescript": "^5.3.0",
        "vite": "^5.0.0"
      },
      "devDependencies": {
        "@vitejs/plugin-vue": "^4.5.0",
        "vue-tsc": "^1.8.0",
        "eslint": "^8.50.0"
      },
      "scripts": {
        "dev": "vite",
        "build": "vue-tsc && vite build",
        "preview": "vite preview",
        "lint": "eslint . --fix"
      }
    },
    {
      "name": "backend",
      "type": "springboot",
      "path": "backend",
      "features": [
        "rest-api",
        "data-persistence",
        "authentication",
        "api-documentation",
        "exception-handling",
        "logging"
      ],
      "dependencies": {
        "spring-boot-starter-web": "3.2.0",
        "spring-boot-starter-data-jpa": "3.2.0",
        "spring-boot-starter-security": "3.2.0",
        "spring-boot-starter-validation": "3.2.0",
        "springdoc-openapi-starter-webmvc-ui": "2.3.0",
        "h2": "2.2.224",
        "lombok": "1.18.30",
        "jackson-databind": "2.16.0"
      },
      "devDependencies": {
        "spring-boot-starter-test": "3.2.0",
        "junit-jupiter": "5.10.1"
      },
      "scripts": {
        "build": "mvn clean package",
        "test": "mvn test",
        "run": "mvn spring-boot:run"
      }
    }
  ],

  "database": {
    "type": "h2",
    "location": "file:./data/aigendb",
    "schema": "public",
    "encoding": "UTF-8",
    "tables": [
      {
        "name": "conversations",
        "description": "对话表",
        "fields": [
          {"name": "id", "type": "BIGINT", "primaryKey": true, "autoIncrement": true},
          {"name": "project_name", "type": "VARCHAR(255)", "nullable": false},
          {"name": "status", "type": "VARCHAR(20)", "nullable": false},
          {"name": "stage", "type": "VARCHAR(50)", "nullable": false},
          {"name": "user_requirement", "type": "TEXT"},
          {"name": "ai_understanding", "type": "TEXT"},
          {"name": "understanding_confirmed", "type": "BOOLEAN"},
          {"name": "generated_code_path", "type": "VARCHAR(500)"},
          {"name": "service_status", "type": "VARCHAR(50)"},
          {"name": "preview_url", "type": "VARCHAR(500)"},
          {"name": "error_message", "type": "TEXT"},
          {"name": "created_by", "type": "VARCHAR(100)"},
          {"name": "created_at", "type": "TIMESTAMP", "nullable": false, "updatable": false},
          {"name": "updated_at", "type": "TIMESTAMP"}
        ]
      },
      {
        "name": "messages",
        "description": "消息表",
        "fields": [
          {"name": "id", "type": "BIGINT", "primaryKey": true, "autoIncrement": true},
          {"name": "conversation_id", "type": "BIGINT", "foreignKey": "conversations.id"},
          {"name": "role", "type": "VARCHAR(20)", "nullable": false},
          {"name": "content", "type": "TEXT"},
          {"name": "sender_name", "type": "VARCHAR(100)"},
          {"name": "timestamp", "type": "TIMESTAMP"},
          {"name": "created_at", "type": "TIMESTAMP", "nullable": false, "updatable": false}
        ]
      }
    ],
    "jpaConfig": {
      "ddlAuto": "update",
      "showSql": false,
      "formatSql": false,
      "dialect": "org.hibernate.dialect.H2Dialect"
    }
  },

  "deployment": {
    "ports": {
      "frontend": 3000,
      "backend": 8080,
      "database": 9092
    },
    "environmentVariables": {
      "required": [
        {"name": "IFLOW_API_KEY", "description": "iFlow SDK API 密钥"}
      ],
      "optional": [
        {"name": "SPRING_PROFILES_ACTIVE", "default": "dev", "description": "Spring 配置文件"},
        {"name": "IFLOW_OUTPUT_DIR", "default": "../../generated-code", "description": "代码生成输出目录"},
        {"name": "LOG_LEVEL", "default": "INFO", "description": "日志级别"}
      ]
    },
    "docker": {
      "enabled": true,
      "compose": true
    },
    "nginx": {
      "enabled": true,
      "config": "/etc/nginx/sites-available/app"
    }
  },

  "conventions": {
    "api": {
      "rest": {
        "baseUrl": "/api",
        "versioning": "none",
        "responseFormat": "JSON",
        "errorHandling": "HTTP status codes"
      },
      "naming": {
        "endpoints": "kebab-case",
        "queryParams": "camelCase"
      }
    },
    "logging": {
      "level": "INFO",
      "format": "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n",
      "file": {
        "enabled": true,
        "path": "./logs/application.log",
        "maxSize": "100MB",
        "maxHistory": 30
      }
    },
    "security": {
      "cors": {
        "enabled": true,
        "allowedOrigins": ["*"],
        "allowedMethods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        "allowedHeaders": ["*"]
      },
      "authentication": {
        "type": "jwt",
        "enabled": false
      }
    },
    "validation": {
      "inputValidation": true,
      "outputValidation": false
    }
  },

  "scripts": {
    "development": {
      "start": "scripts/start-all.sh",
      "startBackend": "scripts/start-backend.sh",
      "startFrontend": "scripts/start-frontend.sh"
    },
    "production": {
      "buildBackend": "cd backend && mvn clean package -DskipTests",
      "buildFrontend": "cd frontend && npm run build",
      "startBackend": "java -jar backend/target/app.jar --spring.profiles.active=prod"
    }
  }
}
```

## 标准模块类型

### vue3

**技术栈：**
- Vue 3.4.0（Composition API）
- TypeScript 5.3
- Vite 5.0
- Element Plus 2.4
- Pinia 2.1
- Vue Router 4.2
- Axios 1.6

**标准结构：**
```
frontend/
├── src/
│   ├── views/           # 页面组件
│   ├── components/      # 可复用组件
│   ├── api/            # API 服务
│   ├── router/         # 路由配置
│   ├── stores/         # Pinia 状态管理
│   ├── types/          # TypeScript 类型
│   ├── utils/          # 工具函数
│   ├── assets/         # 静态资源
│   ├── App.vue         # 根组件
│   └── main.ts         # 入口文件
├── public/             # 公共资源
├── index.html          # HTML 模板
├── package.json        # 依赖配置
├── tsconfig.json       # TypeScript 配置
├── vite.config.ts      # Vite 配置
└── vitest.config.ts    # 测试配置
```

**必需文件：**
- `frontend/index.html` - HTML 模板，必须包含 `<div id="app"></div>`
- `frontend/src/main.ts` - 入口文件，必须创建并挂载 App
- `frontend/src/App.vue` - 根组件，最小可运行组件
- `frontend/vite.config.ts` - Vite 配置，必须包含 Vue 插件
- `frontend/package.json` - 依赖配置，必须包含 dev/build/preview 脚本

### springboot

**技术栈：**
- Spring Boot 3.2.0
- Spring Data JPA
- H2 数据库（开发）/ MySQL（生产）
- Spring Security
- OpenAPI 3.0
- Lombok
- JUnit 5

**标准结构：**
```
backend/
├── src/main/java/com/{company}/{project}/
│   ├── {Project}Application.java  # 应用入口
│   ├── controller/                # REST API 控制器
│   ├── service/                   # 业务逻辑层
│   ├── repository/                # 数据访问层
│   ├── entity/                    # JPA 实体类
│   ├── dto/                       # 数据传输对象
│   ├── config/                    # 配置类
│   ├── exception/                 # 异常处理
│   └── util/                      # 工具类
├── src/main/resources/
│   ├── application.yml            # 主配置文件
│   └── application-{profile}.yml  # 环境配置文件
├── src/test/java/                 # 测试代码
├── data/                          # 数据和日志
└── pom.xml                        # Maven 配置
```

**标准分层：**
1. **Controller 层** - 接收 HTTP 请求，返回响应
   - 使用 `@RestController` 注解
   - 使用 `@RequestMapping` 定义基础路径
   - 返回 `ResponseEntity` 包装响应

2. **Service 层** - 业务逻辑处理
   - 使用 `@Service` 注解
   - 使用 `@RequiredArgsConstructor` 注入依赖
   - 使用 `@Slf4j` 记录日志
   - 抛出 `RuntimeException` 处理错误

3. **Repository 层** - 数据库操作
   - 继承 `JpaRepository`
   - 定义自定义查询方法

4. **Entity 层** - 数据模型
   - 使用 `@Entity` 注解
   - 使用 `@Lombok.Data` 简化代码
   - 使用 `@CreationTimestamp` 和 `@UpdateTimestamp` 管理时间戳
   - 必须包含审计字段：`created_at`, `updated_at`

5. **DTO 层** - 数据传输
   - 用于 Controller 和 Service 之间的数据传递
   - 不直接暴露 Entity

**重要约定：**
- 使用 Jakarta EE（jakarta.servlet.*），不要使用 javax.servlet.*
- 每个实体都必须有对应的 Mapper 接口（如果使用 MyBatis-Plus）

### react

**技术栈：**
- React 18
- TypeScript
- Create React App / Vite
- Material-UI / Ant Design

### nodejs

**技术栈：**
- Node.js 18+
- Express.js
- TypeScript
- MongoDB / PostgreSQL
- Swagger/OpenAPI

## 验证规则

### 基本验证

1. `projectName` 不能为空，只能包含字母、数字、连字符
2. `modules` 必须是数组且至少包含一个元素
3. 每个模块必须包含 `name`、`type` 字段
4. 模块类型必须是支持的类型之一：`vue3`, `springboot`, `react`, `nodejs`

### 技术栈验证

1. 前端模块如果使用 `vue3`，必须配置 `vite` 构建工具
2. 后端模块如果使用 `springboot`，必须配置 `spring-data-jpa`
3. 数据库配置必须与后端技术栈匹配

### 结构验证

1. 前端模块必须包含必需的入口文件（index.html, main.ts, App.vue）
2. 后端模块必须包含 Application 入口类
3. 配置文件必须使用正确的格式（YAML）

## 最佳实践

### 项目命名

- 使用 PascalCase 命名项目
- 避免使用特殊字符
- 名称要能反映项目用途

### 模块拆分

- 前后端分离（至少包含 frontend 和 backend 模块）
- 单一职责原则
- 合理的模块粒度
- 考虑微服务架构（大型项目）

### 技术选型

- 使用成熟稳定的技术栈
- 遵循社区最佳实践
- 考虑长期维护性
- 优先使用本模板推荐的技术栈

### 数据库设计

- 使用合理的字段类型
- 添加必要的索引
- 设置适当的约束
- 包含审计字段（created_at, updated_at）

### API 设计

- RESTful 风格
- 统一的响应格式
- 合理的 HTTP 状态码
- 完善的错误处理

## 代码生成规范

### 前端代码生成

1. **组件生成**
   - 使用 Composition API（`<script setup>`）
   - 使用 TypeScript 类型定义
   - 遵循 PascalCase 命名
   - 添加适当的注释

2. **API 生成**
   - 使用 Axios 封装
   - 统一的错误处理
   - 请求拦截和响应拦截
   - TypeScript 类型定义

3. **路由生成**
   - 使用 Vue Router 4.2
   - 懒加载路由
   - 路由守卫（如需要）

4. **状态管理**
   - 使用 Pinia
   - 模块化设计
   - TypeScript 支持

### 后端代码生成

1. **实体类生成**
   - 使用 Lombok 注解
   - 包含审计字段
   - 适当的关联关系
   - 验证注解（@NotNull, @Size 等）

2. **Controller 生成**
   - RESTful 风格
   - 统一的响应格式
   - 异常处理
   - API 文档注解（@Operation）

3. **Service 生成**
   - 业务逻辑封装
   - 事务管理（@Transactional）
   - 日志记录
   - 异常抛出

4. **Repository 生成**
   - 继承 JpaRepository
   - 自定义查询方法
   - 分页支持

### 配置文件生成

1. **application.yml**
   - 服务器配置
   - 数据库配置
   - 日志配置
   - 环境变量引用

2. **package.json**
   - 必需的脚本：dev, build, preview
   - 版本号使用语义化版本
   - 依赖版本使用 ^ 符号

3. **pom.xml**
   - Spring Boot BOM
   - 依赖版本管理
   - 插件配置

## 部署要求

### 前端部署

1. **构建要求**
   - `npm run build` 必须成功
   - 输出到 `dist` 目录
   - 包含所有必要的静态资源

2. **Nginx 配置**
   - 静态文件服务
   - SPA 路由支持（try_files）
   - API 代理配置

3. **CDN 加速**
   - 静态资源 CDN
   - 缓存策略
   - Gzip 压缩

### 后端部署

1. **构建要求**
   - `mvn clean package` 必须成功
   - 生成可执行 JAR 包
   - 跳过测试（生产环境）

2. **配置管理**
   - 环境变量配置
   - 多环境支持（dev/prod）
   - 敏感信息加密

3. **数据库**
   - H2（开发）或 MySQL/PostgreSQL（生产）
   - 连接池配置
   - 备份策略

### Docker 部署

1. **镜像构建**
   - 多阶段构建
   - 最小化镜像大小
   - 安全扫描

2. **Docker Compose**
   - 服务编排
   - 网络配置
   - 卷挂载

## 测试要求

### 前端测试

- 组件单元测试（Vitest）
- E2E 测试（可选）
- TypeScript 类型检查
- ESLint 代码检查

### 后端测试

- 单元测试（JUnit 5）
- 集成测试（Spring Boot Test）
- API 测试（MockMvc）
- 数据库测试（H2）

## 注意事项

1. **IR 文档验证通过后才能创建代码生成作业**
2. **修改 IR 文档后需要重新验证**
3. **建议在开发阶段使用最小配置进行测试**
4. **复杂项目建议分阶段实现**
5. **生成的代码必须能够直接运行**
6. **确保所有依赖项版本兼容**
7. **检查生成的配置文件是否正确**
8. **验证数据库连接和表结构**
9. **测试 API 端点是否正常工作**
10. **检查前端路由和组件是否正确加载**

## 常见问题

### Q: 为什么生成的代码无法启动？

A: 检查以下几点：
1. 必需的入口文件是否存在（index.html, main.ts, App.vue）
2. 依赖项是否正确安装
3. 配置文件是否正确（application.yml, package.json）
4. 端口是否被占用
5. Java/Node.js 版本是否符合要求

### Q: 如何确保生成的代码符合部署要求？

A: 确保遵循以下规范：
1. 使用本模板推荐的技术栈
2. 包含必需的脚本和配置文件
3. 遵循命名和结构约定
4. 生成后进行本地测试
5. 使用 Docker 进行部署测试

### Q: 支持自定义技术栈吗？

A: 可以，但需要确保：
1. 技术栈在 IR 中正确配置
2. 依赖项版本兼容
3. 配置文件正确
4. 生成代码能正常运行

## 更新日志

- **v1.0.0** - 初始版本，基于 AIGen Studio 标准架构
- 支持的模块类型：vue3, springboot, react, nodejs
- 标准技术栈配置
- 完整的代码生成规范
- 部署和测试要求

## 参考资源

### 核心文档

- **[IR Schema 定义](./ir-schema.json)** - IR 配置的官方协议定义（JSON Schema）
- **[IR 示例](./ir-examples/README.md)** - 各种应用场景的 IR 配置示例
- [AIGen Studio 项目文档](../PROJECT_README.md)
- [实现总结](../IMPLEMENTATION_SUMMARY.md)
- [快速入门](./QUICKSTART.md)
- [部署指南](./DEPLOYMENT.md)

### 官方文档

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Vue 3 官方文档](https://vuejs.org/)
- [Vite 官方文档](https://vitejs.dev/)
- [Element Plus 官方文档](https://element-plus.org/)
- [JSON Schema 规范](https://json-schema.org/)

## 快速链接

- **创建新 IR**：复制示例文件并根据需求修改
- **验证 IR**：使用 `ajv validate -s ir-schema.json -d your-ir.json`
- **查看示例**：浏览 `docs/ir-examples/` 目录