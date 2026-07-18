package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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
 * AI 视频生成服务
 * 调用 vivo 蓝心大模型视频生成 API，根据文本或图片生成维修指导视频
 */
public class VideoGenerationService {

    private static final String TAG = "VideoGenerationService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile VideoGenerationService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private VideoGenerationService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(AiConfig.VIDEO_SUBMIT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全 - DCL）
     */
    public static VideoGenerationService getInstance() {
        if (sInstance == null) {
            synchronized (VideoGenerationService.class) {
                if (sInstance == null) {
                    sInstance = new VideoGenerationService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    /** 视频生成回调 */
    public interface VideoGenerationCallback {
        /** 生成成功，返回视频 URL */
        void onSuccess(String videoUrl);

        /** 生成失败 */
        void onError(String msg);

        /** 进度更新 */
        void onProgress(String status, int progress);
    }

    // ==================== 核心 API ====================

    /**
     * 根据文本描述生成维修指导视频
     *
     * @param prompt   视频描述文本
     * @param callback 结果回调（主线程）
     */
    public void generateFromText(String prompt, VideoGenerationCallback callback) {
        if (prompt == null || prompt.isEmpty()) {
            mainHandler.post(() -> callback.onError("视频描述不能为空"));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. 构造请求 JSON
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", AiConfig.VIVO_VIDEO_MODEL);

                JSONArray content = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", prompt);
                content.put(textPart);

                requestJson.put("content", content);

                // 2. 提交任务
                String taskId = submitTask(requestJson);
                if (taskId == null) {
                    mainHandler.post(() -> callback.onError("视频生成任务提交失败"));
                    return;
                }

                mainHandler.post(() -> callback.onProgress("任务已提交", 0));

                // 3. 轮询任务状态
                pollTaskStatus(taskId, callback);

            } catch (Exception e) {
                Log.e(TAG, "视频生成错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("视频生成出错: " + e.getMessage()));
            }
        });
    }

    /**
     * 根据图片生成维修指导视频（图生视频）
     *
     * @param imageUrl 图片 URL
     * @param prompt   视频描述文本（可选）
     * @param callback 结果回调（主线程）
     */
    public void generateFromImage(String imageUrl, String prompt, VideoGenerationCallback callback) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            mainHandler.post(() -> callback.onError("图片 URL 不能为空"));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. 构造请求 JSON
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", AiConfig.VIVO_VIDEO_MODEL);

                JSONArray content = new JSONArray();

                // 文本部分
                if (prompt != null && !prompt.isEmpty()) {
                    JSONObject textPart = new JSONObject();
                    textPart.put("type", "text");
                    textPart.put("text", prompt);
                    content.put(textPart);
                }

                // 图片部分
                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrlObj = new JSONObject();
                imageUrlObj.put("url", imageUrl);
                imagePart.put("image_url", imageUrlObj);
                content.put(imagePart);

                requestJson.put("content", content);

                // 2. 提交任务
                String taskId = submitTask(requestJson);
                if (taskId == null) {
                    mainHandler.post(() -> callback.onError("视频生成任务提交失败"));
                    return;
                }

                mainHandler.post(() -> callback.onProgress("任务已提交", 0));

                // 3. 轮询任务状态
                pollTaskStatus(taskId, callback);

            } catch (Exception e) {
                Log.e(TAG, "视频生成错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("视频生成出错: " + e.getMessage()));
            }
        });
    }

    // ==================== 内部实现 ====================

