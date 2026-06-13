package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.Config;
import com.gongyoutong.app.repair.DiagnosisRequest;
import com.gongyoutong.app.repair.DiagnosisResult;
import com.gongyoutong.app.repair.RepairIntention;
import com.gongyoutong.app.repair.RepairStep;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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
 * 工单 AI 服务
 * 提供故障预判和工具推荐功能，使用蓝心大模型（OpenAI 兼容协议）
 */
public class WorkOrderAiService {

    private static final String TAG = "WorkOrderAiService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile WorkOrderAiService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private WorkOrderAiService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全）
     */
    public static WorkOrderAiService getInstance() {
        if (sInstance == null) {
            synchronized (WorkOrderAiService.class) {
                if (sInstance == null) {
                    sInstance = new WorkOrderAiService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    /** 故障预判回调 */
    public interface FaultPredictionCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }

    /** 工具推荐回调 */
    public interface ToolRecommendationCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }

    /** AI 诊断回调 */
    public interface DiagnosisCallback {
        void onSuccess(DiagnosisResult result);
        void onError(String errorMessage);
    }

    // ==================== 核心 API ====================

    /**
     * AI 故障预判
     * 根据工单标题、类型和描述，分析可能的故障原因
     *
     * @param title       工单标题
     * @param workType    工单类型
     * @param description 故障描述
     * @param callback    结果回调（主线程）
     */
    public void predictFault(String title, String workType, String description,
                             FaultPredictionCallback callback) {
        if (title == null && workType == null && (description == null || description.isEmpty())) {
            mainHandler.post(() -> callback.onError("工单信息为空，无法进行故障预判"));
            return;
        }

        String prompt = "你是一个专业维修顾问。根据以下工单信息，分析可能的故障原因。\n"
                + "标题：" + (title != null ? title : "未知") + "\n"
                + "类型：" + (workType != null ? workType : "未知") + "\n"
                + "描述：" + (description != null ? description : "无") + "\n\n"
                + "请给出：\n"
                + "1. 可能故障原因（最多3个）\n"
                + "2. 预估难度（简单/中等/困难）\n"
                + "3. 预估耗时\n\n"
                + "请用简洁的中文回答，直接列出分析结果。";

        callAi(prompt, new AiResultCallback() {
            @Override
            public void onSuccess(String content) {
                mainHandler.post(() -> callback.onSuccess(content));
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    /**
     * AI 工具推荐
     * 根据工单类型和故障预判结果，推荐需要携带的工具和配件
     *
     * @param workType     工单类型
     * @param aiPrediction AI 故障预判结果
     * @param callback     结果回调（主线程）
     */
    public void recommendTools(String workType, String aiPrediction,
                               ToolRecommendationCallback callback) {
        if (workType == null && (aiPrediction == null || aiPrediction.isEmpty())) {
            mainHandler.post(() -> callback.onError("信息不足，无法推荐工具"));
            return;
        }

        String prompt = "根据以下故障预判结果，推荐需要携带的工具和配件。\n"
                + "类型：" + (workType != null ? workType : "未知") + "\n"
                + "预判：" + (aiPrediction != null ? aiPrediction : "无") + "\n\n"
                + "请列出：\n"
                + "1. 推荐工具\n"
                + "2. 可能需要的配件\n\n"
                + "请用简洁的中文回答，直接列出推荐结果。";

        callAi(prompt, new AiResultCallback() {
            @Override
            public void onSuccess(String content) {
                mainHandler.post(() -> callback.onSuccess(content));
            }

            @Override
            public void onError(String errorMessage) {
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    // ==================== 内部实现 ====================

    /** 诊断专用 System Prompt */
    private static final String DIAGNOSIS_SYSTEM_PROMPT =
            "你是一位专业的设备维修诊断助手。根据用户提供的故障描述和设备照片，给出结构化的诊断建议。\n\n"
            + "你必须严格返回以下 JSON 格式，不要添加任何其他文字：\n"
            + "{\n"
            + "  \"faultCauses\": \"可能的故障原因（1-3条，每条一行，含可能性百分比）\",\n"
            + "  \"inspectionSteps\": \"排查步骤（编号列表，每步一行，具体可操作）\",\n"
            + "  \"safetyTips\": \"安全注意事项（关键提醒，每条一行，突出危险性）\",\n"
            + "  \"requiredTools\": \"所需工具（逗号分隔）\",\n"
            + "  \"requiredParts\": \"可能需要更换的配件（逗号分隔）\",\n"
            + "  \"estimatedTime\": \"预估维修耗时（如：约30-45分钟）\"\n"
            + "}";

    /** 内部回调 */
    private interface AiResultCallback {
        void onSuccess(String content);
        void onError(String errorMessage);
    }

    /**
     * 调用蓝心大模型 API（OpenAI 兼容协议，非流式）
     */
    private void callAi(String prompt, AiResultCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", Config.VIVO_MODEL);

                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一个专业的维修顾问，擅长家电维修和家政服务领域。"
                        + "请用中文回答，语气专业实用，直接给出结论。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 600);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(Config.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + Config.VIVO_APP_KEY)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "AI API HTTP " + response.code() + ": " + errBody);
                        callback.onError("AI 服务暂时不可用，请稍后重试");
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    String content = extractContent(responseBody);

                    if (content == null || content.isEmpty()) {
                        Log.w(TAG, "AI API returned empty content");
                        callback.onError("AI 未返回有效内容");
                        return;
                    }

                    Log.d(TAG, "AI 调用成功, 内容长度=" + content.length());
                    callback.onSuccess(content);
                }

            } catch (IOException e) {
                Log.e(TAG, "AI 网络错误: " + e.getMessage());
                callback.onError("网络连接失败，请检查网络后重试");
            } catch (Exception e) {
                Log.e(TAG, "AI 调用错误: " + e.getMessage());
                callback.onError("AI 服务出错，请重试");
            }
        });
    }

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
                // 兼容 delta 格式
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

    // ==================== 多模态诊断 API ====================

    /**
     * AI 多模态故障诊断
     * 支持文字+图片输入，返回结构化诊断结果
     *
     * @param request  诊断请求（含故障描述、照片、历史上下文）
     * @param callback 结果回调（主线程）
     */
    public void diagnoseFault(DiagnosisRequest request, DiagnosisCallback callback) {
        if (request == null || (request.getFaultDescription() == null || request.getFaultDescription().isEmpty())
                && (request.getPhotoBase64List() == null || request.getPhotoBase64List().isEmpty())) {
            mainHandler.post(() -> callback.onError("请提供故障描述或照片"));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. 构造请求 JSON
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", Config.VIVO_MODEL);

                JSONArray messages = new JSONArray();

                // System 消息
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", DIAGNOSIS_SYSTEM_PROMPT);
                messages.put(systemMsg);

                // User 消息（多模态 content 数组格式）
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");

                JSONArray userContent = new JSONArray();

                // 文本部分
                StringBuilder textBuilder = new StringBuilder();
                if (request.getWorkOrderTitle() != null && !request.getWorkOrderTitle().isEmpty()) {
                    textBuilder.append("工单标题：").append(request.getWorkOrderTitle()).append("\n");
                }
                if (request.getWorkType() != null && !request.getWorkType().isEmpty()) {
                    textBuilder.append("维修类型：").append(request.getWorkType()).append("\n");
                }
                if (request.getFaultDescription() != null && !request.getFaultDescription().isEmpty()) {
                    textBuilder.append("故障描述：").append(request.getFaultDescription());
                }

                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", textBuilder.toString());
                userContent.put(textPart);

                // 图片部分
                if (request.getPhotoBase64List() != null) {
                    for (String base64 : request.getPhotoBase64List()) {
                        if (base64 != null && !base64.isEmpty()) {
                            JSONObject imagePart = new JSONObject();
                            imagePart.put("type", "image_url");
                            JSONObject imageUrl = new JSONObject();
                            imageUrl.put("url", "data:image/jpeg;base64," + base64);
                            imagePart.put("image_url", imageUrl);
                            userContent.put(imagePart);
                        }
                    }
                }

                userMsg.put("content", userContent);
                messages.put(userMsg);

                // 历史上下文消息（最近2轮）
                if (request.getHistoryMessages() != null && !request.getHistoryMessages().isEmpty()) {
                    int startIndex = Math.max(0, request.getHistoryMessages().size() - 4); // 最多4条消息=2轮
                    for (int i = startIndex; i < request.getHistoryMessages().size(); i++) {
                        DiagnosisRequest.DiagnosisHistoryMessage histMsg = request.getHistoryMessages().get(i);
                        JSONObject histJson = new JSONObject();
                        histJson.put("role", histMsg.getRole());

                        if ("assistant".equals(histMsg.getRole())) {
                            // assistant 消息使用普通字符串 content
                            histJson.put("content", histMsg.getContent());
                        } else {
                            // user 补充消息使用文本格式
                            histJson.put("content", histMsg.getContent());
                        }

                        messages.put(histJson);
                    }
                }

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 1500);

                // 2. 调用多模态 API
                callAiMultimodal(requestJson, new AiResultCallback() {
                    @Override
                    public void onSuccess(String content) {
                        // 3. 解析 AI 返回的 JSON → DiagnosisResult
                        DiagnosisResult result = parseDiagnosisJson(content);
                        if (result == null) {
                            // JSON 解析失败，降级为纯文本
                            result = new DiagnosisResult();
                            result.setFaultCauses(content);
                            result.setInspectionSteps("暂无信息");
                            result.setSafetyTips("暂无信息");
                            result.setRequiredTools("暂无信息");
                            result.setRequiredParts("暂无信息");
                            result.setEstimatedTime("暂无信息");
                            result.setRawResponse(content);
                        }
                        DiagnosisResult finalResult = result;
                        mainHandler.post(() -> callback.onSuccess(finalResult));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> callback.onError(errorMessage));
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "diagnoseFault error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("诊断请求构造失败，请重试"));
            }
        });
    }

    /**
     * 调用蓝心大模型多模态 API（OpenAI 兼容协议，非流式，60s 超时）
     */
    private void callAiMultimodal(JSONObject requestBody, AiResultCallback callback) {
        executor.execute(() -> {
            try {
                // 创建独立的长超时 OkHttpClient（60s 诊断超时）
                OkHttpClient diagnosisClient = httpClient.newBuilder()
                        .readTimeout(Config.AI_DIAGNOSIS_TIMEOUT, TimeUnit.SECONDS)
                        .build();

                RequestBody body = RequestBody.create(requestBody.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(Config.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + Config.VIVO_APP_KEY)
                        .build();

                try (Response response = diagnosisClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "Diagnosis API HTTP " + response.code() + ": " + errBody);
                        callback.onError("AI 诊断服务暂时不可用，请稍后重试");
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    String content = extractContent(responseBody);

                    if (content == null || content.isEmpty()) {
                        Log.w(TAG, "Diagnosis API returned empty content");
                        callback.onError("AI 未返回有效诊断内容");
                        return;
                    }

                    Log.d(TAG, "Diagnosis AI 调用成功, 内容长度=" + content.length());
                    callback.onSuccess(content);
                }

            } catch (IOException e) {
                Log.e(TAG, "Diagnosis 网络错误: " + e.getMessage());
                callback.onError("网络连接失败，请检查网络后重试");
            } catch (Exception e) {
                Log.e(TAG, "Diagnosis 调用错误: " + e.getMessage());
                callback.onError("AI 诊断服务出错，请重试");
            }
        });
    }

    /**
     * 解析 AI 返回的 JSON 字符串为 DiagnosisResult
     * 容错处理：字段缺失显示"暂无信息"，整体解析失败返回 null（由调用方降级）
     */
    private DiagnosisResult parseDiagnosisJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }

        try {
            // 尝试提取 JSON 部分（AI 有时会在 JSON 前后添加 markdown 标记）
            String cleanJson = jsonStr.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            } else if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            JSONObject json = new JSONObject(cleanJson);

            DiagnosisResult result = new DiagnosisResult();
            result.setFaultCauses(json.optString("faultCauses", "暂无信息"));
            result.setInspectionSteps(json.optString("inspectionSteps", "暂无信息"));
            result.setSafetyTips(json.optString("safetyTips", "暂无信息"));
            result.setRequiredTools(json.optString("requiredTools", "暂无信息"));
            result.setRequiredParts(json.optString("requiredParts", "暂无信息"));
            result.setEstimatedTime(json.optString("estimatedTime", "暂无信息"));
            result.setRawResponse(jsonStr);

            // 初始化步骤勾选状态
            result.initStepCheckedStates(result.getInspectionSteps());

            return result;

        } catch (Exception e) {
            Log.e(TAG, "parseDiagnosisJson error: " + e.getMessage());
            return null; // 解析失败，由调用方降级为纯文本
        }
    }

    // ==================== 维修步骤规划与意图识别（委托 RepairLlmService） ====================

    /** 维修步骤规划回调 */
    public interface PlanStepsCallback {
        void onSuccess(List<RepairStep> steps);
        void onError(String msg);
    }

    /** 意图识别回调 */
    public interface IntentCallback {
        void onSuccess(RepairIntention intention);
        void onError(String msg);
    }

    /**
     * 规划维修步骤（非流式）
     * 委托 RepairLlmService 执行
     *
     * @param deviceModel 设备型号
     * @param faultType   故障类型
     * @param callback    结果回调（主线程）
     */
    public void planRepairSteps(String deviceModel, String faultType, PlanStepsCallback callback) {
        RepairLlmService.getInstance().planSteps(deviceModel, faultType,
                new RepairLlmService.StepsCallback() {
                    @Override
                    public void onStepsReady(List<RepairStep> steps) {
                        mainHandler.post(() -> callback.onSuccess(steps));
                    }

                    @Override
                    public void onError(String msg) {
                        mainHandler.post(() -> callback.onError(msg));
                    }
                });
    }

    /**
     * 识别用户意图
     * 委托 RepairLlmService 执行
     *
     * @param text         用户输入文本
     * @param stateContext 当前维修状态上下文
     * @param callback     结果回调（主线程）
     */
    public void recognizeUserIntent(String text, String stateContext, IntentCallback callback) {
        RepairLlmService.getInstance().recognizeIntent(text, stateContext,
                new RepairLlmService.IntentCallback() {
                    @Override
                    public void onIntent(RepairIntention intention) {
                        mainHandler.post(() -> callback.onSuccess(intention));
                    }

                    @Override
                    public void onError(String msg) {
                        mainHandler.post(() -> callback.onError(msg));
                    }
                });
    }
}
