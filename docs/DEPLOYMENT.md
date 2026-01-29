# AIGen Studio 部署指南

## 开发环境部署

### 前置要求

- Java 17+
- Maven 3.8+
- Node.js 18+
- npm 9+

### 后端部署

1. 进入后端目录
```bash
cd backend
```

2. 配置环境变量
```bash
export IFLOW_API_KEY=your-api-key-here
export GITLAB_URL=https://gitlab.com
export GITLAB_TOKEN=your-gitlab-token
export GITLAB_PROJECT_ID=your-project-id
```

3. 编译项目
```bash
mvn clean install
```

4. 启动服务
```bash
mvn spring-boot:run
```

或使用启动脚本：
```bash
chmod +x scripts/start-backend.sh
./scripts/start-backend.sh
```

服务将在 `http://localhost:8080` 启动

### 前端部署

1. 进入前端目录
```bash
cd frontend
```

2. 安装依赖
```bash
npm install
```

3. 启动开发服务器
```bash
npm run dev
```

或使用启动脚本：
```bash
chmod +x scripts/start-frontend.sh
./scripts/start-frontend.sh
```

服务将在 `http://localhost:3000` 启动

### 同时启动前后端

```bash
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

## 生产环境部署

### 后端部署

1. 构建 JAR 包
```bash
cd backend
mvn clean package -DskipTests
```

2. 使用生产配置文件
```bash
java -jar target/aigen-studio-backend-1.0.0.jar --spring.profiles.active=prod
```

3. 使用 systemd 管理（Linux）

创建服务文件 `/etc/systemd/system/aigen-backend.service`:
```ini
[Unit]
Description=AIGen Studio Backend
After=network.target

[Service]
Type=simple
User=aigen
WorkingDirectory=/opt/aigen-studio/backend
ExecStart=/usr/bin/java -jar /opt/aigen-studio/backend/aigen-studio-backend-1.0.0.jar
Restart=always
RestartSec=10
Environment=IFLOW_API_KEY=your-api-key
Environment=GITLAB_URL=https://gitlab.com
Environment=GITLAB_TOKEN=your-token
Environment=GITLAB_PROJECT_ID=your-project-id

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable aigen-backend
sudo systemctl start aigen-backend
```

### 前端部署

1. 构建生产版本
```bash
cd frontend
npm run build
```

2. 使用 Nginx 部署

创建 Nginx 配置 `/etc/nginx/sites-available/aigen-frontend`:
```nginx
server {
    listen 80;
    server_name aigen.example.com;

    root /var/www/aigen-studio/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用配置：
```bash
sudo ln -s /etc/nginx/sites-available/aigen-frontend /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Docker 部署

#### 后端 Dockerfile

```dockerfile
FROM maven:3.8-openjdk-17-slim AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17-slim
WORKDIR /app
COPY --from=builder /app/target/aigen-studio-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 前端 Dockerfile

```dockerfile
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
```

#### Docker Compose

```yaml
version: '3.8'

services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - IFLOW_API_KEY=${IFLOW_API_KEY}
      - GITLAB_URL=${GITLAB_URL}
      - GITLAB_TOKEN=${GITLAB_TOKEN}
      - GITLAB_PROJECT_ID=${GITLAB_PROJECT_ID}
    restart: unless-stopped

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    restart: unless-stopped
```

启动：
```bash
docker-compose up -d
```

## 数据库配置

### H2 数据库（开发环境）

默认使用内存数据库，无需额外配置。

### MySQL 数据库（生产环境）

1. 创建数据库
```sql
CREATE DATABASE aigen_studio CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 修改 application-prod.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aigen_studio
    username: aigen_user
    password: your-password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

## 环境变量配置

### 必需变量

- `IFLOW_API_KEY`: iFlow SDK API 密钥
- `GITLAB_URL`: GitLab 服务器地址
- `GITLAB_TOKEN`: GitLab 访问令牌
- `GITLAB_PROJECT_ID`: GitLab 项目 ID

### 可选变量

- `SPRING_PROFILES_ACTIVE`: Spring 配置文件（dev/prod）
- `SERVER_PORT`: 服务端口（默认 8080）
- `LOG_LEVEL`: 日志级别（DEBUG/INFO/WARN/ERROR）

## 监控和日志

### 日志配置

生产环境建议使用以下配置：

```yaml
logging:
  level:
    root: INFO
    com.aigen.studio: INFO
  file:
    name: /var/log/aigen-studio/application.log
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### 健康检查

后端提供健康检查端点：
```
GET /api/actuator/health
```

## 安全建议

1. 使用 HTTPS 协议
2. 配置防火墙规则
3. 定期更新依赖包
4. 使用密钥管理服务存储敏感信息
5. 启用访问日志和审计日志
6. 配置 CORS 策略
7. 实施 API 限流

## 故障排查

### 后端启动失败

1. 检查 Java 版本：`java -version`
2. 检查端口占用：`lsof -i :8080`
3. 查看日志文件：`tail -f /var/log/aigen-studio/application.log`

### 前端构建失败

1. 清除缓存：`rm -rf node_modules package-lock.json && npm install`
2. 检查 Node.js 版本：`node -v`
3. 查看构建日志：`npm run build`

### 数据库连接失败

1. 检查数据库服务状态
2. 验证连接参数
3. 检查网络连通性
4. 确认数据库用户权限

## 备份和恢复

### 数据库备份

```bash
mysqldump -u aigen_user -p aigen_studio > backup_$(date +%Y%m%d).sql
```

### 数据库恢复

```bash
mysql -u aigen_user -p aigen_studio < backup_20240101.sql
```

## 性能优化

1. 启用 JVM 参数优化
2. 配置连接池
3. 启用缓存
4. 使用 CDN 加速静态资源
5. 启用 Gzip 压缩
6. 配置负载均衡