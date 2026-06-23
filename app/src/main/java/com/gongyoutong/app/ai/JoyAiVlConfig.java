package com.gongyoutong.app.ai;

/**
 * JoyAI-VL-Interaction 本地部署配置
 *
 * 部署方式：在本地 Linux + NVIDIA GPU 电脑上运行 JoyAI-VL-Interaction 服务，
 * Android 应用通过局域网 HTTP API 与之通信。
 *
 * 服务端口：
 *   8070 - webinfer 适配器（OpenAI 兼容 API）
 *   8099 - WebUI（浏览器界面）
 *
 * 启动命令：
 *   git clone https://github.com/jd-opensource/JoyAI-VL-Interaction.git
 *   cd JoyAI-VL-Interaction
 *   ./install/install.sh --with-all
 *   ./install/download-models.sh --all
 *   ./services/scripts/run.sh minimal
 */
public final class JoyAiVlConfig {

    // ==================== 服务地址 ====================
    // 默认本地部署地址，实际使用时替换为服务器 IP
    // 例如：http://192.168.1.100:8070
    public static final String BASE_URL = "http://192.168.1.100:8070";

    // ==================== API 端点 ====================
    public static final String CHAT_COMPLETIONS_URL = BASE_URL + "/v1/chat/completions";
    public static final String HEALTH_URL = BASE_URL + "/health";
    public static final String MODELS_URL = BASE_URL + "/v1/models";
    public static final String RESET_URL = BASE_URL + "/v1/streaming/reset";

    // ==================== 模型名称 ====================
    public static final String MODEL_NAME = "JoyAI-VL-Interaction-Preview";

    // ==================== 请求参数 ====================
    public static final double TEMPERATURE = 0.8;
    public static final int MAX_TOKENS = 256;

    // ==================== 维修场景专用 System Prompt ====================
    public static final String REPAIR_SYSTEM_PROMPT =
            "你是一个专业的设备维修指导AI助手。你通过实时视频流观察维修现场，" +
            "在关键时刻主动给出精准的维修指导。你的指导应当：\n" +
            "1. 简洁明了，一句话说清当前要做什么\n" +
            "2. 注意安全提醒（如断电、戴手套等）\n" +
            "3. 指出操作中的错误或遗漏\n" +
            "4. 仅在有重要信息时才开口，不要废话";

    // ==================== 超时设置 ====================
    public static final int CONNECT_TIMEOUT_SECONDS = 5;
    public static final int READ_TIMEOUT_SECONDS = 30;
    public static final int WRITE_TIMEOUT_SECONDS = 10;

    // ==================== 帧发送配置 ====================
    // 发送到 JoyAI-VL 的帧间隔（毫秒），比本地分析更频繁以利用其流式能力
    public static final int FRAME_SEND_INTERVAL_MS = 500;

    // 帧质量（JPEG 压缩质量，0-100）
    public static final int FRAME_QUALITY = 50;

    // 帧最大宽度（降低分辨率以减少网络传输）
    public static final int FRAME_MAX_WIDTH = 480;

    // ==================== 功能开关 ====================
    public static final boolean ENABLED = true;

    // 私有构造，禁止实例化
    private JoyAiVlConfig() {}
}
