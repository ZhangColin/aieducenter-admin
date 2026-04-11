# aieducenter-admin

管理后台网关后端服务。

## 包含的限界上下文

- **admin** - 管理员、角色、权限、菜单管理

## 技术栈

- Java 21 / Spring Boot 3.4.x / Maven
- cartisan-boot 框架
- PostgreSQL / Redis

## 开发

```bash
# 编译
mvn compile

# 测试
mvn test

# 运行
mvn spring-boot:run
```

## 部署

```bash
# 构建
mvn package -DskipTests

# Docker 部署
docker-compose -f docker-compose.prod.yml --env-file .env.production up -d
```
