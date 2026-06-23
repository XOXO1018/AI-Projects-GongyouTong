package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
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
 * vivo OCR 文字识别服务
 * 识别图片中的文字内容
 */
public class VivoOcrService {

    private static final String TAG = "VivoOcrService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 单例 */
    private static volatile VivoOcrService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private VivoOcrService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(AiConfig.OCR_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例（线程安全 - DCL）
     */
    public static VivoOcrService getInstance() {
        if (sInstance == null) {
            synchronized (VivoOcrService.class) {
                if (sInstance == null) {
                    sInstance = new VivoOcrService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    /** OCR 识别回调 */
    public interface OcrCallback {
        void onSuccess(String ocrText);
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * 识别图片中的文字
     *
     * @param base64Image 图片 Base64 编码（不含 data URI 前缀）
     * @param callback    结果回调（主线程）
     */
    public void recognize(String base64Image, OcrCallback callback) {
        if (base64Image == null || base64Image.isEmpty()) {
            mainHandler.post(() -> callback.onError("图片数据为空"));
            return;
        }

        executor.execute(() -> {
            try {
                // 1. 构造 URL（含查询参数）
                String requestId = UUID.randomUUID().toString();
                long systemTime = System.currentTimeMillis() / 1000;
                String url = AiConfig.VIVO_OCR_URL
                        + "?module=ocr"
                        + "&request_id=" + requestId
                        + "&system_time=" + systemTime;

                // 2. 构造请求体
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", AiConfig.VIVO_OCR_MODEL);
                requestJson.put("image", "data:image/jpeg;base64," + base64Image);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", AiConfig.authHeader())
                        .build();

                Log.d(TAG, "OCR 请求: requestId=" + requestId
                        + ", 图片长度=" + base64Image.length());

                // 3. 发送请求
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "OCR HTTP " + response.code() + ": " + errBody);
                        mainHandler.post(() -> callback.onError("文字识别失败，请稍后重试"));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "OCR 响应: " + responseBody);

                    // 4. 解析响应
                    // 格式：{"code": 0, "data": {"text": "识别到的文字", "confidence": 0.95}}
                    JSONObject respJson = new JSONObject(responseBody);
                    int code = respJson.optInt("code", -1);
                    if (code != 0) {
                        String msg = respJson.optString("message", "文字识别失败");
                        Log.e(TAG, "OCR API 错误: code=" + code + ", message=" + msg);
                        mainHandler.post(() -> callback.onError("文字识别失败：" + msg));
                        return;
                    }

                    JSONObject data = respJson.optJSONObject("data");
                    if (data == null) {
                        mainHandler.post(() -> callback.onError("文字识别返回数据为空"));
                        return;
                    }

                    String text = data.optString("text", "");
                    double confidence = data.optDouble("confidence", 0.0);

                    Log.d(TAG, "OCR 成功: text长度=" + text.length()
                            + ", confidence=" + confidence);

                    if (text.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess("（未识别到文字）"));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(text));
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "OCR 网络错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "OCR 错误: " + e.getMessage());
                mainHandler.post(() -> callback.onError("文字识别出错，请重试"));
            }
        });
    }
}
