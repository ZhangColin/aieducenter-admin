# 管理后台网关 - 生产环境 Dockerfile
# 前提：本地已执行 mvn package -DskipTests 构建好 JAR

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 时区设置
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 复制构建好的 JAR
COPY target/aieducenter-admin-server-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "--enable-preview", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
