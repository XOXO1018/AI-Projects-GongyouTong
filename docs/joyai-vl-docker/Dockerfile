FROM nvidia/cuda:12.4.0-devel-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

# 系统依赖
RUN apt-get update && apt-get install -y \
    python3.12 python3.12-venv python3-pip \
    git wget curl \
    libgl1-mesa-glx libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 设置 Python 3.12 为默认
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.12 1
RUN update-alternatives --install /usr/bin/python python /usr/bin/python3.12 1

WORKDIR /app

# 克隆仓库
RUN git clone --depth 1 https://github.com/jd-opensource/JoyAI-VL-Interaction.git .

# 安装 Python 依赖
RUN pip3 install --no-cache-dir uv && \
    uv venv /app/services/.venv && \
    source /app/services/.venv/bin/activate && \
    uv pip install -r services/webinfer/requirements.txt 2>/dev/null || \
    pip3 install -r services/webinfer/requirements.txt 2>/dev/null || true

# 下载模型（可选，也可以挂载）
# RUN ./install/download-models.sh --all

# 端口
EXPOSE 8070 8099

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8070/health || exit 1

# 启动命令
CMD ["./services/scripts/run.sh", "minimal"]
