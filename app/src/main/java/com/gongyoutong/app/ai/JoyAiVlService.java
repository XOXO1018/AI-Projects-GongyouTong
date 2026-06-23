package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * JoyAI-VL-Interaction 实时视频分析服务
 *
 * 通过 HTTP API 与本地部署的 JoyAI-VL-Interaction 服务通信，
 * 将摄像头帧实时发送到模型进行分析，获取主动式维修指导。
 *
 * 核心能力：
 * - 实时视频帧分析（OpenAI 兼容格式）
 * - 会话状态管理（通过 session ID）
 * - 主动式指导（模型判断何时开口）
 * - 长期记忆（跨帧上下文保持）
 *
 * 使用方式：
 * 1. 确保 JoyAI-VL-Interaction 服务已启动（默认 http://192.168.1.100:8070）
 * 2. 调用 startSession() 开始新会话
 * 3. 调用 sendFrame() 发送视频帧
 * 4. 调用 sendQuery() 发送用户问题
 * 5. 调用 endSession() 结束会话
 */
public class JoyAiVlService {

    private static final String TAG = "JoyAiVlService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile JoyAiVlService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // 会话状态
    private String currentSessionId;
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private String userQuery = "";

    private JoyAiVlService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(JoyAiVlConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(JoyAiVlConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(JoyAiVlConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        executor = Executors.newFixedThreadPool(3);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全 - DCL）
     */
    public static JoyAiVlService getInstance() {
        if (sInstance == null) {
            synchronized (JoyAiVlService.class) {
                if (sInstance == null) {
                    sInstance = new JoyAiVlService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    /** JoyAI-VL 分析回调 */
    public interface JoyAiCallback {
        /** 收到模型主动指导（模型认为需要开口时） */
        void onProactiveGuidance(String guidance);
        /** 收到查询响应 */
        void onQueryResponse(String response);
        /** 模型保持静默（当前帧无需指导） */
        void onSilent();
        /** 错误回调 */
        void onError(String msg);
    }

    /** 健康检查回调 */
    public interface HealthCallback {
        void onHealthy();
        void onUnhealthy(String msg);
    }

    // ==================== 会话管理 ====================

    /**
     * 开始新的 JoyAI-VL 会话
     * 生成唯一的 session ID，后续所有请求都携带此 ID
     */
    public void startSession() {
        currentSessionId = "repair-" + UUID.randomUUID().toString().substring(0, 8);
        isActive.set(true);
        userQuery = "";
        Log.d(TAG, "会话已启动: " + currentSessionId);

        // 重置服务端状态
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                Request request = new Request.Builder()
                        .url(JoyAiVlConfig.RESET_URL)
                        .post(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("x-streaming-session", currentSessionId)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    Log.d(TAG, "会话重置: " + response.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "会话重置失败（可忽略）: " + e.getMessage());
            }
        });
    }

    /**
     * 结束当前会话
     */
    public void endSession() {
        isActive.set(false);
        currentSessionId = null;
        userQuery = "";
        Log.d(TAG, "会话已结束");
    }

    /**
     * 设置当前用户查询（维修上下文）
     * 模型会根据此查询决定是否开口指导
     *
     * @param query 用户问题或当前维修步骤描述
     */
    public void setUserQuery(String query) {
        this.userQuery = query != null ? query : "";
        Log.d(TAG, "用户查询已设置: " + userQuery);
    }

    // ==================== 核心 API ====================

    /**
     * 发送视频帧到 JoyAI-VL 进行实时分析
     *
     * 模型会根据以下条件决定是否开口：
     * 1. 有 userQuery 时：分析帧内容并给出相关指导
     * 2. 无 userQuery 时：仅在检测到重要事件时主动开口
     *
     * @param base64Frame 视频帧 Base64 编码
     * @param callback    结果回调（主线程）
     */
    public void sendFrame(String base64Frame, JoyAiCallback callback) {
        if (!JoyAiVlConfig.ENABLED) {
            mainHandler.post(() -> callback.onError("JoyAI-VL 未启用"));
            return;
        }
        if (base64Frame == null || base64Frame.isEmpty()) {
            mainHandler.post(() -> callback.onError("帧数据为空"));
            return;
        }
        if (!isActive.get()) {
            mainHandler.post(() -> callback.onError("会话未启动"));
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject requestJson = buildFrameRequest(base64Frame);
                String responseBody = doPost(
                        JoyAiVlConfig.CHAT_COMPLETIONS_URL,
                        requestJson,
                        currentSessionId);

                if (responseBody == null) {
                    mainHandler.post(() -> callback.onError("服务无响应"));
                    return;
                }

                parseAndCallback(responseBody, callback);

            } catch (IOException e) {
                Log.e(TAG, "发送帧网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("无法连接 JoyAI-VL 服务: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "发送帧错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("JoyAI-VL 分析出错: " + e.getMessage()));
            }
        });
    }

    /**
     * 发送用户查询（带上下文的视频帧分析）
     *
     * @param base64Frame 视频帧 Base64 编码
     * @param query       用户问题
     * @param callback    结果回调（主线程）
     */
    public void sendFrameWithQuery(String base64Frame, String query, JoyAiCallback callback) {
        setUserQuery(query);
        sendFrame(base64Frame, callback);
    }

    /**
     * 发送纯文本查询（不带视频帧）
     *
     * @param query    用户问题
     * @param callback 结果回调（主线程）
     */
    public void sendQuery(String query, JoyAiCallback callback) {
        if (!JoyAiVlConfig.ENABLED) {
            mainHandler.post(() -> callback.onError("JoyAI-VL 未启用"));
            return;
        }
        if (!isActive.get()) {
            mainHandler.post(() -> callback.onError("会话未启动"));
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject requestJson = buildTextQueryRequest(query);
                String responseBody = doPost(
                        JoyAiVlConfig.CHAT_COMPLETIONS_URL,
                        requestJson,
                        currentSessionId);

                if (responseBody == null) {
                    mainHandler.post(() -> callback.onError("服务无响应"));
                    return;
                }

                parseAndCallback(responseBody, callback);

            } catch (IOException e) {
                Log.e(TAG, "发送查询网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("无法连接 JoyAI-VL 服务: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "发送查询错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("JoyAI-VL 查询出错: " + e.getMessage()));
            }
        });
    }

    /**
     * 健康检查：验证 JoyAI-VL 服务是否可用
     */
    public void checkHealth(HealthCallback callback) {
        executor.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(JoyAiVlConfig.HEALTH_URL)
                        .get()
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        mainHandler.post(callback::onHealthy);
                    } else {
                        mainHandler.post(() ->
                                callback.onUnhealthy("HTTP " + response.code()));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() ->
                        callback.onUnhealthy("无法连接: " + e.getMessage()));
            }
        });
    }

    /**
     * 重置当前会话（清空历史帧和记忆）
     */
    public void resetSession(JoyAiCallback callback) {
        if (!isActive.get()) {
            mainHandler.post(() -> callback.onError("会话未启动"));
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                Request request = new Request.Builder()
                        .url(JoyAiVlConfig.RESET_URL)
                        .post(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("x-streaming-session", currentSessionId)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        mainHandler.post(() -> callback.onQueryResponse("会话已重置"));
                    } else {
                        mainHandler.post(() ->
                                callback.onError("重置失败: HTTP " + response.code()));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() ->
                        callback.onError("重置失败: " + e.getMessage()));
            }
        });
    }

    // ==================== 请求构建 ====================

    /**
     * 构建发送视频帧的请求 JSON
     * 格式兼容 OpenAI chat completions
     */
    private JSONObject buildFrameRequest(String base64Frame) throws Exception {
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", JoyAiVlConfig.MODEL_NAME);

        JSONArray messages = new JSONArray();

        // System message（维修指导角色设定）
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", JoyAiVlConfig.REPAIR_SYSTEM_PROMPT);
        messages.put(systemMsg);

        // User message（多模态：文本 + 图像帧）
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");

        JSONArray content = new JSONArray();

        // 文本部分（用户查询或提示）
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        if (userQuery != null && !userQuery.isEmpty()) {
            textPart.put("text", userQuery);
        } else {
            textPart.put("text", "请观察当前维修场景，如有重要信息请指导，否则保持安静。");
        }
        content.put(textPart);

        // 图像部分（Base64 编码的帧）
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Frame);
        imagePart.put("image_url", imageUrl);
        content.put(imagePart);

        userMsg.put("content", content);
        messages.put(userMsg);

        requestJson.put("messages", messages);
        requestJson.put("temperature", JoyAiVlConfig.TEMPERATURE);
        requestJson.put("max_tokens", JoyAiVlConfig.MAX_TOKENS);

        return requestJson;
    }

    /**
     * 构建纯文本查询的请求 JSON
     */
    private JSONObject buildTextQueryRequest(String query) throws Exception {
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", JoyAiVlConfig.MODEL_NAME);

        JSONArray messages = new JSONArray();

        // System message
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", JoyAiVlConfig.REPAIR_SYSTEM_PROMPT);
        messages.put(systemMsg);

        // User message（纯文本）
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", query);
        messages.put(userMsg);

        requestJson.put("messages", messages);
        requestJson.put("temperature", JoyAiVlConfig.TEMPERATURE);
        requestJson.put("max_tokens", JoyAiVlConfig.MAX_TOKENS);

        return requestJson;
    }

    // ==================== 请求发送 ====================

    /**
     * 发送 HTTP POST 请求
     */
    private String doPost(String url, JSONObject body, String sessionId) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .addHeader("Content-Type", "application/json");

        if (sessionId != null && !sessionId.isEmpty()) {
            builder.addHeader("x-streaming-session", sessionId);
        }

        Request request = builder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "HTTP " + response.code() + ": " + errBody);
                throw new IOException("HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    // ==================== 响应解析 ====================

    /**
     * 解析 JoyAI-VL 响应并触发回调
     *
     * JoyAI-VL 的响应格式：
     * - 静默：content 包含 "</silence>"
     * - 指导：content 包含 "</response>" 后跟实际指导文本
     */
    private void parseAndCallback(String responseBody, JoyAiCallback callback) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");

            if (choices == null || choices.length() == 0) {
                mainHandler.post(callback::onSilent);
                return;
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.optJSONObject("message");
            if (message == null) {
                mainHandler.post(callback::onSilent);
                return;
            }

            String content = message.optString("content", "").trim();

            if (content.isEmpty()) {
                mainHandler.post(callback::onSilent);
                return;
            }

            // 检查是否为静默响应
            if (content.contains("</silence>")) {
                Log.d(TAG, "模型静默");
                mainHandler.post(callback::onSilent);
                return;
            }

            // 提取实际指导内容
            String guidance = extractGuidance(content);
            if (guidance != null && !guidance.isEmpty()) {
                Log.d(TAG, "收到指导: " + guidance);
                if (userQuery != null && !userQuery.isEmpty()) {
                    mainHandler.post(() -> callback.onQueryResponse(guidance));
                } else {
                    mainHandler.post(() -> callback.onProactiveGuidance(guidance));
                }
            } else {
                mainHandler.post(callback::onSilent);
            }

        } catch (Exception e) {
            Log.e(TAG, "解析响应错误: " + e.getMessage());
            mainHandler.post(() -> callback.onError("响应解析失败"));
        }
    }

    /**
     * 从模型输出中提取指导内容
     * 处理 </response> 标签格式
     */
    private String extractGuidance(String content) {
        // 处理 </response> 标签
        int responseIdx = content.indexOf("</response>");
        if (responseIdx >= 0) {
            String after = content.substring(responseIdx + "</response>".length()).trim();
            if (!after.isEmpty()) {
                return after;
            }
        }

        // 如果没有标签，直接返回内容
        return content;
    }

    // ==================== 工具方法 ====================

    /**
     * 检查服务是否可用
     */
    public boolean isEnabled() {
        return JoyAiVlConfig.ENABLED;
    }

    /**
     * 当前是否有活跃会话
     */
    public boolean isSessionActive() {
        return isActive.get() && currentSessionId != null;
    }

    /**
     * 获取当前会话 ID
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }
}
