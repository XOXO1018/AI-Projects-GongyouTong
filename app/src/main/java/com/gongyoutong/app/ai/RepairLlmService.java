package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.Config;
import com.gongyoutong.app.repair.RepairIntention;
import com.gongyoutong.app.repair.RepairStep;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
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
 * 维修 LLM 对话服务
 * 提供 SSE 流式维修对话、非流式步骤规划、意图识别
 */
public class RepairLlmService {

    private static final String TAG = "RepairLlmService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile RepairLlmService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private RepairLlmService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(Config.REPAIR_LLM_STREAM_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全 - DCL）
     */
    public static RepairLlmService getInstance() {
        if (sInstance == null) {
            synchronized (RepairLlmService.class) {
                if (sInstance == null) {
                    sInstance = new RepairLlmService();
                }
            }
        }
        return sInstance;
    }

    // ==================== System Prompt 定义 ====================

    private static final String SYSTEM_PROMPT_REPAIR =
            "你是一个专业的设备维修指导AI。你的职责是：\n" +
            "1. 根据设备型号和用户描述诊断故障\n" +
            "2. 提供详细的、分步骤的维修操作指导\n" +
            "3. 提醒安全注意事项\n" +
            "4. 回答用户的维修相关问题\n" +
            "请用简洁、专业的语言回答，每次只说一个步骤。";

    private static final String SYSTEM_PROMPT_STEPS =
            "你是维修专家。根据设备型号和故障，输出JSON格式的维修步骤列表。\n" +
            "必须严格返回以下JSON格式，不要添加任何其他文字：\n" +
            "{\"steps\": [{\"title\": \"步骤标题\", \"description\": \"详细描述\", " +
            "\"toolRequired\": \"所需工具\", \"safetyNote\": \"安全注意事项\"}]}";

    private static final String SYSTEM_PROMPT_INTENT =
            "根据当前维修状态和用户输入，分类为以下意图之一：\n" +
            "- ASK_GUIDANCE: 用户请求维修指导\n" +
            "- REPORT_PROGRESS: 用户报告维修进度\n" +
            "- ASK_TOOL: 用户询问工具/配件\n" +
            "- REPORT_ISSUE: 用户报告遇到了困难\n" +
            "- CONFIRM_COMPLETE: 用户确认维修完成\n" +
            "- OTHER: 其他\n\n" +
            "必须严格返回以下JSON格式：\n" +
            "{\"intention\": \"ASK_GUIDANCE\", \"confidence\": 0.95}";

    // ==================== 回调接口 ====================

    /** SSE 流式回调 */
    public interface StreamCallback {
        void onStart();
        void onDelta(String text, boolean isComplete);
        void onError(String msg);
    }

    /** 步骤规划回调 */
    public interface StepsCallback {
        void onStepsReady(List<RepairStep> steps);
        void onError(String msg);
    }

    /** 意图识别回调 */
    public interface IntentCallback {
        void onIntent(RepairIntention intention);
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * SSE 流式维修对话
     *
     * @param prompt       用户输入
     * @param stateContext 当前维修状态上下文
     * @param callback     流式回调（主线程）
     */
    public void chatStream(String prompt, String stateContext, StreamCallback callback) {
        if (prompt == null || prompt.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("输入内容为空"));
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", Config.VIVO_MODEL);

                JSONArray messages = new JSONArray();

                // System 消息：根据状态上下文选择不同的引导语
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                String systemContent = buildSystemPrompt(stateContext);
                systemMsg.put("content", systemContent);
                messages.put(systemMsg);

                // User 消息
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", true);
                requestJson.put("temperature", 0.7);
                requestJson.put("max_tokens", 1024);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(Config.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + Config.VIVO_APP_KEY)
                        .addHeader("Accept", "text/event-stream")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "chatStream HTTP " + response.code() + ": " + errBody);
                        mainHandler.post(() -> callback.onError("AI 服务暂时不可用，请稍后重试"));
                        return;
                    }

                    mainHandler.post(callback::onStart);

                    if (response.body() == null) {
                        mainHandler.post(() -> callback.onError("响应为空"));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(response.body().charStream());
                    String line;
                    StringBuilder fullReply = new StringBuilder();
                    boolean receivedAnyContent = false;

                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "SSE raw line: " + line);

                        String data = null;
                        if (line.startsWith("data: ")) {
                            data = line.substring(6);
                        } else if (line.startsWith("data:")) {
                            data = line.substring(5);
                        }

