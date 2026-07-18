<div align="center">

# 🔧 工友通 GongyouTong

### AI 全链路蓝领工作助手

[![Android](https://img.shields.io/badge/Android-24%2B-brightgreen?style=for-the-badge&logo=android)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Room](https://img.shields.io/badge/Room-Database-3DDC84?style=for-the-badge)](https://developer.android.com/training/data-storage/room)
[![CameraX](https://img.shields.io/badge/CameraX-1.3-4285F4?style=for-the-badge)](https://developer.android.com/training/camerax)
[![BaiduMap](https://img.shields.io/badge/百度地图-7.6-0099FF?style=for-the-badge)](https://lbsyun.baidu.com/)

**覆盖维修工人从接单、排程、导航、诊断、维修、报价到知识沉淀的完整工作流**

[功能特性](#-功能特性) · [技术架构](#-技术架构) · [AI 能力矩阵](#-ai-能力矩阵) · [快速开始](#-快速开始) · [项目结构](#-项目结构)

</div>

---

## 📖 项目简介

工友通是一款面向 **家电维修、水电维修、空调安装维修** 等蓝领工人的 Android 原生应用。它不是一个简单的工具，而是将 **AI 深度嵌入每一个业务节点** 的智能工作平台：

> 📅 自然语言说一句"明天上午十点去XX小区安装热水器" → AI 自动解析为结构化日程
> 🔍 对着故障设备拍一张照片 → AI 自动识别型号、诊断故障、推荐工具
> 🛠️ 维修过程中 → 实时视频分析 + 语音引导，边做边教
> 📄 维修完成后 → AI 自动生成工单报告、报价单、维修教程视频

---

## ✨ 功能特性

<table>
<tr>
<td width="50%">

### 🏠 智能工作台
- **AI 语音助手**：说一句话，自动创建日程或回答问题
- **意图分类**：AI 自动判断是"创建日程"还是"知识问答"
- **SSE 流式对话**：实时逐字输出，体验流畅
- **工作看板**：待接单数、今日收入、今日日程一览

</td>
<td width="50%">

### 🎬 AI 视频维修
- **CameraX 实时画面**：实时捕获 + AR 叠加层
- **JoyAI-VL 实时分析**：模型决定何时开口指导
- **多状态维修流程**：设备识别 → 故障诊断 → 步骤指导 → 动作验证 → 完工检查
- **OCR 设备识别**：扫描铭牌自动识别型号

</td>
</tr>
<tr>
<td>

### 📋 工单管理
- **全生命周期**：待接单 → 已接单 → 已出发 → 已到达 → 维修中 → 验收中 → 已完成
- **AI 故障预测**：预估故障原因、难度、耗时
- **AI 工具推荐**：根据工种+故障推荐所需工具和配件

</td>
<td>

### 📚 智能知识库
- **双模式**：本地知识 + 联网获取
- **AI 自动整理**：语音/拍照/文档 → 自动生成标题、摘要、思维导图
- **每日推荐**：AI 生成维修知识文章（覆盖 20+ 题库）

</td>
</tr>
<tr>
<td>

### 👥 客户管理
- **客户 CRUD**：姓名、电话、地址、标签
- **智能标签**：高价值 / 普通 / 易爽约 / 企业客户
- **快速搜索**：按姓名或电话模糊搜索

</td>
<td>

### 💰 收入分析
- **月度统计**：月收入、完成工单数、客单价
- **趋势分析**：基于数据库实时查询

</td>
</tr>
<tr>
<td>

### 📅 AI 日程管理
- **智能提醒**：AI 生成个性化准备清单（工具、材料、安全事项）
- **风险分析**：自动识别高/中/低风险预警
- **状态同步**：日程状态变化自动同步关联工单

</td>
<td>

### 🗺️ 百度地图导航
- **一键导航**：从日程页直接启动百度地图导航
- **坐标定位**：经纬度精确定位

</td>
</tr>
</table>

---

## 🤖 AI 能力矩阵

本项目集成了 **9 大 AI 服务**，覆盖维修工作的全生命周期：

| 服务 | 模型 | 能力 | 应用场景 |
|:---:|:---:|:---:|:---|
| **VivoAiService** | 蓝心大模型 (DeepSeek-V3.2) | 意图分类 / 日程解析 / 流式对话 | 首页 AI 助手、日程创建 |
| **VisionAnalysisService** | DeepSeek-V3.2 (多模态) | 视频帧分析 / 目标检测 / 置信度评分 | AR 维修叠加层 |
| **RepairLlmService** | DeepSeek-V3.2 (SSE) | 维修步骤规划 / 意图识别 / 进度跟踪 | 视频维修语音引导 |
| **JoyAiVlService** | JoyAI-VL (本地部署) | 实时视频理解 / 主动语音指导 | 维修过程实时辅助 |
| **VideoGenerationService** | Doubao-Seedance-1.0-pro | 文本转视频 / 图片转视频 | 维修教程视频生成 |
| **QuotationAiService** | DeepSeek-V3.2 | AI 报价生成 / 配件定价 | 自动报价单 |
| **WorkOrderAiService** | DeepSeek-V3.2 | 故障预测 / 工具推荐 / 多模态诊断 | 工单详情页 |
| **OnlineKnowledgeService** | DeepSeek-V3.2 | 文章生成 / 摘要提取 / 思维导图 | 知识库内容 |
| **VivoOcrService** | vivo-ocr-general | 文字识别 | 设备铭牌扫描 |

### 🎯 AI 降级策略

```
云端 AI 不可用 → 本地正则解析（日期/时间/工种关键词匹配）
JoyAI-VL 不可用 → 本地 VisionAnalysisService（多模态分析）
离线模式 → Room 本地数据库持久化 + 缓存日程
```

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────┐
│                    表现层 (UI)                        │
│  Material Design 3 · ViewBinding · RecyclerView     │
├─────────────────────────────────────────────────────┤
│                   业务层 (ViewModel)                  │
│  LiveData · Handler · ExecutorService               │
├─────────────────────────────────────────────────────┤
│                    AI 服务层                          │
│  VivoAiService · VisionAnalysisService              │
│  RepairLlmService · JoyAiVlService                  │
│  VideoGenerationService · QuotationAiService        │
│  OnlineKnowledgeService · WorkOrderAiService        │
├─────────────────────────────────────────────────────┤
│                    数据层                             │
│  Room Database (SQLite) · SharedPreferences          │
│  WorkspaceRepository · DAOs                          │
├─────────────────────────────────────────────────────┤
│                   网络层                              │
│  OkHttp3 (HTTP + WebSocket) · SSE Streaming          │
├─────────────────────────────────────────────────────┤
│                   设备能力层                          │
│  CameraX · 百度地图 SDK · Glide · Gson               │
└─────────────────────────────────────────────────────┘
```

### 核心技术栈

| 类别 | 技术 | 版本 |
|:---:|:---:|:---:|
| 平台 | Android (Java) | minSdk 24 / targetSdk 35 |
| 构建 | Gradle (Kotlin DSL) | — |
| 数据库 | Room | — |
| 网络 | OkHttp3 | — |
| 图片加载 | Glide | 4.16.0 |
| 地图 | 百度地图 SDK | 7.6.0 |
| 相机 | CameraX | 1.3.4 |
| AI 后端 | vivo 蓝心大模型 | OpenAI 兼容协议 |
| 图像生成 | Doubao-Seedream-4.5 | — |
| 视频生成 | Doubao-Seedance-1.0-pro | — |
| 本地 VL | JoyAI-VL (Docker) | GPU 推荐 |

---

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1) 或更高版本
- JDK 17
- Android SDK 35

### 1. 克隆项目

```bash
git clone https://github.com/XOXO1018/AI-Project.git
```

### 2. 配置 AI 服务

在 `app/src/main/java/com/gongyoutong/app/ai/AiConfig.java` 中配置你的 API Key：

```java
// vivo 蓝心大模型
LLM_API_KEY = "your-vivo-api-key";

// Doubao 图像/视频生成
IMAGE_API_KEY = "your-doubao-api-key";
VIDEO_API_KEY = "your-doubao-api-key";
```

### 3. 配置百度地图

在 `app/src/main/java/com/gongyoutong/app/Config.java` 中替换百度地图 AK：

```java
BAIDU_MAP_AK = "your-baidu-map-ak";
```

同时在 `AndroidManifest.xml` 中更新：

```xml
<meta-data
    android:name="com.baidu.lbsapi.API_KEY"
    android:value="your-baidu-map-ak" />
```

### 4. 构建运行

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

### 5. (可选) 部署 JoyAI-VL 本地服务

JoyAI-VL 提供实时视频维修指导能力，需要 GPU 环境：

```bash
# 使用 Docker Compose 一键部署
cd docs/joyai-vl-docker
docker-compose up -d
```

详细部署指南请参考 [docs/joyai-vl-deploy.md](docs/joyai-vl-deploy.md)

---

## 📁 项目结构

```
app/src/main/java/com/gongyoutong/app/
├── ai/                          # AI 服务层
│   ├── AiConfig.java            # 统一配置管理
│   ├── AiApiClient.java         # AI API 客户端
│   ├── VivoAiService.java       # 核心 AI 服务（意图/对话/日程）
│   ├── VisionAnalysisService.java # 多模态视觉分析
│   ├── RepairLlmService.java    # 维修 LLM 服务
│   ├── JoyAiVlService.java      # JoyAI-VL 实时视频分析
│   ├── VideoGenerationService.java # 视频生成
│   ├── QuotationAiService.java  # AI 报价生成
│   ├── OnlineKnowledgeService.java # 在线知识服务
│   └── WorkOrderAiService.java  # 工单 AI 服务
├── data/                        # 数据模型
│   ├── Customer.java            # 客户实体
│   ├── CustomerAdapter.java     # 客户列表适配器
│   ├── Quotation.java           # 报价实体
│   └── WorkspaceRepository.java # 工作台数据仓库
├── database/                    # 数据库层
│   ├── AppDatabase.java         # Room 数据库
│   ├── WorkOrderDao.java        # 工单 DAO
│   ├── CustomerDao.java         # 客户 DAO
│   ├── ScheduleDao.java         # 日程 DAO
│   ├── QuotationDao.java        # 报价 DAO
│   └── MigrationV*.java         # 数据库迁移
├── repair/                      # 维修模块
│   ├── ArOverlayView.java       # AR 叠加层
│   ├── ErrorDetector.java       # 错误检测器
│   ├── ImageGenerationResult.java
│   └── VideoGenerationResult.java
├── ui/                          # 界面层
│   ├── main/MainActivity.java   # 主页（AI 助手 + 工作台）
│   ├── repair/VideoRepairActivity.java # AI 视频维修
│   ├── workorder/WorkOrderActivity.java # 工单管理
│   ├── knowledge/KnowledgeActivity.java # 知识库
│   ├── customer/CustomerActivity.java # 客户管理
│   ├── income/IncomeActivity.java # 收入分析
│   ├── detail/ScheduleDetailActivity.java # 日程详情
│   ├── navigation/NavigationActivity.java # 导航
│   ├── profile/ProfileActivity.java # 个人中心
│   └── settings/SettingsActivity.java # 设置
└── Config.java                  # 应用配置
```

---

## 🔄 业务闭环

```
        ┌──────────────────────────────────────────────────┐
        │                                                  │
        ▼                                                  │
   ┌─────────┐    ┌─────────┐    ┌─────────┐             │
   │  接 单   │───▶│  排 程   │───▶│  导 航   │             │
   └─────────┘    └─────────┘    └─────────┘             │
        │              │              │                    │
        │         AI 日程解析     百度地图                   │
        │              │              │                    │
        ▼              ▼              ▼                    │
   ┌─────────┐    ┌─────────┐    ┌─────────┐             │
   │  诊 断   │───▶│  维 修   │───▶│  报 告   │             │
   └─────────┘    └─────────┘    └─────────┘             │
        │              │              │                    │
   AI 故障预测   AI 实时指导    AI 生成工单                  │
        │         视频分析         报价单                    │
        │              │              │                    │
        ▼              ▼              ▼                    │
   ┌─────────┐    ┌─────────┐    ┌─────────┐             │
   │  报 价   │───▶│  收 款   │───▶│  知 识   │─────────────┘
   └─────────┘    └─────────┘    └─────────┘
        │              │              │
   AI 自动报价     月度分析     AI 整理沉淀
```

---

## 📄 License

本项目为私有项目，未经授权禁止复制、分发或用于商业用途。

---

<div align="center">

**Made with ❤️ for blue-collar workers**

工友通 — 让每一位维修师傅都有 AI 助手

</div>
