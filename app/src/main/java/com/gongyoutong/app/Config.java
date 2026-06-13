package com.gongyoutong.app;

/**
 * 应用配置类
 * 存放所有第三方服务的配置信息
 */
public class Config {

    // ==================== vivo 蓝心大模型 配置（OpenAI 兼容协议）====================
    // 访问地址：https://api-ai.vivo.com.cn/v1/chat/completions
    // 模型：Volc-DeepSeek-V3.2
    public static final String VIVO_APP_ID = "2026062497";
    public static final String VIVO_APP_KEY = "sk-xuanji-2026062497-YmpCQXZQeWVkSU9Kd2JaaQ==";
    public static final String VIVO_API_URL = "https://api-ai.vivo.com.cn/v1/chat/completions";
    public static final String VIVO_MODEL = "Volc-DeepSeek-V3.2";

    // ==================== vivo 蓝心大模型 实时短语音识别(ASR) 配置 ====================
    // WebSocket 协议，PCM 16kHz 16bit 单声道，最大 60 秒
    // 接口文档：实施短语音识别.docx
    public static final String VIVO_ASR_WS_URL = "ws://api-ai.vivo.com.cn/asr/v2";
    public static final String VIVO_ASR_ENGINE_ID = "shortasrinput";
    public static final String VIVO_ASR_USER_ID = "2addc42b7ae689dfdf1c63e220df52a2";

    // ==================== Ollama 本地 AI 模型配置（已弃用，保留备用）====================
    // public static final String OLLAMA_BASE_URL = "http://10.50.80.97:11434";
    // public static final String OLLAMA_MODEL = "qwen2:1.5b";
    // public static final String OLLAMA_API_URL = OLLAMA_BASE_URL + "/api/generate";

    // ==================== 百度地图 配置 ====================
    // 请在 https://lbsyun.baidu.com/ 注册应用获取 AK
    public static final String BAIDU_MAP_AK = "vzPA8jeqjVqTabbT8nLJap4Bw5Kg2imn";

    // ==================== 数据库配置 ====================
    public static final String DATABASE_NAME = "gongyoutong_db";
    public static final int DATABASE_VERSION = 7;

    // ==================== SharedPreferences 配置 ====================
    public static final String PREFS_NAME = "gongyoutong_prefs";

    // ==================== Intent Extra Keys ====================
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_WORKORDER_ID = "workorder_id";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";

    // ==================== AI 功能开关 ====================
    // true = 使用云端 AI, false = 仅使用本地规则解析
    public static final boolean USE_CLOUD_AI = true;

    // ==================== 维修诊断配置 ====================
    public static final int MAX_PHOTOS = 9;               // 最大照片数量
    public static final int PHOTO_MAX_SIZE_PX = 800;      // 照片长边最大像素
    public static final int PHOTO_QUALITY = 70;            // JPEG 压缩质量 (0-100)
    public static final int AI_DIAGNOSIS_TIMEOUT = 60;     // AI 诊断超时（秒）
    public static final String EXTRA_DIAGNOSIS_ID = "extra_diagnosis_id"; // 诊断记录ID Extra Key

    // ==================== vivo 蓝心大模型 图片生成 配置 ====================
    // 访问地址：https://api-ai.vivo.com.cn/api/v1/image_generation
    // 模型：Doubao-Seedream-4.5
    public static final String VIVO_IMAGE_GENERATION_URL = "https://api-ai.vivo.com.cn/api/v1/image_generation";
    public static final String VIVO_IMAGE_MODEL = "Doubao-Seedream-4.5";
    public static final String VIVO_IMAGE_MODULE = "aigc";
    
    // 图片生成参数
    public static final String DEFAULT_IMAGE_SIZE = "1024x1024";  // 默认图片尺寸
    public static final int IMAGE_GENERATION_TIMEOUT = 120;       // 图片生成超时时间（秒）
    public static final int MAX_IMAGE_RETRY = 3;                  // 最大重试次数

    // ========== vivo TTS 超拟人音色 (WebSocket 协议) ==========
    // 接口文档：音频生成.docx
    public static final String VIVO_TTS_WS_URL = "wss://api-ai.vivo.com.cn/tts";
    public static final String VIVO_TTS_ENGINE_ID = "tts_humanoid_lam";  // 超拟人音色引擎
    public static final String VIVO_TTS_VOICE = "M24";  // 俊朗男声（可选：F245_natural知性柔美, M193理性男声）
    public static final int VIVO_TTS_SAMPLE_RATE = 24000;  // TTS 输出采样率
    public static final int VIVO_TTS_TIMEOUT = 15;
    public static final String VIVO_TTS_USER_ID = "user_gyt_001";

    // ========== vivo 通用 OCR ==========
    public static final String VIVO_OCR_URL = "https://api-ai.vivo.com.cn/api/v1/ocr";
    public static final String VIVO_OCR_MODEL = "vivo-ocr-general";
    public static final long OCR_TIMEOUT = 10;

    // ========== vivo 视觉分析（多模态，复用 LLM 接口） ==========
    // vision 通过 VIVO_API_URL 的 chat/completions 接口，使用 image_url 传帧
    // 使用支持多模态的模型进行视觉理解
    public static final String VIVO_VISION_MODEL = "Volc-DeepSeek-V3.2";
    public static final long VISION_TIMEOUT = 20;
    // vision 复用现有 VIVO_LLM_URL (https://api-ai.vivo.com.cn/api/v1/chat/completions)，通过 image_url 传帧

    // ========== CameraX 配置 ==========
    public static final int CAMERA_FRAME_INTERVAL_MS = 1000;       // 帧捕获间隔 1秒
    public static final int CAMERA_FRAME_MAX_WIDTH = 640;          // 帧缩放最大宽度
    public static final int CAMERA_FRAME_QUALITY = 70;             // JPEG 压缩质量

    // ========== 维修 LLM 超时 ==========
    public static final int REPAIR_LLM_STREAM_TIMEOUT = 120;       // SSE 流式长连接
    public static final int REPAIR_LLM_CHAT_TIMEOUT = 60;          // 非流式对话
}
