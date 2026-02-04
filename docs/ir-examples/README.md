# IR 示例文档

本目录包含各种应用场景的 IR（Intermediate Representation）配置示例，用于帮助理解 IR 结构和格式。

## 示例列表

### 1. simple-todo-app.json
**场景**：简单的待办事项管理应用

**功能**：
- 待办事项列表
- 创建待办事项
- 编辑待办事项
- 删除待办事项
- 标记完成/未完成

**技术栈**：
- 前端：Vue 3 + TypeScript + Element Plus
- 后端：Spring Boot + H2
- 数据库：H2（内存数据库）

**特点**：
- 最小化配置
- 适合初学者
- 无用户认证
- 简单的数据模型

**适用场景**：
- 学习 IR 结构
- 测试代码生成
- 快速原型开发

---

### 2. blog-system.json
**场景**：完整的博客系统

**功能**：
- 文章 CRUD 操作
- 评论系统
- 标签管理
- 分类管理
- Markdown 编辑器
- 搜索功能
- 用户认证和授权

**技术栈**：
- 前端：Vue 3 + TypeScript + Element Plus + Marked
- 后端：Spring Boot + Spring Security + MySQL
- 数据库：MySQL 8.0

**特点**：
- 完整的用户系统
- 丰富的内容管理功能
- 支持 Markdown
- RESTful API
- JWT 认证

**适用场景**：
- 内容管理系统（CMS）
- 个人博客
- 技术文档站点

---

### 3. e-commerce.json
**场景**：电商平台

**功能**：
- 商品管理
- 库存管理
- 购物车
- 订单处理
- 支付集成
- 用户认证
- 搜索引擎
- 推荐系统
- 商品对比
- 收藏夹
- 评价和评分

**技术栈**：
- 前端：Vue 3 + TypeScript + Element Plus
- 后端：Spring Boot + Spring Security + PostgreSQL
- 数据库：PostgreSQL 15

**特点**：
- 复杂的数据模型
- 多表关联
- 事务处理
- 支付集成
- 搜索和推荐

**适用场景**：
- 电商平台
- B2C 商城
- 在线商店

## IR 配置结构说明

### 基本结构

```json
{
  "projectName": "项目名称",
  "description": "项目描述",
  "version": "1.0.0",
  "techStack": {...},
  "architecture": {...},
  "modules": [...],
  "database": {...},
  "deployment": {...},
  "conventions": {...},
  "scripts": {...}
}
```

### 必需字段

| 字段 | 类型 | 说明 |
|------|------|------|
| projectName | string | 项目名称 |
| version | string | 版本号 |
| techStack | object | 技术栈配置 |
| architecture | object | 架构配置 |
| modules | array | 模块列表 |

### 可选字段

| 字段 | 类型 | 说明 |
|------|------|------|
| description | string | 项目描述 |
| database | object | 数据库配置 |
| deployment | object | 部署配置 |
| conventions | object | 约定配置 |
| scripts | object | 脚本配置 |

## 技术栈选择

### 前端技术栈

**框架**：
- `vue3` - Vue 3（推荐）
- `react` - React 18
- `vue2` - Vue 2
- `angular` - Angular

**UI 库**：
- `element-plus` - Element Plus（Vue 3）
- `ant-design` - Ant Design（React）
- `material-ui` - Material-UI（React）
- `naive-ui` - Naive UI（Vue 3）

### 后端技术栈

**框架**：
- `springboot` - Spring Boot 3（推荐）
- `nodejs` - Node.js + Express
- `nest` - NestJS
- `django` - Django
- `flask` - Flask

**数据库**：
- `h2` - H2（开发环境）
- `mysql` - MySQL 8.0+
- `postgresql` - PostgreSQL 12+
- `mongodb` - MongoDB 4.0+
- `sqlite` - SQLite 3

## 数据库设计规范

### 表定义示例

```json
{
  "name": "users",
  "description": "用户表",
  "fields": [
    {
      "name": "id",
      "type": "BIGINT",
      "primaryKey": true,
      "autoIncrement": true
    },
    {
      "name": "username",
      "type": "VARCHAR(50)",
      "nullable": false,
      "unique": true
    },
    {
      "name": "created_at",
      "type": "TIMESTAMP",
      "nullable": false,
      "updatable": false
    }
  ]
}
```

