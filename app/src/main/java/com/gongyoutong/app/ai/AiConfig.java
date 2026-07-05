package com.gongyoutong.app.ai;

/**
 * ============================================================
 * 大模型能力 — 统一配置管理
 * ============================================================
 *
 * 本文件集中管理项目中所有 vivo 蓝心大模型能力的配置参数，
 * 包括 API 密钥、端点地址、模型名称、超时设置、重试策略等。
 *
 * 配置分类索引：
 * ┌──────────────────────────────────────────────────────────────┐
 * │ 分类           │ 接口能力        │ 模型                      │
 * ├──────────────────────────────────────────────────────────────┤
 * │ 聊天补全       │ chat completions │ Volc-DeepSeek-V3.2       │
 * │ 流式对话       │ SSE streaming   │ Volc-DeepSeek-V3.2       │
 * │ 多模态诊断     │ multimodal       │ Volc-DeepSeek-V3.2       │
 * │ 视觉分析       │ vision           │ Volc-DeepSeek-V3.2       │
 * │ 图片生成       │ image generation │ Doubao-Seedream-4.5      │
 * │ OCR            │ ocr              │ vivo-ocr-general         │
 * │ ASR            │ websocket asr    │ shortasrinput            │
 * │ TTS            │ websocket tts    │ tts_humanoid_lam         │
 * └──────────────────────────────────────────────────────────────┘
 *
 * 使用方式：
 *   在 AI 服务类中通过 AiConfig.XXX 引用，不再依赖外层 Config 类。
 *   例如：AiConfig.VIVO_API_URL、AiConfig.VIVO_MODEL
 */
public final class AiConfig {

    // ==================== 通用认证 ====================
    // 所有 vivo 蓝心大模型接口使用相同的 AppId 和 AppKey
    public static final String VIVO_APP_ID = "2026062497";
    public static final String VIVO_APP_KEY = "sk-xuanji-2026062497-YmpCQXZQeWVkSU9Kd2JaaQ==";

    // ==================== #01-#04 聊天补全 / 多模态共用的基础 URL ====================
    // 协议：OpenAI 兼容格式
    // 访问地址：https://api-ai.vivo.com.cn/v1/chat/completions
    public static final String VIVO_API_URL = "https://api-ai.vivo.com.cn/v1/chat/completions";
    public static final String VIVO_MODEL = "Volc-DeepSeek-V3.2";

    // ==================== #01 非流式聊天补全 ====================
    // 默认超时 120 秒，温度 0.3，最大输出 1000 tokens
    // 使用者：VivoAiService, QuotationAiService, OnlineKnowledgeService

    // ==================== #02 流式聊天补全（SSE） ====================
    // 流式 SSE 协议，与 #01 共用 URL 和模型
    // 使用者：RepairLlmService.chatStream(), VivoAiService.chatWithAiStream()

    // ==================== #03 多模态诊断（文字+图片） ====================
    // 与 #01 共用 URL 和模型
    // 使用者：WorkOrderAiService.diagnoseFault(), VisionAnalysisService.analyze()

    // ==================== #04 视觉分析（多模态，复用 LLM 接口） ====================
    // vision 通过 VIVO_API_URL 的 chat/completions 接口，使用 image_url 传帧
    // 使用支持多模态的模型进行视觉理解
    public static final String VIVO_VISION_MODEL = "Volc-DeepSeek-V3.2";
    public static final long VISION_TIMEOUT = 20;

    // ==================== #05 图片生成 ====================
    // 协议：自定义 REST（非 OpenAI 兼容）
    // 访问地址：https://api-ai.vivo.com.cn/api/v1/image_generation
    // 模型：Doubao-Seedream-4.5
    public static final String VIVO_IMAGE_GENERATION_URL = "https://api-ai.vivo.com.cn/api/v1/image_generation";
    public static final String VIVO_IMAGE_MODEL = "Doubao-Seedream-4.5";
    public static final String VIVO_IMAGE_MODULE = "aigc";
    public static final String DEFAULT_IMAGE_SIZE = "2048x2048";
    public static final int IMAGE_GENERATION_TIMEOUT = 120;  // 秒
    public static final int MAX_IMAGE_RETRY = 3;

