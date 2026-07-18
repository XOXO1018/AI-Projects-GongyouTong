# JoyAI-VL-Interaction 部署指南

## 方案一：直接部署（推荐）

### 环境要求
- Linux (Ubuntu 22.04+) 或 WSL2
- NVIDIA GPU (建议 >= 12GB VRAM)
- CUDA 12.x + NVIDIA Driver 535+
- Python 3.12

### 部署步骤

```bash
# 1. 克隆仓库
git clone https://github.com/jd-opensource/JoyAI-VL-Interaction.git
cd JoyAI-VL-Interaction

# 2. 安装依赖
chmod +x install/*.sh
./install/install.sh --with-all

# 3. 下载模型
./install/download-models.sh --all

# 4. 启动服务
./services/scripts/run.sh minimal
```

### 服务地址
- WebUI: `https://127.0.0.1:8099`
- API: `http://<本机IP>:8070/v1/chat/completions`
- 健康检查: `http://<本机IP>:8070/health`

## 方案二：Docker 部署

### 前提
- 安装 Docker Desktop
- 安装 NVIDIA Container Toolkit

### 部署步骤

```bash
# 进入 Docker 部署目录
cd docs/joyai-vl-docker

# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

## 方案三：云 GPU 服务器

推荐平台：
- **AutoDL**: https://www.autodl.com/ （国内便宜）
- **阿里云 GPU**: https://www.aliyun.com/product/gpu
- **腾讯云 GPU**: https://cloud.tencent.com/product/gpu

### AutoDL 快速部署

```bash
# 1. 创建实例（选择 RTX 4090 或更好的 GPU）
# 2. SSH 连接后执行：

git clone https://github.com/jd-opensource/JoyAI-VL-Interaction.git
cd JoyAI-VL-Interaction
./install/install.sh --with-all
./install/download-models.sh --all
./services/scripts/run.sh minimal
```

## Android 应用配置

部署完成后，修改 `JoyAiVlConfig.java`:

```java
// 将 BASE_URL 修改为你的服务地址
public static final String BASE_URL = "http://<服务器IP>:8070";
```

## 显存需求

| 模型 | 精度 | 显存需求 |
|------|------|----------|
| JoyAI-VL-8B | FP16 | ~16GB |
| JoyAI-VL-8B | INT8 | ~10GB |
| JoyAI-VL-8B | INT4 | ~6GB |

如果你的 GPU 显存不足，可以：
1. 使用量化版本
2. 租用更大的 GPU
3. 使用 CPU 推理（非常慢，不推荐）