### 字段类型

| 类型 | 说明 |
|------|------|
| BIGINT | 长整型（主键） |
| VARCHAR(n) | 变长字符串 |
| TEXT | 长文本 |
| INT | 整数 |
| DECIMAL(m,n) | 小数 |
| BOOLEAN | 布尔值 |
| TIMESTAMP | 时间戳 |

### 字段属性

| 属性 | 类型 | 说明 |
|------|------|------|
| primaryKey | boolean | 是否为主键 |
| autoIncrement | boolean | 是否自增 |
| nullable | boolean | 是否可为空 |
| unique | boolean | 是否唯一 |
| foreignKey | string | 外键引用 |
| defaultValue | string | 默认值 |

## 部署配置

### 端口配置

```json
{
  "ports": {
    "frontend": 3000,
    "backend": 8080,
    "database": 3306
  }
}
```

### 环境变量

```json
{
  "environmentVariables": {
    "required": [
      {
        "name": "DB_HOST",
        "description": "数据库主机"
      }
    ],
    "optional": [
      {
        "name": "SPRING_PROFILES_ACTIVE",
        "default": "dev",
        "description": "Spring 配置文件"
      }
    ]
  }
}
```

## 最佳实践

### 1. 命名规范

- 项目名称：PascalCase（`MyProject`）
- 模块名称：kebab-case（`my-module`）
- 数据库表名：snake_case（`my_table`）
- API 端点：kebab-case（`/api/my-endpoint`）

### 2. 版本控制

- 使用语义化版本：`MAJOR.MINOR.PATCH`
- 主版本号：不兼容的 API 修改
- 次版本号：向下兼容的功能新增
- 修订号：向下兼容的问题修正

### 3. 安全考虑

- 生产环境必须使用认证
- 敏感信息使用环境变量
- 启用 CORS 限制
- 验证所有用户输入

### 4. 数据库设计

- 包含审计字段（created_at, updated_at）
- 使用适当的数据类型
- 添加必要的索引
- 定义外键关系

## 验证 IR 配置

### 使用 JSON Schema 验证

```bash
# 安装 ajv-cli
npm install -g ajv-cli

# 验证 IR 文件
ajv validate -s ../ir-schema.json -d simple-todo-app.json
```

### 在线验证

访问 [JSON Schema Validator](https://www.jsonschemavalidator.net/) 在线验证 IR 配置。

## 自定义 IR 示例

### 步骤

1. **复制示例文件**
   ```bash
   cp simple-todo-app.json my-app.json
   ```

2. **修改配置**
   - 更新 `projectName`
   - 调整 `techStack`
   - 修改 `modules`
   - 定义 `database`

3. **验证配置**
   ```bash
   ajv validate -s ../ir-schema.json -d my-app.json
   ```

4. **测试生成**
   - 使用 IR 配置生成代码
   - 验证生成的代码是否符合预期

## 常见问题

### Q: 如何选择合适的数据库？

**A:**
- 开发/测试：H2（内存数据库）
- 中小型应用：MySQL 8.0+
- 大型应用：PostgreSQL 12+
- 文档存储：MongoDB 4.0+

### Q: 是否必须包含所有字段？

**A:**
- 必需字段必须包含
- 可选字段根据需要添加
- 参考 Schema 定义

### Q: 如何支持多模块？

**A:**
在 `modules` 数组中添加多个模块：
```json
{
  "modules": [
    {
      "name": "frontend",
      "type": "vue3",
      ...
    },
    {
      "name": "backend",
      "type": "springboot",
      ...
    }
  ]
}
```

## 相关文档

- [IR Schema 定义](../ir-schema.json)
- [IR 模板文档](../IR_TEMPLATE.md)
- [项目文档](../PROJECT_README.md)
- [快速入门](../QUICKSTART.md)

## 贡献示例

如果你有其他应用场景的 IR 示例，欢迎提交 PR！

## 更新日志

- **v1.0.0** - 初始版本
  - 添加 simple-todo-app.json
  - 添加 blog-system.json
  - 添加 e-commerce.json