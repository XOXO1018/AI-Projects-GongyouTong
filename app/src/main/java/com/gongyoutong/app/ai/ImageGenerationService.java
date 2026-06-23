package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.repair.ImageGenerationResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI 图片生成服务
 * 调用 vivo 蓝心大模型图片生成 API，根据设备照片和故障描述生成维修流程图
 */
public class ImageGenerationService {

    private static final String TAG = "ImageGenerationService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile ImageGenerationService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private ImageGenerationService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(AiConfig.IMAGE_GENERATION_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全）
     */
    public static ImageGenerationService getInstance() {
        if (sInstance == null) {
            synchronized (ImageGenerationService.class) {
                if (sInstance == null) {
                    sInstance = new ImageGenerationService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    /**
     * 图片生成回调
     */
    public interface ImageGenerationCallback {
        /** 生成成功，返回图片 URL 列表 */
        void onSuccess(List<String> imageUrls);

        /** 生成失败 */
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * 根据故障描述和设备照片生成维修流程图
     *
     * @param prompt          故障描述文本（用于构造生成提示词）
     * @param photoBase64List 设备照片 Base64 编码列表（可为空）
     * @param callback        结果回调（主线程）
     */
    public void generateImages(String prompt, List<String> photoBase64List,
                               ImageGenerationCallback callback) {
        if (prompt == null || prompt.isEmpty()) {
            mainHandler.post(() -> callback.onError("故障描述不能为空"));
            return;
        }

        executor.execute(() -> {
            int retryCount = 0;
            Exception lastException = null;

            while (retryCount <= AiConfig.MAX_IMAGE_RETRY) {
                try {
                    if (retryCount > 0) {
                        Log.d(TAG, "图片生成重试 " + retryCount + "/" + AiConfig.MAX_IMAGE_RETRY);
                        // 检测是否为频率限制错误，使用更长的退避时间
                        boolean isRateLimit = isRateLimitError(lastException);
                        long waitMs = isRateLimit
                                ? (long) Math.pow(2, retryCount) * 3000L  // 3s, 6s, 12s, 24s
                                : retryCount * 2000L;                      // 2s, 4s, 6s, 8s
                        Log.d(TAG, "等待 " + waitMs + "ms 后重试"
                                + (isRateLimit ? "（频率限制退避）" : ""));
                        Thread.sleep(waitMs);
                    }

                    // 1. 构造 URL（含查询参数）
                    String requestId = UUID.randomUUID().toString();
                    long systemTime = System.currentTimeMillis() / 1000;
                    String url = AiConfig.VIVO_IMAGE_GENERATION_URL
                            + "?module=" + AiConfig.VIVO_IMAGE_MODULE
                            + "&request_id=" + requestId
                            + "&system_time=" + systemTime;

                    // 2. 构造请求体
                    JSONObject requestJson = new JSONObject();
                    requestJson.put("model", AiConfig.VIVO_IMAGE_MODEL);
                    requestJson.put("prompt", prompt);

                    // image 字段：0张不传，1张传字符串，2+张传数组
                    if (photoBase64List != null && !photoBase64List.isEmpty()) {
                        // 过滤空值
                        List<String> validImages = new ArrayList<>();
                        for (String base64 : photoBase64List) {
                            if (base64 != null && !base64.isEmpty()) {
                                validImages.add("data:image/jpeg;base64," + base64);
                            }
                        }
                        if (!validImages.isEmpty()) {
                            if (validImages.size() == 1) {
                                // 单张：传字符串
                                requestJson.put("image", validImages.get(0));
                            } else {
                                // 多张：传数组
                                JSONArray imageArray = new JSONArray();
                                for (String img : validImages) {
                                    imageArray.put(img);
                                }
                                requestJson.put("image", imageArray);
                            }
                        }
                    }
                    // 0 张照片时不传 image 字段

                    // 参数
                    JSONObject parameters = new JSONObject();
                    parameters.put("size", AiConfig.DEFAULT_IMAGE_SIZE);
                    requestJson.put("parameters", parameters);

                    // 3. 发送请求
                    RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                    Request request = new Request.Builder()
                            .url(url)
                            .post(body)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Authorization", AiConfig.authHeader())
                            .build();

                    Log.d(TAG, "图片生成请求: requestId=" + requestId
                            + ", prompt长度=" + prompt.length()
                            + ", 图片数=" + (photoBase64List != null ? photoBase64List.size() : 0));

                    try (Response response = httpClient.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            int httpCode = response.code();
                            String errBody = response.body() != null ? response.body().string() : "(empty)";
                            Log.e(TAG, "图片生成 HTTP " + httpCode + ": " + errBody);
                            
                            // HTTP 429 频率限制
                            if (httpCode == 429 || errBody.toLowerCase().contains("ratelimit")) {
                                lastException = new IOException("请求过于频繁，请稍后重试");
                            } else {
                                lastException = new IOException("HTTP " + httpCode);
                            }
                            retryCount++;
                            continue;
                        }

                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "图片生成响应: " + responseBody);

                        // 4. 解析响应
                        ImageGenerationResult result = ImageGenerationResult.fromJson(responseBody);
                        if (result == null) {
                            Log.w(TAG, "图片生成响应解析失败");
                            lastException = new Exception("响应解析失败");
                            retryCount++;
                            continue;
                        }

                        if (!result.isSuccess()) {
                            Log.e(TAG, "图片生成 API 返回错误: code=" + result.getCode()
                                    + ", message=" + result.getMessage());
                            lastException = new Exception("API 返回错误: " + result.getMessage());
                            retryCount++;
                            continue;
                        }

                        List<String> imageUrls = result.getImageUrls();
                        if (imageUrls.isEmpty()) {
                            Log.w(TAG, "图片生成成功但无图片返回");
                            lastException = new Exception("未生成任何图片");
                            retryCount++;
                            continue;
                        }

                        Log.d(TAG, "图片生成成功, 共 " + imageUrls.size() + " 张图片");
                        mainHandler.post(() -> callback.onSuccess(imageUrls));
                        return;
                    }

                } catch (IOException e) {
                    Log.e(TAG, "图片生成网络错误: " + e.getMessage());
                    lastException = e;
                    retryCount++;
                } catch (InterruptedException e) {
                    Log.e(TAG, "图片生成重试中断: " + e.getMessage());
                    lastException = e;
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "图片生成错误: " + e.getMessage());
                    lastException = e;
                    retryCount++;
                }
            }

            // 所有重试均失败
            final String errorMsg;
            if (isRateLimitError(lastException)) {
                errorMsg = "请求过于频繁，请等待 30 秒后重试";
            } else if (lastException instanceof IOException) {
                errorMsg = "网络连接失败，请检查网络后重试";
            } else if (lastException != null) {
                errorMsg = "图片生成失败: " + lastException.getMessage();
            } else {
                errorMsg = "图片生成失败，请重试";
            }
            mainHandler.post(() -> callback.onError(errorMsg));
        });
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断异常是否为频率限制错误
     */
    private boolean isRateLimitError(Exception e) {
        if (e == null || e.getMessage() == null) return false;
        String msg = e.getMessage().toLowerCase();
        return msg.contains("ratelimit")
                || msg.contains("rate limit")
                || msg.contains("频率限制")
                || msg.contains("过于频繁");
    }
}
