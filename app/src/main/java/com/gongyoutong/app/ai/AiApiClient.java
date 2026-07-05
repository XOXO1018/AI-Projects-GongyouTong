package com.gongyoutong.app.ai;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ============================================================
 * AI 接口统一管理中心
 * ============================================================
 *
 * 本文件集中管理项目中所有 vivo 蓝心大模型能力接口：
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │ 接口编号 │ 接口名称           │ 协议    │ 模型                    │
 * ├─────────────────────────────────────────────────────────────┤
 * │ #01     │ 聊天补全(非流式)    │ HTTP    │ Volc-DeepSeek-V3.2      │
 * │ #02     │ 聊天补全(流式SSE)   │ HTTP    │ Volc-DeepSeek-V3.2      │
 * │ #03     │ 多模态诊断         │ HTTP    │ Volc-DeepSeek-V3.2      │
 * │ #04     │ 视觉分析           │ HTTP    │ Volc-DeepSeek-V3.2      │
 * │ #05     │ 图片生成           │ HTTP    │ Doubao-Seedream-4.5     │
 * │ #06     │ OCR 文字识别       │ HTTP    │ vivo-ocr-general        │
 * │ #07     │ 实时短语音识别(ASR) │ WebSocket │ shortasrinput       │
 * │ #08     │ 语音合成(TTS)      │ WebSocket │ tts_humanoid_lam    │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 使用说明：
 * - 非流式调用：调用 chatCompletion() 或 chatCompletionWithImages()
 * - 流式调用：调用 chatCompletionStream()，通过 BufferedReader 逐行读取 SSE
 * - 图片生成：调用 generateImage()
 * - OCR：调用 recognizeText()
 * - ASR/TTS：由 VivoAsrService / VivoTtsService 管理（WebSocket 协议）
 *
 * 每个接口方法都附带了详细注释，标注了接口地址、请求格式、响应格式。
 */
public class AiApiClient {

    private static final String TAG = "AiApiClient";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    // ==================== 共享 OkHttpClient（全局复用） ====================
    private static volatile OkHttpClient sHttpClient;

