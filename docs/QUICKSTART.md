# AIGen Studio 快速入门指南

本指南将帮助你在 5 分钟内启动 AIGen Studio 并完成第一个代码生成任务。

## 第一步：环境准备

确保你已经安装了以下软件：

```bash
# 检查 Java 版本（需要 17+）
java -version

# 检查 Maven 版本
mvn -version

# 检查 Node.js 版本（需要 18+）
node -v
```

如果未安装，请先安装这些依赖。

## 第二步：启动项目

### 方式一：一键启动（推荐）

```bash
cd aigen_studio
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

### 方式二：分别启动

**启动后端：**
```bash
cd backend
mvn spring-boot:run
```

**启动前端（新终端）：**
```bash
cd frontend
npm install
npm run dev
```

## 第三步：访问系统

打开浏览器访问：

- 前端界面：http://localhost:3000
- API 文档：http://localhost:8080/api/swagger-ui.html

## 第四步：创建第一个需求

1. 点击"需求管理"菜单
2. 点击"新建需求"按钮
3. 填写需求信息：
   - 需求编号：`REQ-2024-001`
   - 标题：`电商管理平台`
   - 描述：`一个简单的电商管理系统，包含商品和订单管理功能`
4. 点击"确定"保存

## 第五步：编辑 IR 文档

1. 在需求列表中，点击刚创建的需求的"查看详情"
2. 在 IR 文档区域，点击"创建 IR 文档"
3. 使用以下模板作为起始内容：

```json
{
  "projectName": "ECommercePlatform",
  "modules": [
    {
      "name": "frontend",
      "type": "vue3",
      "features": [
        "product-list",
        "order-management",
        "user-authentication"
      ]
    },
    {
      "name": "backend",
      "type": "springboot",
      "features": [
        "product-service",
        "order-service",
        "user-service"
      ]
    }
  ]
}
```

4. 点击"验证"按钮，确保 IR 文档格式正确
5. 点击"保存"按钮保存 IR 文档

## 第六步：生成代码

1. 在作业列表区域，点击"发起作业"按钮
2. 系统会创建一个新的代码生成作业
3. 点击"执行"按钮开始代码生成
4. 在执行日志区域查看生成进度

## 第七步：查看产出物

代码生成完成后：

1. 在产出物列表中查看生成的文件
2. 点击"查看"按钮预览代码
3. 点击"下载"按钮下载生成的代码

## 常见问题

### 后端启动失败

**问题：** 端口 8080 被占用

**解决方案：**
```bash
# 查找占用端口的进程
lsof -i :8080

# 杀死进程
kill -9 <PID>
```

### 前端启动失败

**问题：** npm install 失败

**解决方案：**
```bash
# 清除缓存重新安装
rm -rf node_modules package-lock.json
npm install
```

### IR 验证失败

**问题：** IR 文档格式不正确

**解决方案：**
- 确保 JSON 格式正确
- 检查必需字段：`projectName` 和 `modules`
- 确保 `modules` 是数组且至少包含一个元素

### 作业执行失败

**问题：** iFlow SDK 连接失败

**解决方案：**
- 检查 `IFLOW_API_KEY` 环境变量是否配置
- 确认 iFlow 服务是否可访问

## 下一步

- 阅读 [项目详细文档](../PROJECT_README.md) 了解更多功能
- 查看 [IR 文档模板](./IR_TEMPLATE.md) 学习更多配置选项
- 参考 [部署指南](./DEPLOYMENT.md) 了解生产环境部署

## 获取帮助

如果遇到问题：

1. 查看项目文档
2. 检查后端日志：`backend/logs/application.log`
3. 检查前端控制台错误
4. 提交 Issue 到项目仓库

祝你使用愉快！🎉