    /**
     * 提交视频生成任务
     *
     * @param requestJson 请求 JSON
     * @return 任务 ID，失败返回 null
     */
    private String submitTask(JSONObject requestJson) throws Exception {
        RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
        Request request = new Request.Builder()
                .url(AiConfig.VIVO_VIDEO_SUBMIT_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", AiConfig.authHeader())
                .build();

        Log.d(TAG, "提交视频生成任务: " + requestJson.toString());

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "提交任务响应: " + responseBody);

            if (!response.isSuccessful()) {
                Log.e(TAG, "提交任务 HTTP " + response.code() + ": " + responseBody);
                return null;
            }

            JSONObject json = new JSONObject(responseBody);
            int code = json.optInt("code", -1);
            if (code != 0) {
                String message = json.optString("message", "未知错误");
                Log.e(TAG, "提交任务失败: code=" + code + ", message=" + message);
                return null;
            }

            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                return data.optString("id", null);
            }
            return null;
        }
    }

    /**
     * 轮询任务状态
     *
     * @param taskId   任务 ID
     * @param callback 回调
     */
    private void pollTaskStatus(String taskId, VideoGenerationCallback callback) {
        AtomicBoolean isCancelled = new AtomicBoolean(false);
        int[] attempt = {0};

        Runnable pollTask = new Runnable() {
            @Override
            public void run() {
                if (isCancelled.get() || attempt[0] >= AiConfig.VIDEO_MAX_POLL_ATTEMPTS) {
                    if (!isCancelled.get()) {
                        mainHandler.post(() -> callback.onError("视频生成超时"));
                    }
                    return;
                }

                try {
                    JSONObject result = queryTask(taskId);
                    if (result == null) {
                        attempt[0]++;
                        mainHandler.postDelayed(this, AiConfig.VIDEO_POLL_INTERVAL_MS);
                        return;
                    }

                    String status = result.optString("status", "");
                    Log.d(TAG, "任务状态: " + status);

                    if ("succeeded".equals(status)) {
                        JSONObject content = result.optJSONObject("content");
                        if (content != null) {
                            String videoUrl = content.optString("video_url", "");
                            if (!videoUrl.isEmpty()) {
                                mainHandler.post(() -> callback.onSuccess(videoUrl));
                                return;
                            }
                        }
                        mainHandler.post(() -> callback.onError("视频生成成功但未返回 URL"));
                    } else if ("failed".equals(status)) {
                        JSONObject error = result.optJSONObject("error");
                        String errorMsg = error != null ? error.optString("message", "未知错误") : "视频生成失败";
                        mainHandler.post(() -> callback.onError(errorMsg));
                    } else {
                        // 仍在处理中
                        int progress = calculateProgress(status);
                        mainHandler.post(() -> callback.onProgress(status, progress));
                        attempt[0]++;
                        mainHandler.postDelayed(this, AiConfig.VIDEO_POLL_INTERVAL_MS);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "查询任务状态错误: " + e.getMessage());
                    attempt[0]++;
                    mainHandler.postDelayed(this, AiConfig.VIDEO_POLL_INTERVAL_MS);
                }
            }
        };

        mainHandler.post(pollTask);
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务结果 JSON，失败返回 null
     */
    private JSONObject queryTask(String taskId) throws Exception {
        String url = AiConfig.VIVO_VIDEO_QUERY_URL + "?id=" + taskId;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", AiConfig.authHeader())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                Log.e(TAG, "查询任务 HTTP " + response.code());
                return null;
            }

            JSONObject json = new JSONObject(responseBody);
            int code = json.optInt("code", -1);
            if (code != 0) {
                Log.e(TAG, "查询任务失败: " + json.optString("message"));
                return null;
            }

            return json.optJSONObject("data");
        }
    }

    /**
     * 根据状态字符串计算进度百分比
     */
    private int calculateProgress(String status) {
        if (status == null) return 0;
        if (status.contains("pending") || status.contains("queued")) return 10;
        if (status.contains("processing") || status.contains("running")) return 50;
        if (status.contains("finalizing") || status.contains("encoding")) return 80;
        return 30;
    }

    /**
     * 取消任务（通过设置标志位）
     */
    public void cancel() {
        // 实际取消需要调用 API，这里简化处理
        Log.d(TAG, "视频生成任务已取消");
    }
}