    // ==================== #05b 视频生成 ====================
    // 协议：自定义 REST（非 OpenAI 兼容）
    // 提交任务：POST https://api-ai.vivo.com.cn/api/v1/submit_task
    // 查询任务：GET  https://api-ai.vivo.com.cn/api/v1/query_task
    // 模型：Doubao-Seedance-1.0-pro
    // 限制：每分钟 5 个视频，总计 50 个视频
    public static final String VIVO_VIDEO_SUBMIT_URL = "https://api-ai.vivo.com.cn/api/v1/submit_task";
    public static final String VIVO_VIDEO_QUERY_URL = "https://api-ai.vivo.com.cn/api/v1/query_task";
    public static final String VIVO_VIDEO_MODEL = "Doubao-Seedance-1.0-pro";
    public static final int VIDEO_SUBMIT_TIMEOUT = 30;   // 秒
    public static final int VIDEO_QUERY_TIMEOUT = 10;    // 秒
    public static final int VIDEO_POLL_INTERVAL_MS = 5000;  // 轮询间隔 5 秒
    public static final int VIDEO_MAX_POLL_ATTEMPTS = 60;   // 最大轮询次数（5分钟）

    // ==================== #06 OCR 文字识别 ====================
    // 协议：自定义 REST（非 OpenAI 兼容）
    // 访问地址：https://api-ai.vivo.com.cn/ocr/general_recognition
    // 访问方式：POST (application/x-www-form-urlencoded)
    public static final String VIVO_OCR_URL = "https://api-ai.vivo.com.cn/ocr/general_recognition";
    public static final long OCR_TIMEOUT = 15;  // 增加到15秒，支持大图片
    public static final int OCR_MAX_RETRY = 2;
    // businessid: 支持旋转/非正向文字
    public static final String OCR_BUSINESS_ID_FULL = "aigc" + VIVO_APP_ID;
    // businessid: 仅正向文字但更快
    public static final String OCR_BUSINESS_ID_FAST = "8bf312e702043779ad0f2760b37a0806";

    // ==================== #07 实时短语音识别 (ASR) — WebSocket 协议 ====================
    // 连接地址：wss://api-ai.vivo.com.cn/asr/v2
    // 协议：WebSocket v2
    // 音频参数：PCM 16kHz 16bit 单声道
    public static final String VIVO_ASR_WS_URL = "wss://api-ai.vivo.com.cn/asr/v2";
    public static final String VIVO_ASR_ENGINE_ID = "shortasrinput";
    public static final String VIVO_ASR_USER_ID = "2addc42b7ae689dfdf1c63e220df52a2";

    // ==================== #08 语音合成 (TTS) — WebSocket 协议 ====================
    // 连接地址：wss://api-ai.vivo.com.cn/tts
    // 协议：WebSocket
    // 输出格式：PCM 24kHz 16bit 单声道（需转 WAV 播放）
    public static final String VIVO_TTS_WS_URL = "wss://api-ai.vivo.com.cn/tts";
    public static final String VIVO_TTS_ENGINE_ID = "tts_humanoid_lam";
    public static final String VIVO_TTS_VOICE = "M24";
    public static final int VIVO_TTS_SAMPLE_RATE = 24000;
    public static final int VIVO_TTS_TIMEOUT = 15;
    public static final String VIVO_TTS_USER_ID = "user_gyt_001";

    // ==================== AI 功能开关 ====================
    public static final boolean USE_CLOUD_AI = true;

    // ==================== 超时设置汇总 ====================
    // 聊天补全（非流式）超时
    public static final int CHAT_COMPLETION_TIMEOUT = 120;
    // AI 诊断超时（秒）
    public static final int AI_DIAGNOSIS_TIMEOUT = 60;
    // 维修 LLM 流式长连接超时（秒）
    public static final int REPAIR_LLM_STREAM_TIMEOUT = 120;
    // 维修 LLM 非流式对话超时（秒）
    public static final int REPAIR_LLM_CHAT_TIMEOUT = 60;
    // 网络连接超时（秒）
    public static final int CONNECT_TIMEOUT = 10;
    public static final int WRITE_TIMEOUT = 30;

    // ==================== 对话记忆配置 ====================
    public static final int MAX_CHAT_HISTORY = 20;

    // ==================== Ollama 本地 AI 模型配置（已弃用，保留备用）====================
    // public static final String OLLAMA_BASE_URL = "http://10.50.80.97:11434";
    // public static final String OLLAMA_MODEL = "qwen2:1.5b";
    // public static final String OLLAMA_API_URL = OLLAMA_BASE_URL + "/api/generate";

    // ==================== 工具方法 ====================

    /**
     * 构建 Authorization 头（Bearer Token）
     *
     * @return "Bearer sk-xxxxx" 格式的认证头值
     */
    public static String authHeader() {
        return "Bearer " + VIVO_APP_KEY;
    }

    // 私有构造，禁止实例化
    private AiConfig() {}
}
