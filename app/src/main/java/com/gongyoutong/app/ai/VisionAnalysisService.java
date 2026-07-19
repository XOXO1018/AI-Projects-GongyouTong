package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.repair.FrameAnalysisResult;
import com.gongyoutong.app.repair.FrameAnalysisResult.BoundingBox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI 视觉分析服务
 * 分析视频帧，返回场景描述和检测区域
 */
public class VisionAnalysisService {

    private static final String TAG = "VisionAnalysisService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile VisionAnalysisService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private VisionAnalysisService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(AiConfig.VISION_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全 - DCL）
     */
    public static VisionAnalysisService getInstance() {
        if (sInstance == null) {
            synchronized (VisionAnalysisService.class) {
                if (sInstance == null) {
                    sInstance = new VisionAnalysisService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    /** 视觉分析回调 */
    public interface VisionCallback {
        void onSuccess(String description, List<BoundingBox> regions, float confidence);
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * 分析视频帧（视觉理解）
     *
     * @param base64Frame 视频帧 Base64 编码（不含 data URI 前缀）
     * @param context     分析上下文（如：当前维修步骤）
     * @param callback    结果回调（主线程）
     */
    public void analyze(String base64Frame, String context, VisionCallback callback) {
        if (base64Frame == null || base64Frame.isEmpty()) {
            mainHandler.post(() -> callback.onError("图片帧数据为空"));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. 构造多模态请求 JSON
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", AiConfig.VIVO_VISION_MODEL);

                JSONArray messages = new JSONArray();

                // User 消息（多模态 content 数组格式）
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");

                JSONArray userContent = new JSONArray();

                // 文本部分
                StringBuilder textBuilder = new StringBuilder();
                textBuilder.append("请分析这张维修现场照片，并作为维修师傅给出下一步指导。");
                if (context != null && !context.isEmpty()) {
                    textBuilder.append(" 当前上下文：").append(context);
                }
                textBuilder.append(" 必须只返回 JSON，不要添加 markdown 或解释。格式如下：");
                textBuilder.append("{\"deviceType\":\"设备类型或未知设备\",");
                textBuilder.append("\"faultGuess\":\"可能故障\",");
                textBuilder.append("\"currentStep\":\"当前应该做什么\",");
                textBuilder.append("\"safetyWarning\":\"安全提醒，没有则为空字符串\",");
                textBuilder.append("\"nextAction\":\"下一步动作\",");
                textBuilder.append("\"confidence\":0.0,");
                textBuilder.append("\"regions\":[{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.2,\"label\":\"故障区域\",\"confidence\":0.8}]}。");
                textBuilder.append(" regions 使用 0 到 1 的归一化坐标；无法判断坐标时返回空数组。");

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", textBuilder.toString());
                userContent.put(textPart);

                // 图片部分
                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", "data:image/jpeg;base64," + base64Frame);
                imagePart.put("image_url", imageUrl);
                userContent.put(imagePart);

                userMsg.put("content", userContent);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 1000);

                // 2. 发送请求
                RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(AiConfig.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", AiConfig.authHeader())
                        .build();

                Log.d(TAG, "视觉分析请求: 帧长度=" + base64Frame.length());

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "视觉分析 HTTP " + response.code() + ": " + errBody);
                        mainHandler.post(() -> callback.onError("视觉分析服务暂时不可用，请稍后重试"));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    String content = extractContent(responseBody);

                    if (content == null || content.isEmpty()) {
                        Log.w(TAG, "视觉分析返回空内容");
                        mainHandler.post(() -> callback.onError("AI 未返回有效分析内容"));
                        return;
                    }

                    Log.d(TAG, "视觉分析成功, 内容长度=" + content.length());

                    // 3. 解析响应
                    FrameAnalysisResult result = parseVisionResponse(content);
                    String description = result.getDescription();
                    List<BoundingBox> regions = result.getRegions();
                    float confidence = result.getConfidence();

                    mainHandler.post(() -> callback.onSuccess(description, regions, confidence));
                }

            } catch (IOException e) {
                Log.e(TAG, "视觉分析网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "视觉分析错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("视觉分析出错，请重试"));
            }
        });
    }

    // ==================== 内部实现 ====================

    /**
     * 从 OpenAI 兼容格式响应中提取 content
     */
    private String extractContent(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    String content = message.optString("content", "").trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                }
                JSONObject delta = firstChoice.optJSONObject("delta");
                if (delta != null) {
                    String content = delta.optString("content", "").trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "extractContent error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析视觉分析响应
     * 如果响应包含 JSON 结构，解析为结构化数据
     * 否则将全文本作为 description 返回，regions 为空
     */
    private FrameAnalysisResult parseVisionResponse(String content) {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setDescription(content);
        result.setRegions(new ArrayList<BoundingBox>());
        result.setConfidence(0.5f);

        // 尝试在内容中提取 JSON 部分
        String jsonPart = extractJsonFromText(content);
        if (jsonPart == null) {
            return result;
        }

        try {
            JSONObject json = new JSONObject(jsonPart);

            String deviceType = json.optString("deviceType", "");
            String faultGuess = json.optString("faultGuess", "");
            String currentStep = json.optString("currentStep", "");
            String safetyWarning = json.optString("safetyWarning", "");
            String nextAction = json.optString("nextAction", "");

            String description = json.optString("description", "");
            if (!deviceType.isEmpty() || !faultGuess.isEmpty()
                    || !currentStep.isEmpty() || !nextAction.isEmpty()) {
                StringBuilder formatted = new StringBuilder();
                if (!deviceType.isEmpty()) {
                    formatted.append("设备：").append(deviceType);
                }
                if (!faultGuess.isEmpty()) {
                    appendLine(formatted, "可能故障：", faultGuess);
                }
                if (!currentStep.isEmpty()) {
                    appendLine(formatted, "当前步骤：", currentStep);
                }
                if (!safetyWarning.isEmpty()) {
                    appendLine(formatted, "安全提醒：", safetyWarning);
                }
                if (!nextAction.isEmpty()) {
                    appendLine(formatted, "下一步：", nextAction);
                }
                result.setDescription(formatted.toString());
            } else if (!description.isEmpty()) {
                result.setDescription(description);
            }

            // 如果有 confidence 字段
            float confidence = (float) json.optDouble("confidence", 0.5);
            result.setConfidence(confidence);

            // 如果有 regions/objects/bboxes 数组
            JSONArray regions = null;
            if (json.has("regions")) {
                regions = json.optJSONArray("regions");
            } else if (json.has("objects")) {
                regions = json.optJSONArray("objects");
            } else if (json.has("bboxes")) {
                regions = json.optJSONArray("bboxes");
            }

            if (regions != null) {
                List<BoundingBox> boxes = new ArrayList<>();
                for (int i = 0; i < regions.length(); i++) {
                    JSONObject regionObj = regions.getJSONObject(i);
                    BoundingBox box = new BoundingBox();
                    box.x = (float) regionObj.optDouble("x", 0.0);
                    box.y = (float) regionObj.optDouble("y", 0.0);
                    box.width = (float) regionObj.optDouble("width", 0.0);
                    box.height = (float) regionObj.optDouble("height", 0.0);
                    box.label = regionObj.optString("label", "");
                    box.confidence = (float) regionObj.optDouble("confidence", 0.0);
                    boxes.add(box);
                }
                result.setRegions(boxes);
            }

        } catch (Exception e) {
            Log.w(TAG, "parseVisionResponse: 非结构化响应，使用全文描述: " + e.getMessage());
        }

        return result;
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(label).append(value);
    }

    /**
     * 从文本中提取 JSON 部分
     * 处理 AI 可能包裹在 markdown 中的情况
     */
    private String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String trimmed = text.trim();

        // 尝试查找 markdown 包裹的 JSON
        int jsonStart = trimmed.indexOf("{");
        int jsonEnd = trimmed.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }

        return null;
    }
}
