#!/bin/bash
# ============================================================
# JoyAI-VL-Interaction 本地部署脚本
# ============================================================
#
# 环境要求：
#   - Linux (Ubuntu 22.04+) 或 WSL2
#   - NVIDIA GPU (建议 >= 12GB VRAM，8GB 需量化)
#   - CUDA 12.x + NVIDIA Driver 535+
#   - Python 3.12
#
# 使用方式：
#   chmod +x joyai-vl-deploy.sh
#   ./joyai-vl-deploy.sh
#
# 部署完成后，Android 应用连接地址：
#   http://<本机IP>:8070/v1/chat/completions
# ============================================================

set -e

REPO_URL="https://github.com/jd-opensource/JoyAI-VL-Interaction.git"
INSTALL_DIR="$HOME/JoyAI-VL-Interaction"
MODEL_DIR="/tmp/models"

echo "=========================================="
echo " JoyAI-VL-Interaction 部署脚本"
echo "=========================================="

# ==================== 1. 环境检查 ====================
echo ""
echo "[1/7] 检查环境..."

# 检查 Python
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到 python3，请先安装 Python 3.12"
    echo "  Ubuntu: sudo apt install python3.12 python3.12-venv"
    exit 1
fi

PYTHON_VERSION=$(python3 --version 2>&1 | awk '{print $2}')
echo "  Python: $PYTHON_VERSION"

# 检查 CUDA
if ! command -v nvidia-smi &> /dev/null; then
    echo "错误: 未找到 nvidia-smi，请先安装 NVIDIA 驱动"
    echo "  Ubuntu: sudo apt install nvidia-driver-535"
    exit 1
fi

echo "  GPU: $(nvidia-smi --query-gpu=name --format=csv,noheader)"
echo "  VRAM: $(nvidia-smi --query-gpu=memory.total --format=csv,noheader)"
echo "  CUDA: $(nvidia-smi --query-gpu=driver_version --format=csv,noheader)"

# 检查 Git
if ! command -v git &> /dev/null; then
    echo "错误: 未找到 git，请先安装"
    echo "  Ubuntu: sudo apt install git"
    exit 1
fi

# ==================== 2. 克隆仓库 ====================
echo ""
echo "[2/7] 克隆 JoyAI-VL-Interaction 仓库..."

if [ -d "$INSTALL_DIR" ]; then
    echo "  目录已存在，跳过克隆"
    cd "$INSTALL_DIR"
    git pull
else
    git clone "$REPO_URL" "$INSTALL_DIR"
    cd "$INSTALL_DIR"
fi

# ==================== 3. 安装依赖 ====================
echo ""
echo "[3/7] 安装依赖..."

chmod +x install/*.sh
./install/install.sh --with-all

# ==================== 4. 下载模型 ====================
echo ""
echo "[4/7] 下载模型（这可能需要较长时间）..."

./install/download-models.sh --all

# ==================== 5. 检查显存 ====================
echo ""
echo "[5/7] 检查显存是否足够..."

VRAM_MB=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits | head -1)
VRAM_MB=$(echo $VRAM_MB | tr -d ' ')

echo "  可用显存: ${VRAM_MB}MB"

if [ "$VRAM_MB" -lt 12000 ]; then
    echo ""
    echo "  ⚠️  警告: 显存不足 12GB，8B 模型可能无法正常运行"
    echo "  建议方案:"
    echo "    1. 使用量化版本（如有）"
    echo "    2. 租用云 GPU 服务器（AutoDL / 阿里云 / 腾讯云）"
    echo "    3. 减少 GPU 内存占用（关闭其他 GPU 程序）"
    echo ""
    read -p "  是否继续部署？(y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# ==================== 6. 配置服务地址 ====================
echo ""
echo "[6/7] 配置服务..."

# 获取本机 IP
LOCAL_IP=$(hostname -I | awk '{print $1}')
echo "  本机局域网 IP: $LOCAL_IP"

# 写入配置文件
cat > "$INSTALL_DIR/.env" << EOF
# JoyAI-VL-Interaction 配置
ADAPTER_PORT=8070
MAIN_GPU=0
SUMMARY_GPU=0
MODEL_PATH=$MODEL_DIR/JoyAI-VL-Interaction-Preview
SUMMARY_MODEL_PATH=$MODEL_DIR/Qwen3-VL-4B-Instruct
EOF

echo "  配置已写入: $INSTALL_DIR/.env"

# ==================== 7. 启动服务 ====================
echo ""
echo "[7/7] 启动服务..."

echo "  启动最小服务集（推理 + WebUI）..."
echo "  服务启动后，访问 https://127.0.0.1:8099 查看 WebUI"
echo ""
echo "  Android 应用连接地址: http://$LOCAL_IP:8070"
echo ""

# 启动服务
./services/scripts/run.sh minimal

echo ""
echo "=========================================="
echo " 部署完成！"
echo "=========================================="
echo ""
echo "  WebUI: https://127.0.0.1:8099"
echo "  API:   http://$LOCAL_IP:8070/v1/chat/completions"
echo "  健康检查: http://$LOCAL_IP:8070/health"
echo ""
echo "  在 Android 应用中，将 JoyAiVlConfig.java 的"
echo "  BASE_URL 修改为: http://$LOCAL_IP:8070"
echo ""
echo "  停止服务: ./services/scripts/stop.sh all"
echo "=========================================="
