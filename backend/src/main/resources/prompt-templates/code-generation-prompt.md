模板模式：CODE_GENERATION

请根据以下 IR 配置生成完整的代码项目：

IR 配置：
{{IR_CONTENT}}

要求：
1. 在当前工作目录生成完整的项目结构
2. 生成前端 Vue 3 项目（如果包含 frontend 模块）
3. 生成后端 Spring Boot 项目（如果包含 backend 模块）
4. 生成 OpenAPI 规范文件 openapi.yaml
5. 生成 TypeScript SDK（如果需要）
6. 确保所有代码都是完整的、可运行的
7. 添加必要的配置文件和说明文档
8. 如果包含 frontend 模块，必须生成最小可运行的 Vite + Vue3 前端骨架：
   - frontend/index.html（含 #app 且引用 /src/main.ts）
   - frontend/src/main.ts（创建并挂载 App）
   - frontend/src/App.vue（最小根组件即可）
   - frontend/vite.config.(ts|js)（含 Vue 插件；路由需 createWebHistory(import.meta.env.BASE_URL)）
   - frontend/package.json（含 dev/build/preview 脚本和依赖）
9. 若暂时没有业务页面，也必须生成上述入口文件，不要只创建目录或空 src。
10. 后端使用 Spring Boot 3.x 时，必须使用 jakarta.servlet.*，不要使用 javax.servlet.*。
11. 如果使用 MyBatis-Plus，每个实体都要有对应的 Mapper 接口文件。
12. 后端必须生成 schema.sql，且该脚本必须兼容 H2（预览默认使用 H2）：
    - 禁止使用 CREATE DATABASE、USE 等数据库级语句
    - 禁止使用 ENGINE=、CHARSET、COLLATE 等 MySQL 专属语法
    - 禁止在 CREATE TABLE 中使用 KEY/UNIQUE KEY；索引请使用 CREATE INDEX/CREATE UNIQUE INDEX 单独创建
    - 表必须使用 CREATE TABLE IF NOT EXISTS
    - 尽量使用通用数据类型（INT/BIGINT/VARCHAR/TEXT/DECIMAL/DATE/DATETIME）
13. 如果后端使用 H2 作为默认开发数据库，application.yml 中请设置 spring.sql.init.mode=embedded。
14. 禁止使用实体命名 Character，避免与 java.lang.Character 冲突；建议使用 PersonalityCharacter/HistoricalCharacter。
15. 不要使用实体包通配符导入（import ...entity.*），请显式 import 需要的实体。
16. selectCount 返回 Long，请使用 Math.toIntExact(...) 或 .intValue()。

输出目录：{{OUTPUT_PATH}}

请开始生成代码，并详细说明每个步骤。