    public static OkHttpClient getHttpClient() {
        if (sHttpClient == null) {
            synchronized (AiApiClient.class) {
                if (sHttpClient == null) {
                    sHttpClient = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(180, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(false)
                            .build();
                }
            }
        }
        return sHttpClient;
    }

    /**
     * 创建自定义超时的 OkHttpClient（基于共享实例）
     *
     * @param readTimeoutSec 读超时（秒）
     */
    public static OkHttpClient getHttpClient(int readTimeoutSec) {
        return getHttpClient().newBuilder()
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .build();
    }

    // ============================================================
    // #01 聊天补全（非流式）
    // ============================================================
    //
    // 接口地址：POST https://api-ai.vivo.com.cn/v1/chat/completions
    // 协议：OpenAI 兼容（非流式）
    // 模型：Volc-DeepSeek-V3.2
    // 认证：Bearer Token (Config.VIVO_APP_KEY)
    //
    // 请求体示例：
    //   {
    //     "model": "Volc-DeepSeek-V3.2",
    //     "messages": [
    //       {"role": "system", "content": "你是..."},
    //       {"role": "user", "content": "..."}
    //     ],
    //     "stream": false,
    //     "temperature": 0.3,
    //     "max_tokens": 1000
    //   }
    //
    // 响应体示例：
    //   {
    //     "choices": [{
    //       "message": {"role": "assistant", "content": "..."},
    //       "finish_reason": "stop"
    //     }]
    //   }
    //
    // 使用者：VivoAiService, QuotationAiService, OnlineKnowledgeService,
    //        WorkOrderAiService, RepairLlmService
    // ============================================================

    /**
     * #01 非流式聊天补全
     *
     * @param messages    完整的 messages 数组（含 system + user）
     * @param temperature 温度参数（0.0-1.0，越低越确定）
     * @param maxTokens   最大输出 token 数
     * @return AI 返回的 content 文本，失败返回 null
     */
    public static String chatCompletion(JSONObject messages, double temperature, int maxTokens) {
        return chatCompletion(AiConfig.VIVO_MODEL, messages, temperature, maxTokens, 120);
    }

    /**
     * #01 非流式聊天补全（指定模型和超时）
     */
    public static String chatCompletion(String model, JSONObject messages,
                                         double temperature, int maxTokens, int timeoutSec) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", model);
            requestJson.put("messages", messages);
            requestJson.put("stream", false);
            requestJson.put("temperature", temperature);
            requestJson.put("max_tokens", maxTokens);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
            Request request = new Request.Builder()
                    .url(AiConfig.VIVO_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", AiConfig.authHeader())
                    .build();

            OkHttpClient client = getHttpClient(timeoutSec);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "(empty)";
                    Log.e(TAG, "#01 chatCompletion HTTP " + response.code() + ": " + errBody);
                    return null;
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                return extractContent(responseBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "#01 chatCompletion error: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // #02 聊天补全（流式 SSE）
    // ============================================================
    //
    // 接口地址：POST https://api-ai.vivo.com.cn/v1/chat/completions
    // 协议：OpenAI 兼容（流式 SSE，stream: true）
    // 模型：Volc-DeepSeek-V3.2
    //
    // 请求体：同 #01，但 "stream": true
    //
    // 响应格式（SSE 逐行）：
    //   data: {"choices":[{"delta":{"content":"你"},"index":0}]}
    //   data: {"choices":[{"delta":{"content":"好"},"index":0}]}
    //   data: [DONE]
    //
    // 注意：蓝心大模型 SSE 格式为 "data:{...}"（无空格），
    //       标准 OpenAI 格式为 "data: {...}"（有空格），两种都兼容。
    //
    // 使用者：RepairLlmService.chatStream(), VivoAiService.chatWithAiStream()
    // ============================================================

    /**
     * #02 流式聊天补全 —— 将整个流式响应读取为完整文本
     *
     * @param messages    完整的 messages 数组
     * @param temperature 温度参数
     * @param maxTokens   最大输出 token 数
     * @return AI 返回的完整 content 文本，失败返回 null
     */
    public static String chatCompletionStream(JSONObject messages, double temperature, int maxTokens) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("messages", messages);
            requestJson.put("model", AiConfig.VIVO_MODEL);
            requestJson.put("stream", true);
            requestJson.put("temperature", temperature);
            requestJson.put("max_tokens", maxTokens);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
            Request request = new Request.Builder()
                    .url(AiConfig.VIVO_API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", AiConfig.authHeader())
                    .addHeader("Accept", "text/event-stream")
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "#02 chatCompletionStream HTTP " + response.code());
                    return null;
                }
                if (response.body() == null) return null;
                return readSseResponse(response);
            }
        } catch (Exception e) {
            Log.e(TAG, "#02 chatCompletionStream error: " + e.getMessage());
            return null;
        }
    }

    /**
     * #02 流式聊天补全 —— 逐行回调（适用于 UI 逐字输出场景）
     *
     * @param requestJson  已构建好的请求 JSON（含 stream: true）
     * @param lineCallback 每收到一行 SSE delta 时回调：onDelta(累积文本, 是否完成)
     * @return 完整回复文本，失败返回 null
     */
    public static String chatCompletionStreamCallback(JSONObject requestJson,
                                                      StreamLineCallback lineCallback) {
        RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
        Request request = new Request.Builder()
                .url(AiConfig.VIVO_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", AiConfig.authHeader())
                .addHeader("Accept", "text/event-stream")
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "#02 streamCallback HTTP " + response.code());
                return null;
            }
            if (response.body() == null) return null;

            StringBuilder fullReply = new StringBuilder();
            BufferedReader reader = new BufferedReader(response.body().charStream());
            String line;

            while ((line = reader.readLine()) != null) {
                String data = null;
                if (line.startsWith("data: ")) {
                    data = line.substring(6);
                } else if (line.startsWith("data:")) {
                    data = line.substring(5);
                }
                if (data == null) continue;
                if ("[DONE]".equals(data.trim())) break;

                try {
                    JSONObject json = new JSONObject(data);
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject delta = choice.optJSONObject("delta");
                        if (delta != null) {
                            String content = delta.optString("content", "");
                            if (!content.isEmpty()) {
                                fullReply.append(content);
                                lineCallback.onDelta(fullReply.toString(), false);
                            }
                        } else {
                            JSONObject message = choice.optJSONObject("message");
                            if (message != null) {
                                String content = message.optString("content", "");
                                if (!content.isEmpty()) {
                                    fullReply.append(content);
                                    lineCallback.onDelta(fullReply.toString(), false);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "#02 SSE parse error: " + e.getMessage());
                }
            }

            String finalReply = fullReply.toString();
            lineCallback.onDelta(finalReply, true);
            return finalReply;
        } catch (Exception e) {
            Log.e(TAG, "#02 streamCallback error: " + e.getMessage());
            return null;
        }
    }

    public interface StreamLineCallback {
        void onDelta(String accumulatedText, boolean isDone);
    }

    // ============================================================
    // #03 多模态诊断（文字+图片输入）
    // ============================================================
    //
    // 接口地址：POST https://api-ai.vivo.com.cn/v1/chat/completions
    // 协议：OpenAI 兼容多模态（content 为数组，含 text + image_url）
    // 模型：Volc-DeepSeek-V3.2
    //
    // 请求体示例：
    //   {
    //     "model": "Volc-DeepSeek-V3.2",
    //     "messages": [{
    //       "role": "user",
    //       "content": [
    //         {"type": "text", "text": "请分析这张照片..."},
    //         {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
    //       ]
    //     }],
    //     "stream": false,
    //     "temperature": 0.3,
    //     "max_tokens": 1500
    //   }
    //
    // 响应体：同 #01 格式
    //
    // 使用者：WorkOrderAiService.diagnoseFault(), VisionAnalysisService.analyze()
    // ============================================================

    /**
     * #03 多模态聊天补全（文字+图片）
     *
     * @param requestJson 已构建好的请求 JSON（含 messages 数组，content 为数组格式）
     * @return AI 返回的 content 文本，失败返回 null
     */
    public static String multimodalCompletion(JSONObject requestJson) {
        return multimodalCompletion(requestJson, 60);
    }

    /**
     * #03 多模态聊天补全（自定义超时）
     */
    public static String multimodalCompletion(JSONObject requestJson, int timeoutSec) {
        RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
        Request request = new Request.Builder()
                .url(AiConfig.VIVO_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", AiConfig.authHeader())
                .build();

        try (Response response = getHttpClient(timeoutSec).newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "(empty)";
                Log.e(TAG, "#03 multimodalCompletion HTTP " + response.code() + ": " + errBody);
                return null;
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            return extractContent(responseBody);
        } catch (Exception e) {
            Log.e(TAG, "#03 multimodalCompletion error: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // #04 视觉分析（复用 #03 多模态接口）
    // ============================================================
    //
    // 接口地址：同 #01/#03（https://api-ai.vivo.com.cn/v1/chat/completions）
    // 协议：OpenAI 兼容多模态
    // 模型：Volc-DeepSeek-V3.2（Config.VIVO_VISION_MODEL）
    //
    // 说明：视觉分析本质上就是 #03 多模态接口，
    //       使用 Config.VIVO_VISION_MODEL 模型，传入视频帧 base64。
    //       由 VisionAnalysisService 封装调用逻辑。
    //
    // 使用者：VisionAnalysisService.analyze()
    // ============================================================

    // ============================================================
    // #05 图片生成
    // ============================================================
    //
    // 接口地址：POST https://api-ai.vivo.com.cn/api/v1/image_generation
    //           ?module=aigc&request_id={uuid}&system_time={unix_seconds}
    // 协议：自定义 REST（非 OpenAI 兼容）
    // 模型：Doubao-Seedream-4.5
    // 认证：Bearer Token (Config.VIVO_APP_KEY)
    //
    // 请求体示例：
    //   {
    //     "model": "Doubao-Seedream-4.5",
    //     "prompt": "生成维修流程图...",
    //     "image": "data:image/jpeg;base64,...",   // 可选，0/1/多张
    //     "parameters": {
    //       "size": "1024x1024",
    //       "sequential_image_generation": "disabled"
    //     }
    //   }
    //
    // 响应体示例：
    //   {
    //     "code": 0,
    //     "data": {"image_urls": ["https://..."]},
    //     "message": "success"
    //   }
    //
    // 使用者：ImageGenerationService.generateImages()
    // ============================================================

    /**
     * #05 图片生成
     *
     * @param requestJson 已构建好的请求 JSON（含 model, prompt, image, parameters）
     * @return 完整响应体字符串，失败返回 null
     */
    public static String generateImage(JSONObject requestJson) {
        try {
            String requestId = java.util.UUID.randomUUID().toString();
            long systemTime = System.currentTimeMillis() / 1000;
            String url = AiConfig.VIVO_IMAGE_GENERATION_URL
                    + "?module=" + AiConfig.VIVO_IMAGE_MODULE
                    + "&request_id=" + requestId
                    + "&system_time=" + systemTime;

            RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", AiConfig.authHeader())
                    .build();

            try (Response response = getHttpClient(AiConfig.IMAGE_GENERATION_TIMEOUT).newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "(empty)";
                    Log.e(TAG, "#05 generateImage HTTP " + response.code() + ": " + errBody);
                    return null;
                }
                return response.body() != null ? response.body().string() : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "#05 generateImage error: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // #06 OCR 文字识别
    // ============================================================
    //
    // 接口地址：POST https://api-ai.vivo.com.cn/api/v1/ocr
    //           ?module=ocr&request_id={uuid}&system_time={unix_seconds}
    // 协议：自定义 REST（非 OpenAI 兼容）
    // 模型：vivo-ocr-general
    // 认证：Bearer Token (Config.VIVO_APP_KEY)
    //
    // 请求体示例：
    //   {
    //     "model": "vivo-ocr-general",
    //     "image": "data:image/jpeg;base64,..."
    //   }
    //
    // 响应体示例：
    //   {
    //     "code": 0,
    //     "data": {
    //       "text": "识别到的文字内容",
    //       "confidence": 0.95
    //     }
    //   }
    //
    // 使用者：VivoOcrService.recognize()
    // ============================================================

    /**
     * #06 OCR 文字识别
     *
     * @param base64Image 图片 Base64 编码（不含 data URI 前缀）
     * @return 识别到的文字，失败返回 null
     */
    public static String recognizeText(String base64Image) {
        try {
            String requestId = java.util.UUID.randomUUID().toString();
            String businessId = AiConfig.OCR_BUSINESS_ID_FULL;

            // 构建 query 参数
            HttpUrl url = HttpUrl.parse(AiConfig.VIVO_OCR_URL).newBuilder()
                    .addQueryParameter("requestId", requestId)
                    .build();

            // 构建 form-urlencoded body
            RequestBody body = new FormBody.Builder()
                    .add("image", base64Image)
                    .add("pos", "0")
                    .add("businessid", businessId)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", AiConfig.authHeader())
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = getHttpClient((int) AiConfig.OCR_TIMEOUT).newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "#06 recognizeText HTTP " + response.code());
                    return null;
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject respJson = new JSONObject(responseBody);
                int code = respJson.optInt("code", -1);
                if (code != 0) {
                    Log.e(TAG, "#06 OCR API error: code=" + code);
                    return null;
                }
                JSONObject data = respJson.optJSONObject("data");
                return data != null ? data.optString("text", "") : null;
            }
        } catch (Exception e) {
            Log.e(TAG, "#06 recognizeText error: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // #07 实时短语音识别 (ASR) — WebSocket 协议
    // ============================================================
    //
    // 连接地址：ws://api-ai.vivo.com.cn/asr/v2
    //           ?engineid=shortasrinput
    //           &system_time={unix_ms}
    //           &user_id={user_id}
    //           &requestId={uuid}
    // 协议：WebSocket（v2 接口）
    // 音频参数：PCM 16kHz 16bit 单声道
    // 最大识别时长：60 秒
    //
    // 握手流程：
    //   客户端 → 服务端：{"type":"started","request_id":"...","asr_info":{...}}
    //   服务端 → 客户端：{"action":"started","code":0,"sid":"..."}
    //
    // 数据传输：
    //   客户端 → 服务端：[binary] PCM 音频帧（每帧 1280 字节 = 40ms）
    //   客户端 → 服务端：[binary] "--end--" 标记（录音结束）
    //
    // 结果响应：
    //   {"action":"result","type":"asr","data":{"text":"识别结果","is_last":false}}
    //   {"action":"result","type":"asr","data":{"text":"最终结果","is_last":true}}
    //
    // 关闭：发送 "--close--" 标记，然后 close(1000, "session ended")
    //
    // 使用者：VivoAsrService
    // ============================================================

    // ============================================================
    // #08 语音合成 (TTS) — WebSocket 协议
    // ============================================================
    //
    // 连接地址：wss://api-ai.vivo.com.cn/tts
    //           ?engineid=tts_humanoid_lam
    //           &system_time={unix_seconds}
    //           &user_id={user_id}
    //           &requestId={uuid}
    // 协议：WebSocket
    // 输出格式：PCM 24kHz 16bit 单声道（需转 WAV 播放）
    //
    // 合成请求：
    //   {
    //     "aue": 0,                              // PCM 格式
    //     "auf": "audio/L16;rate=24000",         // 24kHz 采样率
    //     "vcn": "M24",                          // 音色（俊朗男声）
    //     "speed": 50,                           // 语速 0-100
    //     "volume": 50,                          // 音量 0-100
    //     "text": "base64编码的文本",              // 待合成文本
    //     "encoding": "utf8",
    //     "reqId": {timestamp}
    //   }
    //
    // 响应：
    //   {"error_code": 0, "data": {"status": 0, "audio": "base64音频数据"}}
    //   status: 0=开始合成, 2=合成完成
    //   audio: Base64 编码的 PCM 音频数据块
    //
    // 音色选项：
    //   M24  - 俊朗男声（当前使用）
    //   M193 - 理性男声
    //   F245_natural - 知性柔美
    //
    // 使用者：VivoTtsService
    // ============================================================

    // ==================== 公共工具方法 ====================

    /**
     * 从 OpenAI 兼容格式的响应中提取 content
     *
     * 支持两种响应格式：
     * 1. choices[0].message.content（标准格式）
     * 2. choices[0].delta.content（流式聚合格式）
     *
     * @param responseBody 完整响应体 JSON 字符串
     * @return 提取的 content 文本，提取失败返回 null
     */
    public static String extractContent(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                // 优先：message.content（非流式标准格式）
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    String content = message.optString("content", "").trim();
                    if (!content.isEmpty()) return content;
                }
                // 兼容：delta.content（流式聚合格式）
                JSONObject delta = firstChoice.optJSONObject("delta");
                if (delta != null) {
                    String content = delta.optString("content", "").trim();
                    if (!content.isEmpty()) return content;
                }
            }
            // 降级：直接读取顶层 content 字段
            String content = json.optString("content", "").trim();
            if (!content.isEmpty()) return content;
            return null;
        } catch (Exception e) {
            Log.e(TAG, "extractContent error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 读取 SSE 流式响应，累积所有 delta 内容为完整文本
     *
     * @param response OkHttp Response 对象（body 为 SSE 流）
     * @return 累积的完整回复文本
     */
    public static String readSseResponse(Response response) throws IOException {
        StringBuilder fullReply = new StringBuilder();
        if (response.body() == null) return fullReply.toString();

        BufferedReader reader = new BufferedReader(response.body().charStream());
        String line;
        while ((line = reader.readLine()) != null) {
            String data = null;
            if (line.startsWith("data: ")) {
                data = line.substring(6);
            } else if (line.startsWith("data:")) {
                data = line.substring(5);
            }
            if (data == null) continue;
            if ("[DONE]".equals(data.trim())) break;

            try {
                JSONObject json = new JSONObject(data);
                JSONArray choices = json.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta != null) {
                        String content = delta.optString("content", "");
                        if (!content.isEmpty()) fullReply.append(content);
                    } else {
                        JSONObject message = choice.optJSONObject("message");
                        if (message != null) {
                            String content = message.optString("content", "");
                            if (!content.isEmpty()) fullReply.append(content);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "readSseResponse parse error: " + e.getMessage());
            }
        }
        return fullReply.toString();
    }

    /**
     * 清理 AI 返回的 JSON 字符串（去除 markdown 代码块标记）
     *
     * @param jsonStr 可能包含 ```json...``` 包裹的 JSON 字符串
     * @return 清理后的纯 JSON 字符串
     */
    public static String cleanJson(String jsonStr) {
        String s = jsonStr.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }

    /**
     * 从文本中提取 JSON 对象（处理 AI 可能在 JSON 前后添加说明文字的情况）
     *
     * @param text 包含 JSON 的文本
     * @return 提取的 JSON 对象，提取失败返回 null
     */
    public static JSONObject extractJsonObject(String text) {
        if (text == null || text.isEmpty()) return null;
        String cleaned = cleanJson(text);
        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");
        if (start < 0 || end <= start) return null;
        try {
            return new JSONObject(cleaned.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }
}
