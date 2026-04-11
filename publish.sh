#!/bin/bash
# 管理后台网关 - 本地发布脚本（构建 + 上传到服务器 + 触发部署）

set -e

# ═══════════════════════════════════════════════════════════════
# 服务器配置
# ═══════════════════════════════════════════════════════════════
SERVER_USER="root"
SERVER_HOST="43.140.211.9"
SERVER_PATH="/opt/hcy/aieducenter_admin"
SERVER_PASSWORD="Hcy@20260327"
# ═══════════════════════════════════════════════════════════════

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   管理后台网关 - 发布${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 本地构建
echo -e "${YELLOW}正在本地构建...${NC}"
mvn package -DskipTests -q
echo -e "${GREEN}✓ 构建完成${NC}"

# 检查 sshpass
if ! command -v sshpass &> /dev/null; then
  echo -e "${RED}未安装 sshpass，正在安装...${NC}"
  brew install hudochenkov/sshpass/sshpass 2>/dev/null || {
    echo -e "${RED}安装失败，请手动安装: brew install hudochenkov/sshpass/sshpass${NC}"
    exit 1
  }
fi

SSH_CMD="sshpass -p $SERVER_PASSWORD ssh -o StrictHostKeyChecking=no"
RSYNC_CMD="sshpass -p $SERVER_PASSWORD rsync"

echo -e "${BLUE}服务器: ${SERVER_USER}@${SERVER_HOST}${NC}"
echo -e "${BLUE}部署路径: ${SERVER_PATH}${NC}"
echo ""

# 准备部署文件（只打包必要文件）
JAR_NAME="aieducenter-admin-server-1.0.0-SNAPSHOT.jar"
JAR_FILE="target/$JAR_NAME"
DEPLOY_FILES=(Dockerfile docker-compose.prod.yml deploy.sh .env.production)

# 验证
echo -e "${YELLOW}验证文件...${NC}"
if [ ! -f "$JAR_FILE" ]; then
  echo -e "${RED}✗ JAR 不存在: ${JAR_FILE}${NC}"
  exit 1
fi
JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
echo -e "  ✓ ${JAR_NAME} ($JAR_SIZE)"

for file in "${DEPLOY_FILES[@]}"; do
  if [ ! -f "$file" ]; then
    echo -e "${RED}✗ ${file} 缺失！${NC}"
    exit 1
  fi
  echo -e "  ✓ $file"
done
echo ""

# 创建临时目录，只放必要文件
TEMP_DIR=$(mktemp -d)
mkdir -p "$TEMP_DIR/target"
cp "$JAR_FILE" "$TEMP_DIR/target/"
cp "${DEPLOY_FILES[@]}" "$TEMP_DIR/"

# 上传
echo -e "${YELLOW}正在上传到服务器...${NC}"
$SSH_CMD ${SERVER_USER}@${SERVER_HOST} "mkdir -p ${SERVER_PATH}/target"
$RSYNC_CMD -avz -e "sshpass -p $SERVER_PASSWORD ssh -o StrictHostKeyChecking=no" \
  "$TEMP_DIR/" ${SERVER_USER}@${SERVER_HOST}:${SERVER_PATH}/

rm -rf "$TEMP_DIR"
echo -e "${GREEN}✓ 上传完成${NC}"

# 远程部署
echo ""
echo -e "${YELLOW}────────────────────────────────────${NC}"
echo -e "${YELLOW}正在服务器上执行部署...${NC}"
echo ""

$SSH_CMD ${SERVER_USER}@${SERVER_HOST} "cd ${SERVER_PATH} && bash deploy.sh"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   ✓ 发布完成！${NC}"
echo -e "${GREEN}========================================${NC}"