                        if (data != null) {
                            if ("[DONE]".equals(data.trim())) {
                                break;
                            }
                            try {
                                JSONObject json = new JSONObject(data);
                                JSONArray choices = json.optJSONArray("choices");
                                if (choices != null && choices.length() > 0) {
                                    JSONObject choice = choices.getJSONObject(0);
                                    JSONObject delta = choice.optJSONObject("delta");
                                    if (delta != null) {
                                        String content = delta.optString("content", "");
                                        if (!content.isEmpty()) {
                                            receivedAnyContent = true;
                                            fullReply.append(content);
                                            String currentText = fullReply.toString();
                                            mainHandler.post(() -> callback.onDelta(currentText, false));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "SSE parse error: " + e.getMessage() + " | data=" + data);
                            }
                        }
                    }

                    String finalReply = fullReply.toString();
                    Log.d(TAG, "流式回复完成, 总长度=" + finalReply.length()
                            + ", receivedAnyContent=" + receivedAnyContent);
                    mainHandler.post(() -> callback.onDelta(finalReply, true));
                }

            } catch (IOException e) {
                Log.e(TAG, "chatStream 网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "chatStream 错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("对话出错，请重试"));
            }
        });
    }

    /**
     * 非流式步骤规划
     *
     * @param deviceModel 设备型号
     * @param faultType   故障类型
     * @param callback    结果回调（主线程）
     */
    public void planSteps(String deviceModel, String faultType, StepsCallback callback) {
        if (deviceModel == null && faultType == null) {
            mainHandler.post(() -> callback.onError("设备型号和故障类型不能都为空"));
            return;
        }

        executor.execute(() -> {
            try {
                String prompt = "设备型号：" + (deviceModel != null ? deviceModel : "未知") + "\n"
                        + "故障类型：" + (faultType != null ? faultType : "未知") + "\n"
                        + "请为以上设备和故障规划详细的维修步骤列表。";

                JSONObject requestJson = new JSONObject();
                requestJson.put("model", Config.VIVO_MODEL);

                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", SYSTEM_PROMPT_STEPS);
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 1500);

                String content = executeNonStreamRequest(requestJson);

                if (content == null || content.isEmpty()) {
                    mainHandler.post(() -> callback.onError("AI 未返回有效步骤"));
                    return;
                }

                List<RepairStep> steps = parseStepsJson(content);
                if (steps.isEmpty()) {
                    mainHandler.post(() -> callback.onError("步骤解析失败，请重试"));
                    return;
                }

                Log.d(TAG, "步骤规划完成, 共 " + steps.size() + " 步");
                mainHandler.post(() -> callback.onStepsReady(steps));

            } catch (IOException e) {
                Log.e(TAG, "planSteps 网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "planSteps 错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("步骤规划出错，请重试"));
            }
        });
    }

    /**
     * 意图识别
     *
     * @param text         用户输入文本
     * @param stateContext 当前维修状态上下文
     * @param callback     结果回调（主线程）
     */
    public void recognizeIntent(String text, String stateContext, IntentCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("输入内容为空"));
            return;
        }

        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", Config.VIVO_MODEL);

                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                String systemPrompt = SYSTEM_PROMPT_INTENT;
                if (stateContext != null && !stateContext.isEmpty()) {
                    systemPrompt += "\n\n当前维修状态：" + stateContext;
                }
                systemMsg.put("content", systemPrompt);
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", text);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.1);
                requestJson.put("max_tokens", 200);

                String content = executeNonStreamRequest(requestJson);

                if (content == null || content.isEmpty()) {
                    mainHandler.post(() -> callback.onError("AI 未返回意图识别结果"));
                    return;
                }

                RepairIntention intention = parseIntentionJson(content);
                if (intention == null) {
                    mainHandler.post(() -> callback.onError("意图解析失败，请重试"));
                    return;
                }

                Log.d(TAG, "意图识别完成: " + intention.getType()
                        + ", confidence=" + intention.getConfidence());
                mainHandler.post(() -> callback.onIntent(intention));

            } catch (IOException e) {
                Log.e(TAG, "recognizeIntent 网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "recognizeIntent 错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("意图识别出错，请重试"));
            }
        });
    }

    // ==================== 内部实现 ====================

    /**
     * 执行非流式请求并返回 content
     */
    private String executeNonStreamRequest(JSONObject requestJson) throws IOException {
        OkHttpClient shortTimeoutClient = httpClient.newBuilder()
                .readTimeout(Config.REPAIR_LLM_CHAT_TIMEOUT, TimeUnit.SECONDS)
                .build();

        RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
        Request request = new Request.Builder()
                .url(Config.VIVO_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + Config.VIVO_APP_KEY)
                .build();

        try (Response response = shortTimeoutClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "(empty)";
                Log.e(TAG, "非流式请求 HTTP " + response.code() + ": " + errBody);
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return extractContent(responseBody);
        }
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
     * 根据状态上下文选择 System Prompt
     */
    private String buildSystemPrompt(String stateContext) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_REPAIR);
        if (stateContext != null && !stateContext.isEmpty()) {
            sb.append("\n\n当前状态：").append(stateContext);
        }
        return sb.toString();
    }

    /**
     * 解析 AI 返回的步骤 JSON
     * 格式：{"steps": [{"title": "...", "description": "...", "toolRequired": "...", "safetyNote": "..."}]}
     */
    private List<RepairStep> parseStepsJson(String jsonStr) {
        List<RepairStep> steps = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) {
            return steps;
        }

        try {
            String cleanJson = cleanJsonString(jsonStr);
            JSONObject json = new JSONObject(cleanJson);
            JSONArray stepsArray = json.optJSONArray("steps");
            if (stepsArray != null) {
                for (int i = 0; i < stepsArray.length(); i++) {
                    JSONObject stepObj = stepsArray.getJSONObject(i);
                    RepairStep step = new RepairStep();
                    step.setTitle(stepObj.optString("title", ""));
                    step.setDescription(stepObj.optString("description", ""));
                    step.setToolRequired(stepObj.optString("toolRequired", ""));
                    step.setSafetyNote(stepObj.optString("safetyNote", ""));
                    steps.add(step);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseStepsJson error: " + e.getMessage());
        }

        return steps;
    }

    /**
     * 解析 AI 返回的意图 JSON
     * 格式：{"intention": "ASK_GUIDANCE", "confidence": 0.95}
     */
    private RepairIntention parseIntentionJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }

        try {
            String cleanJson = cleanJsonString(jsonStr);
            JSONObject json = new JSONObject(cleanJson);
            String intentionStr = json.optString("intention", "OTHER");
            float confidence = (float) json.optDouble("confidence", 0.0);

            RepairIntention.Type type;
            try {
                type = RepairIntention.Type.valueOf(intentionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = RepairIntention.Type.OTHER;
            }

            return new RepairIntention(type, confidence, jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "parseIntentionJson error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 清理 AI 返回的 JSON 字符串（去除 markdown 标记）
     */
    private String cleanJsonString(String jsonStr) {
        String clean = jsonStr.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }
}
