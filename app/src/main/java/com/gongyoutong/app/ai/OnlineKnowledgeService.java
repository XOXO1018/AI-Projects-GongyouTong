package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * 在线知识服务 —— 通过 LLM 生成维修专业知识文章
 * 每日推荐 5 条，点击可阅读 HTML 全文，可添加到本地知识库
 */
public class OnlineKnowledgeService {

    private static final String TAG = "OnlineKnowledgeService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private static volatile OnlineKnowledgeService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    /** 维修主题池（每日从中选 5 个） */
    public static final String[] TOPIC_POOL = {
        "空调不制冷三步排查法",
        "洗衣机漏水原因与维修",
        "冰箱冷藏室结冰处理",
        "燃气热水器点火故障检修",
        "油烟机吸力不足清洁保养",
        "燃气灶打不着火处理流程",
        "家庭电路跳闸安全排查",
        "水管漏水的紧急处理方法",
        "马桶堵塞专业疏通技巧",
        "墙面插座更换安全规范",
        "空调移机标准操作流程",
        "洗衣机滚筒异响诊断",
        "冰箱压缩机更换要点",
        "电热水器漏电保护检查",
        "油烟机电机更换步骤详解",
        "下水道堵塞专业疏通方案",
        "空调加氟操作与注意事项",
        "净水器滤芯更换周期指南",
        "智能门锁安装调试教程",
        "暖气片不热故障排除法",
    };

    private OnlineKnowledgeService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static OnlineKnowledgeService getInstance() {
        if (sInstance == null) {
            synchronized (OnlineKnowledgeService.class) {
                if (sInstance == null) sInstance = new OnlineKnowledgeService();
            }
        }
        return sInstance;
    }

    // ==================== 数据模型 ====================

    public static class OnlineKnowledgeItem {
        private String id;
        private String title;
        private String category;
        private String summary;       // 卡片摘要
        private String htmlContent;   // 完整 HTML（WebView 阅读用）
        private String keyPoints;
        private String toolsRequired;
        private String safetyNote;
        private boolean added;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getHtmlContent() { return htmlContent; }
        public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
        public String getKeyPoints() { return keyPoints; }
        public void setKeyPoints(String keyPoints) { this.keyPoints = keyPoints; }
        public String getToolsRequired() { return toolsRequired; }
        public void setToolsRequired(String toolsRequired) { this.toolsRequired = toolsRequired; }
        public String getSafetyNote() { return safetyNote; }
        public void setSafetyNote(String safetyNote) { this.safetyNote = safetyNote; }
        public boolean isAdded() { return added; }
        public void setAdded(boolean added) { this.added = added; }
    }

    // ==================== 回调 ====================

    public interface RecommendCallback {
        void onSuccess(List<OnlineKnowledgeItem> items);
        void onError(String msg);
    }

    public interface SaveCallback {
        void onSuccess(String title, String summary, String mindMapJson);
        void onError(String msg);
    }

    // ==================== 每日推荐（5 条） ====================

