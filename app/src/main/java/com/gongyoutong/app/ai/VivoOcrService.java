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

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * vivo 通用 OCR 文字识别服务
 *
 * 接口规格：
 *   POST http://api-ai.vivo.com.cn/ocr/general_recognition
 *   Content-Type: application/x-www-form-urlencoded
 *   Authorization: Bearer {AppKey}
 *
 * 支持参数：
 *   - pos: 0=仅文字, 1=文字+绝对坐标, 2=文字+相对坐标（默认）
 *   - fast_mode: true=仅正向文字但更快, false=支持旋转文字
 */
public class VivoOcrService {

    private static final String TAG = "VivoOcrService";

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

    // ==================== 数据模型 ====================

    /** OCR 识别结果 */
    public static class OcrResult {
        public final String words;
        public final int angle;
        public final List<OcrWord> wordList;

        public OcrResult(String words, int angle, List<OcrWord> wordList) {
            this.words = words;
            this.angle = angle;
            this.wordList = wordList;
        }
    }

    /** 单个识别文字及其坐标 */
    public static class OcrWord {
        public final String words;
        public final JSONObject location; // 仅 pos=1 或 pos=2 时有值

        public OcrWord(String words, JSONObject location) {
            this.words = words;
            this.location = location;
        }
    }

    // ==================== 回调接口 ====================

    /** 简単回调：仅返回拼接后的文本 */
    public interface OcrCallback {
        void onSuccess(String ocrText);
        void onError(String msg);
    }

    /** 增强回调：返回完整结构化结果 */
    public interface OcrResultCallback {
        void onSuccess(OcrResult result);
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * 识别图片中的文字（简单模式，仅返回文本）
     *
     * @param base64Image 图片 Base64 编码（不含 data URI 前缀）
     * @param callback    结果回调（主线程）
     */
    public void recognize(String base64Image, OcrCallback callback) {
        recognize(base64Image, 2, false, new OcrResultCallback() {
            @Override
            public void onSuccess(OcrResult result) {
                mainHandler.post(() -> callback.onSuccess(result.words));
            }

            @Override
            public void onError(String msg) {
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    /**
     * 识别图片中的文字（增强模式，返回完整结果）
     *
     * @param base64Image 图片 Base64 编码（不含 data URI 前缀）
     * @param pos         0=仅文字, 1=文字+绝对坐标, 2=文字+相对坐标
     * @param fastMode    true=仅正向文字但更快, false=支持旋转文字
     * @param callback    结果回调（主线程）
     */
    public void recognize(String base64Image, int pos, boolean fastMode, OcrResultCallback callback) {
        if (base64Image == null || base64Image.isEmpty()) {
            mainHandler.post(() -> callback.onError("图片数据为空"));
            return;
        }

        executor.execute(() -> {
            int maxRetry = AiConfig.OCR_MAX_RETRY;
            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                try {
                    OcrResult result = doOcrRequest(base64Image, pos, fastMode);
                    mainHandler.post(() -> callback.onSuccess(result));
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "OCR attempt " + (attempt + 1) + " failed: " + e.getMessage());
                    if (attempt == maxRetry) {
                        mainHandler.post(() -> callback.onError("OCR识别失败: " + e.getMessage()));
                    }
                }
            }
        });
    }

    /**
     * 执行 OCR 请求（同步方法，内部调用）
     */
    private OcrResult doOcrRequest(String base64Image, int pos, boolean fastMode) throws Exception {
        String businessId = fastMode ? AiConfig.OCR_BUSINESS_ID_FAST : AiConfig.OCR_BUSINESS_ID_FULL;
        String requestId = UUID.randomUUID().toString();

        // 构建 query 参数
        HttpUrl url = HttpUrl.parse(AiConfig.VIVO_OCR_URL).newBuilder()
                .addQueryParameter("requestId", requestId)
                .build();

        // 构建 form-urlencoded body
        RequestBody body = new FormBody.Builder()
                .add("image", base64Image)
                .add("pos", String.valueOf(pos))
                .add("businessid", businessId)
                .build();

        // 构建请求
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", AiConfig.authHeader())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        Log.d(TAG, "OCR 请求: requestId=" + requestId + ", pos=" + pos
                + ", fastMode=" + fastMode + ", imageLen=" + base64Image.length());

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() != 200) {
                String errBody = response.body() != null ? response.body().string() : "(empty)";
                throw new IOException("HTTP " + response.code() + ": " + errBody);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            Log.d(TAG, "OCR 响应: " + responseBody);

            JSONObject respJson = new JSONObject(responseBody);

            // 检查 error_code
            int errorCode = respJson.optInt("error_code", -1);
            if (errorCode == 1) {
                throw new Exception("OCR识别失败");
            } else if (errorCode == 2) {
                throw new Exception("图像错误，请检查图片格式或内容");
            } else if (errorCode != 0) {
                String errorMsg = respJson.optString("error_msg", "未知错误");
                throw new Exception("OCR错误: " + errorMsg);
            }

            // 解析 result
            JSONObject result = respJson.optJSONObject("result");
            if (result == null) {
                throw new Exception("OCR返回数据为空");
            }

            int angle = result.optInt("angle", 0);
            List<OcrWord> wordList = new ArrayList<>();
            StringBuilder textBuilder = new StringBuilder();

            if (pos == 0) {
                // pos=0: 仅返回 words 数组
                JSONArray wordsArray = result.optJSONArray("words");
                if (wordsArray != null) {
                    for (int i = 0; i < wordsArray.length(); i++) {
                        JSONObject wordObj = wordsArray.optJSONObject(i);
                        if (wordObj != null) {
                            String word = wordObj.optString("words", "");
                            if (!word.isEmpty()) {
                                wordList.add(new OcrWord(word, null));
                                if (textBuilder.length() > 0) textBuilder.append("\n");
                                textBuilder.append(word);
                            }
                        }
                    }
                }
            } else {
                // pos=1 或 pos=2: 返回 OCR 数组（含坐标）
                JSONArray ocrArray = result.optJSONArray("OCR");
                if (ocrArray != null) {
                    for (int i = 0; i < ocrArray.length(); i++) {
                        JSONObject ocrObj = ocrArray.optJSONObject(i);
                        if (ocrObj != null) {
                            String word = ocrObj.optString("words", "");
                            JSONObject location = ocrObj.optJSONObject("location");
                            if (!word.isEmpty()) {
                                wordList.add(new OcrWord(word, location));
                                if (textBuilder.length() > 0) textBuilder.append("\n");
                                textBuilder.append(word);
                            }
                        }
                    }
                }
            }

            String text = textBuilder.toString();
            if (text.isEmpty()) {
                text = "（未识别到文字）";
            }

            Log.d(TAG, "OCR 成功: angle=" + angle + ", words=" + wordList.size()
                    + ", textLen=" + text.length());

            return new OcrResult(text, angle, wordList);
        }
    }
}
