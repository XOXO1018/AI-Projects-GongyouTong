package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.data.Quotation;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * AI 报价生成服务
 */
public class QuotationAiService {
    private static final String TAG = "QuotationAiService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static volatile QuotationAiService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private QuotationAiService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static QuotationAiService getInstance() {
        if (sInstance == null) {
            synchronized (QuotationAiService.class) {
                if (sInstance == null) {
                    sInstance = new QuotationAiService();
                }
            }
        }
        return sInstance;
    }

    public interface QuotationCallback {
        void onSuccess(Quotation quotation);
        void onError(String msg);
    }

    /**
     * 生成报价单
     */
    public void generateQuotation(String faultDescription, String deviceType,
                                   List<String> partsUsed, QuotationCallback callback) {
        executor.execute(() -> {
            try {
                String prompt = buildQuotationPrompt(faultDescription, deviceType, partsUsed);

                JSONObject requestJson = new JSONObject();
                requestJson.put("model", AiConfig.VIVO_MODEL);

                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是维修报价专家。根据维修内容生成合理的报价单。必须返回JSON格式。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 1000);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(AiConfig.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", AiConfig.authHeader())
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("AI 服务暂时不可用"));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Quotation quotation = parseQuotationResponse(responseBody);

                    if (quotation != null) {
                        mainHandler.post(() -> callback.onSuccess(quotation));
                    } else {
                        mainHandler.post(() -> callback.onError("报价生成失败，请重试"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "generateQuotation error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络错误，请检查网络后重试"));
            }
        });
    }

    private String buildQuotationPrompt(String faultDescription, String deviceType,
                                         List<String> partsUsed) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下维修信息生成报价单：\n\n");
        prompt.append("设备类型：").append(deviceType != null ? deviceType : "未知").append("\n");
        prompt.append("故障描述：").append(faultDescription).append("\n");
        if (partsUsed != null && !partsUsed.isEmpty()) {
            prompt.append("使用的配件：").append(String.join("、", partsUsed)).append("\n");
        }
        prompt.append("\n请返回JSON格式：\n");
        prompt.append("{\"items\": [{\"name\": \"项目名称\", \"quantity\": 1, \"unitPrice\": 价格, \"category\": \"配件/人工/其他\"}], \"totalAmount\": 总价}\n");
        prompt.append("配件价格参考市场价，人工费根据工时合理定价。");
        return prompt.toString();
    }

    private Quotation parseQuotationResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.optJSONObject("message");
                if (message != null) {
                    String content = message.optString("content", "");
                    return parseQuotationJson(content);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseQuotationResponse error: " + e.getMessage());
        }
        return null;
    }

    private Quotation parseQuotationJson(String jsonStr) {
        try {
            String clean = jsonStr.replaceAll("```json", "").replaceAll("```", "").trim();
            int start = clean.indexOf("{");
            int end = clean.lastIndexOf("}");
            if (start < 0 || end < 0) return null;

            JSONObject json = new JSONObject(clean.substring(start, end + 1));
            Quotation quotation = new Quotation();
            quotation.setId(String.valueOf(System.currentTimeMillis()));
            quotation.setTotalAmount(json.optDouble("totalAmount", 0));
            quotation.setStatus("待确认");
            quotation.setCreatedAt(System.currentTimeMillis());

            List<Quotation.QuotationItem> items = new ArrayList<>();
            JSONArray itemsArray = json.optJSONArray("items");
            if (itemsArray != null) {
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject item = itemsArray.getJSONObject(i);
                    items.add(new Quotation.QuotationItem(
                            item.optString("name", ""),
                            item.optInt("quantity", 1),
                            item.optDouble("unitPrice", 0),
                            item.optString("category", "其他")
                    ));
                }
            }
            quotation.setItems(items);
            return quotation;
        } catch (Exception e) {
            Log.e(TAG, "parseQuotationJson error: " + e.getMessage());
            return null;
        }
    }
}