    public void getDailyRecommendations(int dayOfYear, RecommendCallback callback) {
        executor.execute(() -> {
            try {
                // 基于日期从池中选出 5 个主题
                java.util.Random rng = new java.util.Random(dayOfYear);
                List<Integer> poolIndices = new ArrayList<>();
                for (int i = 0; i < TOPIC_POOL.length; i++) poolIndices.add(i);

                String[] topics = new String[5];
                for (int i = 0; i < 5; i++) {
                    int pick = rng.nextInt(poolIndices.size());
                    topics[i] = TOPIC_POOL[poolIndices.remove(pick)];
                }

                // 为每个主题调用 LLM
                List<OnlineKnowledgeItem> allItems = new ArrayList<>();
                String systemPrompt = buildKnowledgeSystemPrompt();

                for (int i = 0; i < topics.length; i++) {
                    try {
                        String json = callLlm(systemPrompt,
                                "请为以下维修主题生成专业知识文章：" + topics[i]);
                        if (json != null) {
                            OnlineKnowledgeItem item = parseSingleArticle(json, topics[i]);
                            if (item != null) {
                                item.setId(UUID.randomUUID().toString());
                                allItems.add(item);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "getDailyRecommendations partial fail for " + topics[i]);
                        allItems.add(createFallbackItem(topics[i]));
                    }
                }

                final List<OnlineKnowledgeItem> result = allItems;
                mainHandler.post(() -> callback.onSuccess(result));

            } catch (Exception e) {
                Log.e(TAG, "getDailyRecommendations error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("获取推荐失败"));
            }
        });
    }

    /**
     * 将在线文章存入本地知识库 —— AI 生成详细总结和思维导图
     */
    public void generateSummaryAndMindMap(String title, String rawContent, SaveCallback callback) {
        executor.execute(() -> {
            try {
                String systemPrompt =
                    "你是专业的维修知识整理助手。将用户提供的维修知识文章整理为：\n"
                    + "1. 一段 150-200 字的精炼总结\n"
                    + "2. 一个思维导图结构（JSON 树形）\n\n"
                    + "必须严格返回以下 JSON 格式，不要添加任何其他文字：\n"
                    + "{\n"
                    + "  \"summary\": \"知识总结内容（150-200字）\",\n"
                    + "  \"mindMap\": {\n"
                    + "    \"root\": \"中心主题\",\n"
                    + "    \"children\": [\n"
                    + "      {\"text\": \"一级分支1\", \"children\": [\n"
                    + "        {\"text\": \"二级要点1\"},\n"
                    + "        {\"text\": \"二级要点2\"}\n"
                    + "      ]},\n"
                    + "      {\"text\": \"一级分支2\", \"children\": []}\n"
                    + "    ]\n"
                    + "  }\n"
                    + "}";

                String json = callLlm(systemPrompt,
                        "请为以下知识文章生成总结和思维导图：\n\n" + rawContent);
                if (json == null) {
                    mainHandler.post(() -> callback.onError("AI 未返回结果"));
                    return;
                }

                String cleaned = cleanJson(json);
                JSONObject obj = new JSONObject(cleaned);
                String summary = obj.optString("summary", "");
                JSONObject mindMap = obj.optJSONObject("mindMap");
                String mindMapJson = mindMap != null ? mindMap.toString() : "";

                mainHandler.post(() -> callback.onSuccess(title, summary, mindMapJson));

            } catch (Exception e) {
                Log.e(TAG, "generateSummaryAndMindMap error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("总结生成失败"));
            }
        });
    }

    // ==================== LLM 调用 ====================

    private String callLlm(String systemPrompt, String userPrompt) throws Exception {
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", AiConfig.VIVO_MODEL);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        requestJson.put("messages", messages);
        requestJson.put("stream", false);
        requestJson.put("temperature", 0.5);
        requestJson.put("max_tokens", 3000);

        RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
        Request request = new Request.Builder()
                .url(AiConfig.VIVO_API_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", AiConfig.authHeader())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "LLM HTTP " + response.code());
                return null;
            }
            String bodyStr = response.body() != null ? response.body().string() : "";
            return extractContent(bodyStr);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null) {
                    String c = msg.optString("content", "").trim();
                    if (!c.isEmpty()) return c;
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "extractContent error: " + e.getMessage());
            return null;
        }
    }

    // ==================== System Prompt ====================

    private String buildKnowledgeSystemPrompt() {
        return "你是资深家电/家居维修专家，有 20 年一线经验。请为指定主题生成一篇详细专业文章，"
            + "同时生成对应的精美 HTML 页面版本。\n\n"
            + "必须严格返回以下 JSON 格式，不要添加任何其他文字：\n"
            + "{\n"
            + "  \"title\": \"文章标题（简洁有力）\",\n"
            + "  \"category\": \"分类（空调维修/水路维修/电路维修/厨卫维修/暖通维修/门窗维修/综合维修）\",\n"
            + "  \"summary\": \"文章摘要（80字以内，显示在卡片上）\",\n"
            + "  \"keyPoints\": \"核心要点（3-5条，中文逗号分隔）\",\n"
            + "  \"toolsRequired\": \"所需工具（逗号分隔）\",\n"
            + "  \"safetyNote\": \"最重要的安全提醒（1-2句话）\",\n"
            + "  \"htmlContent\": \"完整的HTML文章内容，使用h2、p、ul、li、strong等标签，"
            + "带有橙色主题CSS内联样式，适合移动端阅读，500-800字\"\n"
            + "}\n\n"
            + "htmlContent 要求：用完整HTML格式(CSS内联在style标签中)，"
            + "橙色(#F97316)为主题色，白色背景，黑色正文(#1C1917)，"
            + "有标题、步骤、要点、工具清单、安全提示等段落。";
    }

    // ==================== 解析 ====================

    private OnlineKnowledgeItem parseSingleArticle(String content, String fallbackTopic) {
        try {
            String json = cleanJson(content);
            JSONObject obj = new JSONObject(json);
            OnlineKnowledgeItem item = new OnlineKnowledgeItem();
            item.setId(UUID.randomUUID().toString());
            item.setTitle(obj.optString("title", fallbackTopic));
            item.setCategory(obj.optString("category", "综合维修"));
            item.setSummary(obj.optString("summary", ""));
            item.setKeyPoints(obj.optString("keyPoints", ""));
            item.setToolsRequired(obj.optString("toolsRequired", ""));
            item.setSafetyNote(obj.optString("safetyNote", ""));
            item.setHtmlContent(obj.optString("htmlContent", wrapAsHtml(fallbackTopic, content)));
            item.setAdded(false);
            return item;
        } catch (Exception e) {
            Log.w(TAG, "parseSingleArticle fallback: " + e.getMessage());
            return createFallbackItem(fallbackTopic);
        }
    }

    private OnlineKnowledgeItem createFallbackItem(String topic) {
        OnlineKnowledgeItem item = new OnlineKnowledgeItem();
        item.setId(UUID.randomUUID().toString());
        item.setTitle(topic);
        item.setCategory("综合维修");
        item.setSummary("请点击查看完整维修知识");
        item.setKeyPoints("安全第一，按步骤操作");
        item.setToolsRequired("根据实际情况准备");
        item.setSafetyNote("操作前务必切断电源/水源");
        item.setHtmlContent("<html><body style='padding:16px;font-size:16px;color:#1C1917;'>"
                + "<h2 style='color:#F97316;'>" + topic + "</h2>"
                + "<p>AI 正在生成中，请返回重试...</p></body></html>");
        item.setAdded(false);
        return item;
    }

    private String wrapAsHtml(String title, String content) {
        return "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
            + "<style>body{font-family:sans-serif;padding:16px;color:#1C1917;line-height:1.8;}"
            + "h2{color:#F97316;} h3{color:#F97316;} .step{background:#FFF7ED;padding:10px;"
            + "border-left:3px solid #F97316;margin:8px 0;} .warn{color:#DC2626;font-weight:bold;}"
            + "</style></head><body><h2>" + title + "</h2>"
            + "<p>" + (content != null ? content : "") + "</p></body></html>";
    }

    private String cleanJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
