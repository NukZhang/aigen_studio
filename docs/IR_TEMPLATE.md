# IR 文档模板

## 基本信息

IR（Intermediate Representation）文档是 AIGen Studio 的核心配置文件，用于描述要生成的项目结构和功能模块。

## 文档格式

IR 文档采用 JSON 格式，必须包含以下字段：

### 必需字段

- `projectName`: 项目名称（字符串，非空）
- `modules`: 模块数组（至少包含一个模块）

### 模块结构

每个模块包含以下字段：

- `name`: 模块名称
- `type`: 模块类型（支持：vue3, springboot, react, nodejs）
- `features`: 功能列表（数组）

## 示例

### 最小示例

```json
{
  "projectName": "MyApp",
  "modules": [
    {
      "name": "frontend",
      "type": "vue3",
      "features": []
    }
  ]
}
```

### 完整示例

```json
{
  "projectName": "ECommercePlatform",
  "modules": [
    {
      "name": "frontend",
      "type": "vue3",
      "features": [
        "user-authentication",
        "product-list",
        "shopping-cart",
        "order-management"
      ]
    },
    {
      "name": "backend",
      "type": "springboot",
      "features": [
        "user-service",
        "product-service",
        "order-service",
        "payment-service"
      ]
    }
  ]
}
```

### 企业级应用示例

```json
{
  "projectName": "EnterpriseCRM",
  "modules": [
    {
      "name": "web-frontend",
      "type": "vue3",
      "features": [
        "customer-management",
        "lead-tracking",
        "sales-reporting",
        "dashboard-analytics"
      ]
    },
    {
      "name": "mobile-app",
      "type": "react",
      "features": [
        "mobile-customer-view",
        "push-notifications",
        "offline-mode"
      ]
    },
    {
      "name": "api-gateway",
      "type": "springboot",
      "features": [
        "rate-limiting",
        "authentication",
        "load-balancing"
      ]
    },
    {
      "name": "core-service",
      "type": "springboot",
      "features": [
        "database-operations",
        "business-logic",
        "external-integrations"
      ]
    }
  ]
}
```

## 模块类型说明

### vue3
Vue 3 前端应用，支持：
- Composition API
- TypeScript
- Vite 构建工具
- Element Plus UI 组件库

### springboot
Spring Boot 后端应用，支持：
- RESTful API
- Spring Data JPA
- Spring Security
- OpenAPI 文档生成

### react
React 前端应用，支持：
- React 18
- TypeScript
- Create React App
- Material-UI

### nodejs
Node.js 后端应用，支持：
- Express.js
- TypeScript
- MongoDB/PostgreSQL
- Swagger/OpenAPI

## 验证规则

1. `projectName` 不能为空
2. `modules` 必须是数组且至少包含一个元素
3. 每个模块必须包含 `name`、`type` 字段
4. 模块类型必须是支持的类型之一

## 最佳实践

1. 使用有意义的模块名称
2. 合理拆分功能模块
3. 遵循单一职责原则
4. 考虑前后端分离架构
5. 为功能模块添加清晰的描述

## 注意事项

- IR 文档验证通过后才能创建代码生成作业
- 修改 IR 文档后需要重新验证
- 建议在开发阶段使用最小配置进行测试
- 复杂项目建议分阶段实现