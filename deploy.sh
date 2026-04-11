#!/bin/bash
# 管理后台网关 - 生产环境部署脚本
# 使用方法: ./deploy.sh

set -e

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

COMPOSE="docker-compose -f docker-compose.prod.yml --env-file .env.production"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   管理后台网关 - 生产环境部署${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查环境配置
if [ ! -f .env.production ]; then
  echo -e "${RED}错误: 未找到 .env.production${NC}"
  exit 1
fi
echo -e "${BLUE}✓ 读取 .env.production${NC}"

# 检查 JAR 文件
JAR_FILE="target/aieducenter-admin-server-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo -e "${RED}错误: 未找到 JAR 文件: ${JAR_FILE}${NC}"
  exit 1
fi
JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
echo -e "${GREEN}✓ JAR: $JAR_FILE ($JAR_SIZE)${NC}"
echo ""

# 停止旧容器
echo -e "${YELLOW}停止旧容器...${NC}"
$COMPOSE down 2>/dev/null || true

# 构建镜像
echo -e "${YELLOW}构建镜像...${NC}"
$COMPOSE build

# 启动新容器
echo -e "${YELLOW}启动服务...${NC}"
$COMPOSE up -d

# 等待容器启动
echo -e "${YELLOW}等待服务启动...${NC}"
sleep 15

# 通过 docker 检查容器健康状态
if docker inspect --format='{{.State.Health.Status}}' aieducenter-admin 2>/dev/null | grep -q healthy; then
  echo ""
  echo -e "${GREEN}========================================${NC}"
  echo -e "${GREEN}   ✓ 服务已启动 (healthy)${NC}"
  echo -e "${GREEN}========================================${NC}"
  echo ""
  echo "常用命令："
  echo "  查看日志: docker logs -f aieducenter-admin"
  echo "  停止服务: $COMPOSE down"
  echo "  重启服务: $COMPOSE restart"
  echo ""
else
  # 容器可能还在启动中，再等一会
  echo -e "${YELLOW}⚠ 等待健康检查...${NC}"
  sleep 20
  if docker inspect --format='{{.State.Health.Status}}' aieducenter-admin 2>/dev/null | grep -q healthy; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}   ✓ 服务已启动 (healthy)${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "常用命令："
    echo "  查看日志: docker logs -f aieducenter-admin"
    echo "  停止服务: $COMPOSE down"
    echo "  重启服务: $COMPOSE restart"
    echo ""
  else
    echo -e "${RED}✗ 服务未就绪${NC}"
    echo -e "${YELLOW}  状态: $(docker inspect --format='{{.State.Health.Status}}' aieducenter-admin 2>/dev/null || echo '未知')${NC}"
    echo -e "${YELLOW}  查看日志: docker logs aieducenter-admin${NC}"
    exit 1
  fi
fi
