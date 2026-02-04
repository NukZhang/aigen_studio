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
export IFLOW_API_KEY=sk-53b6922f314b9738c8083aabb2f7ceda
export IFLOW_OUTPUT_DIR=/opt/aigen-studio/generated-code
export GITLAB_URL=https://git.longhu.net
export GITLAB_TOKEN=your-gitlab-token
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
Environment=IFLOW_OUTPUT_DIR=/opt/aigen-studio/generated-code
Environment=GITLAB_URL=https://git.longhu.net
Environment=GITLAB_TOKEN=your-gitlab-token

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

    # 前端路由支持
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 支持（如果有）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 预览服务代理（可选）
    location /__preview__/ {
        proxy_pass http://localhost:3001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
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
      - IFLOW_OUTPUT_DIR=/app/generated-code
      - GITLAB_URL=${GITLAB_URL}
      - GITLAB_TOKEN=${GITLAB_TOKEN}
    volumes:
      - generated-code:/app/generated-code
      - data:/app/data
    restart: unless-stopped

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  generated-code:
  data:
```

启动：
```bash
docker-compose up -d
```

## 数据库配置

### H2 数据库（开发环境）

默认使用文件数据库，数据存储在 `data/` 目录。

配置：
```yaml
spring:
  datasource:
    url: jdbc:h2:file:/path/to/data/aigendb;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: password
```

### MySQL 数据库（生产环境）

1. 创建数据库
```sql
CREATE DATABASE aigen_studio CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'aigen_user'@'localhost' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON aigen_studio.* TO 'aigen_user'@'localhost';
FLUSH PRIVILEGES;
```

2. 修改 application-prod.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aigen_studio?useSSL=false&serverTimezone=UTC
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

3. 添加 MySQL 依赖（pom.xml）
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

### PostgreSQL 数据库（生产环境）

1. 创建数据库
```sql
CREATE DATABASE aigen_studio ENCODING 'UTF8';
CREATE USER aigen_user WITH PASSWORD 'your-password';
GRANT ALL PRIVILEGES ON DATABASE aigen_studio TO aigen_user;
```

2. 修改 application-prod.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aigen_studio
    username: aigen_user
    password: your-password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

## 环境变量配置

### 必需变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `IFLOW_API_KEY` | iFlow SDK API 密钥 | `sk-53b6922f314b9738c8083aabb2f7ceda` |

### 可选变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring 配置文件 | `dev` |
| `SERVER_PORT` | 服务端口 | `8080` |
| `IFLOW_OUTPUT_DIR` | 代码生成输出目录 | `../../generated-code` |
| `IFLOW_SDK_TIMEOUT` | SDK 超时时间（毫秒） | `300000` |
| `GITLAB_URL` | GitLab 服务器地址 | - |
| `GITLAB_TOKEN` | GitLab 访问令牌 | - |
| `TUTORIAL_BASE_PATH` | 教程目录路径 | `./tutorials` |
| `LOG_LEVEL` | 日志级别 | `INFO` |

### 环境变量文件

创建 `.env` 文件：
```bash
# iFlow 配置
IFLOW_API_KEY=sk-53b6922f314b9738c8083aabb2f7ceda
IFLOW_OUTPUT_DIR=/opt/aigen-studio/generated-code
IFLOW_SDK_TIMEOUT=300000

# GitLab 配置
GITLAB_URL=https://git.longhu.net
GITLAB_TOKEN=your-gitlab-token

# Spring 配置
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# 日志配置
LOG_LEVEL=INFO
```

## 目录权限配置

确保应用有权限访问以下目录：

```bash
# 生成的代码目录
chmod -R 755 /opt/aigen-studio/generated-code

# 数据目录
chmod -R 755 /opt/aigen-studio/data

# 日志目录
chmod -R 755 /var/log/aigen-studio
```

## 监控和日志

### 日志配置

开发环境配置（application-dev.yml）：
```yaml
logging:
  level:
    root: INFO
    com.aigen.studio: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

生产环境配置（application-prod.yml）：
```yaml
logging:
  level:
    root: WARN
    com.aigen.studio: INFO
  file:
    name: /var/log/aigen-studio/application.log
    max-size: 100MB
    max-history: 30
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### 健康检查

后端提供健康检查端点：
```
GET /api/actuator/health
```

响应示例：
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

### 指标监控

启用 Spring Boot Actuator：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

访问指标：`http://localhost:8080/api/actuator/metrics`

## 安全建议

1. **使用 HTTPS 协议**：配置 SSL 证书
2. **配置防火墙规则**：只开放必要端口
3. **定期更新依赖包**：`npm audit fix` 和 `mvn versions:display-dependency-updates`
4. **使用密钥管理服务**：存储 API 密钥和敏感信息
5. **启用访问日志和审计日志**：记录所有操作
6. **配置 CORS 策略**：限制跨域访问
7. **实施 API 限流**：防止滥用
8. **输入验证**：所有用户输入都需要验证
9. **SQL 注入防护**：使用参数化查询
10. **XSS 防护**：对所有输出进行转义

### SSL/TLS 配置

生成自签名证书（仅用于开发）：
```bash
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650
```

配置 application-prod.yml：
```yaml
server:
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: your-password
    key-store-type: PKCS12
    key-alias: tomcat
  port: 8443
```

## 故障排查

### 后端启动失败

1. 检查 Java 版本：`java -version`
2. 检查端口占用：`lsof -i :8080`
3. 查看日志文件：`tail -f /var/log/aigen-studio/application.log`
4. 检查环境变量：`printenv | grep IFLOW`
5. 验证数据库连接

### 前端构建失败

1. 清除缓存：`rm -rf node_modules package-lock.json && npm install`
2. 检查 Node.js 版本：`node -v`
3. 查看构建日志：`npm run build`
4. 检查磁盘空间：`df -h`

### 预览服务启动失败

1. 查看预览日志：`tail -f data/preview-*.log`
2. 检查端口占用：`lsof -i :3001 -i :8081`
3. 验证代码生成路径：检查 `generated-code/conversation-{id}/` 是否存在
4. 检查 npm 和 Maven 是否已安装

### 数据库连接失败

1. 检查数据库服务状态：`systemctl status mysql`
2. 验证连接参数
3. 检查网络连通性：`telnet localhost 3306`
4. 确认数据库用户权限
5. 查看数据库日志

### iFlow SDK 连接失败

1. 验证 API 密钥：`echo $IFLOW_API_KEY`
2. 测试网络连接：`curl https://platform.iflow.cn`
3. 检查防火墙规则
4. 查看后端日志中的错误信息

## 备份和恢复

### 数据库备份

```bash
# MySQL 备份
mysqldump -u aigen_user -p aigen_studio > backup_$(date +%Y%m%d).sql

# PostgreSQL 备份
pg_dump -U aigen_user aigen_studio > backup_$(date +%Y%m%d).sql

# H2 备份
cp data/aigendb.mv.db backup/aigendb_$(date +%Y%m%d).mv.db
```

### 生成的代码备份

```bash
# 备份生成的代码
tar -czf generated-code-backup-$(date +%Y%m%d).tar.gz generated-code/
```

### 数据库恢复

```bash
# MySQL 恢复
mysql -u aigen_user -p aigen_studio < backup_20240101.sql

# PostgreSQL 恢复
psql -U aigen_user aigen_studio < backup_20240101.sql

# H2 恢复
cp backup/aigendb_20240101.mv.db data/aigendb.mv.db
```

## 性能优化

### 后端优化

1. **JVM 参数优化**
```bash
java -Xms512m -Xmx2g -XX:+UseG1GC -jar app.jar
```

2. **连接池配置**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

3. **启用缓存**
```yaml
spring:
  cache:
    type: redis
  redis:
    host: localhost
    port: 6379
```

### 前端优化

1. **启用 Gzip 压缩**（Nginx）
```nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
```

2. **使用 CDN 加速静态资源**
3. **代码分割和懒加载**
4. **启用 HTTP/2**

### 负载均衡

使用 Nginx 进行负载均衡：
```nginx
upstream aigen_backend {
    server localhost:8080;
    server localhost:8081;
    server localhost:8082;
}

server {
    location /api {
        proxy_pass http://aigen_backend;
    }
}
```

## 维护建议

1. **定期更新依赖**
   - 后端：每月检查一次 Maven 依赖更新
   - 前端：每月运行 `npm audit fix`

2. **日志轮转**
   - 配置 logrotate 自动轮转日志文件
   - 保留最近 30 天的日志

3. **监控告警**
   - 配置 Prometheus + Grafana 监控
   - 设置关键指标告警

4. **定期备份**
   - 数据库：每天自动备份
   - 生成的代码：每周备份

5. **容量规划**
   - 监控磁盘使用情况
   - 预估用户增长和资源需求