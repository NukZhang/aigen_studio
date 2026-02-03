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

## 第四步：创建第一个对话

1. 点击"新建对话"按钮
2. 填写项目信息与需求描述
3. 发送需求，系统会生成理解内容

## 第五步：确认理解并生成代码

1. 点击"确认理解"
2. 系统开始生成代码并输出日志
3. 生成完成后，页面会提示可启动服务

## 第六步：查看生成代码

1. 打开"文件"页签
2. 在文件树中浏览生成的目录与文件
3. 点击文件查看内容，必要时保存修改

**代码输出位置：**
- 生成代码保存到 `iflow.sdk.output-dir` 指定的目录
- 每个对话输出到 `conversation-{id}` 子目录

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

### 代码生成失败

**问题：** iFlow SDK 连接失败

**解决方案：**
- 检查 `IFLOW_API_KEY` 环境变量是否配置
- 确认 iFlow 服务是否可访问

## 下一步

- 阅读 [项目详细文档](../PROJECT_README.md) 了解更多功能
- 参考 [部署指南](./DEPLOYMENT.md) 了解生产环境部署

## 获取帮助

如果遇到问题：

1. 查看项目文档
2. 检查后端日志：`backend/logs/application.log`
3. 检查前端控制台错误
4. 提交 Issue 到项目仓库

祝你使用愉快！🎉
