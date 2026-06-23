package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.data.Schedule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI 服务 - 支持 vivo 蓝心大模型（OpenAI 兼容协议）
 * 用于分析用户输入的日程信息并生成个性化提醒
 */
public class VivoAiService {

    private static final String TAG = "VivoAiService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 【修复1】OkHttpClient 单例 + 合理超时
    private static volatile OkHttpClient sHttpClient;

    private static OkHttpClient getHttpClient() {
        if (sHttpClient == null) {
            synchronized (VivoAiService.class) {
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

    // Handler 延迟初始化
    private Handler getMainHandler() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }

    private Handler mainHandler;

    // ========== 日期时间正则表达式（全部 static final）==========
    private static final Pattern DATE_TODAY = Pattern.compile("今天|今日");
    private static final Pattern DATE_TOMORROW = Pattern.compile("明天|明日");
    private static final Pattern DATE_DAY_AFTER = Pattern.compile("后天");
    private static final Pattern DATE_DAY_AFTER_TOMORROW = Pattern.compile("大后天");
    private static final Pattern DATE_WEEK = Pattern.compile("本周|这周");
    private static final Pattern DATE_NEXT_WEEK = Pattern.compile("下周|下星期|下个星期");
    private static final Pattern DATE_NEXT_MONTH = Pattern.compile("下个月|下月");
    private static final Pattern WEEKDAY_PATTERN = Pattern.compile("(星期|周|礼拜)([一二三四五六日天1-7])");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("(下|下个)(星期|周|礼拜)?([一二三四五六日天1-7])");
    private static final Pattern DATE_MONTH_DAY = Pattern.compile("(\\d{1,2})[月/-](\\d{1,2})[日号]?");
    private static final Pattern DATE_FULL = Pattern.compile("(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})[日号]?");
    private static final Pattern DATE_MONTH_DAY_CN = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]?");
    private static final Pattern TIME_HOUR_MIN = Pattern.compile("(\\d{1,2})[点时:：](\\d{1,2})?");
    // 时间正则：支持 "下午五点二十"、"下午5点20"、"下午5:20" 等格式
    private static final Pattern TIME_AM_PM = Pattern.compile("(上午|下午|晚上|早上|早晨|中午|凌晨|傍晚)([一二三四五六七八九十百千万亿零]+|\\d{1,2})[点时:：]?([一二三四五六七八九十百千万亿零]+|\\d{1,2})?");
    private static final Pattern TIME_HALF = Pattern.compile("(\\d{1,2})[点时]半");
    private static final Pattern TIME_QUARTER = Pattern.compile("(\\d{1,2})[点时][一二三四五]刻");
    private static final Pattern TIME_ONLY_HOUR = Pattern.compile("(?<!\\d)([0-9]|1[0-9]|2[0-3])(点|点钟)(?!\\d)");
    private static final Pattern RELATIVE_HOUR = Pattern.compile("(\\d{1,2})个?小时(之)?后");
    private static final Pattern RELATIVE_MINUTE = Pattern.compile("(\\d{1,2})(分钟|分)(之)?后");
    private static final Pattern RELATIVE_HOUR_SHORT = Pattern.compile("(\\d{1,2})小时后");
    private static final Pattern RELATIVE_MINUTE_SHORT = Pattern.compile("(\\d{1,2})分钟后");

    // ========== 工作类型映射 ==========
    private static final String[] WORK_TYPES = {
        "拆装", "拆除", "加装", "安装", "维修", "疏通", "清洗",
        "检修", "更换", "换新", "调试", "检测", "保养",
        "搬运", "整理", "维护", "检查", "拆装", "移机", "新装"
    };

    private static final Map<String, String> WORK_TYPE_MAPPING = buildWorkTypeMapping();

    private static Map<String, String> buildWorkTypeMapping() {
        Map<String, String> m = new HashMap<>();
        m.put("拆除", "拆除服务");
        m.put("安装", "安装服务");
        m.put("加装", "加装服务");
        m.put("维修", "维修服务");
        m.put("疏通", "疏通服务");
        m.put("清洗", "清洗服务");
        m.put("检修", "检修服务");
        m.put("更换", "更换服务");
        m.put("换新", "换新服务");
        m.put("调试", "调试服务");
        m.put("检测", "检测服务");
        m.put("保养", "保养服务");
        m.put("搬运", "搬运服务");
        m.put("整理", "整理服务");
        m.put("维护", "维护服务");
        m.put("检查", "检查服务");
        m.put("拆装", "拆装服务");
        m.put("移机", "拆装服务");
        m.put("新装", "安装服务");
        return m;
    }

    // ========== 设备关键词 ==========
    private static final String[] DEVICE_KEYWORDS = {
        "热水器", "空调", "洗衣机", "冰箱", "电视", "油烟机",
        "燃气灶", "水表", "电路", "灶具", "马桶", "窗户",
        "下水道", "排水管", "灯具", "插座", "开关", "水龙头"
    };

    private static final Map<String, String[]> DEVICE_TOOLS_MAP = buildDeviceToolsMap();

    private static Map<String, String[]> buildDeviceToolsMap() {
        Map<String, String[]> m = new HashMap<>();
        m.put("热水器", new String[]{"扳手套装", "生料带", "密封胶", "测电笔", "卷尺"});
        m.put("空调", new String[]{"扳手套装", "螺丝刀组", "制冷剂", "卷尺", "梯子"});
        m.put("洗衣机", new String[]{"扳手套装", "螺丝刀组", "清洁剂", "测电笔"});
        m.put("油烟机", new String[]{"清洁刷", "清洁剂", "螺丝刀组", "防护手套"});
        m.put("燃气灶", new String[]{"扳手套装", "肥皂水", "打火机", "生料带"});
        m.put("下水道", new String[]{"管道疏通器", "疏通剂", "橡胶手套", "水桶"});
        m.put("水管", new String[]{"管钳", "生料带", "密封胶", "扳手"});
        m.put("电路", new String[]{"测电笔", "万用表", "螺丝刀组", "绝缘胶带"});
        m.put("灯具", new String[]{"测电笔", "螺丝刀组", "梯子", "防护手套"});
        return m;
    }

    // ========== 回调接口 ==========
    public interface AiCallback {
        void onSuccess(Schedule schedule, String aiReminder, AiSource source);
        void onError(String errorMessage);
    }

    public interface ReminderCallback {
        void onSuccess(String reminder, AiSource source);
        void onError(String errorMessage);
    }

    public enum AiSource {
        CLOUD_AI_SUCCESS,
        LOCAL_FALLBACK,
        LOCAL_ONLY
    }

    private String getApiUrl() {
        return AiConfig.VIVO_API_URL;
    }

    private String getApiKey() {
        return AiConfig.VIVO_APP_KEY;
    }

    private String getModel() {
        return AiConfig.VIVO_MODEL;
    }

    // 线程池用于异步任务
    private static final java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newFixedThreadPool(2);

    // ========== 对话记忆 ==========
    public static class ChatMessage {
        public final String role;    // "user" or "assistant"
        public final String content;
        public final long timestamp;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 20; // 最多保留 20 轮对话

    public void addToHistory(String role, String content) {
        chatHistory.add(new ChatMessage(role, content));
        if (chatHistory.size() > MAX_HISTORY * 2) {
            // 保留最近的 MAX_HISTORY 轮，去掉最旧的
            chatHistory.subList(0, chatHistory.size() - MAX_HISTORY * 2).clear();
        }
    }

    public void clearHistory() {
        chatHistory.clear();
    }

    public List<ChatMessage> getHistory() {
        return new ArrayList<>(chatHistory);
    }

    /**
     * 生成 AI 个性化提醒（异步，带 fallback）
     */
    public void generateReminderAsync(String title, String workType, String address,
                                      ReminderCallback callback) {
        String prompt = buildReminderPrompt(
            title != null ? title : "",
            workType != null ? workType : "",
            address != null ? address : ""
        );

        Log.d(TAG, "generateReminderAsync: calling " + getApiUrl());

        executor.execute(() -> {
            try {
                // OpenAI 兼容格式请求体
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", getModel());

                org.json.JSONArray messages = new org.json.JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一个专业的家政服务管家，专门为维修师傅提供出发前准备建议。请用中文回答，语气专业友好。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 800);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(getApiUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + getApiKey())
                        .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = "(empty)";
                        if (response.body() != null) {
                            errBody = response.body().string();
                        }
                        Log.w(TAG, "generateReminderAsync failed HTTP " + response.code() + ": " + errBody);
                        fallbackReminder(title, workType, callback);
                        return;
                    }

                    String responseBody = "";
                    if (response.body() != null) {
                        responseBody = response.body().string();
                    }
                    Log.d(TAG, "generateReminderAsync raw response length=" + responseBody.length());

                    String content = extractContentFromResponse(responseBody);

                    if (content == null || content.isEmpty()) {
                        Log.w(TAG, "generateReminderAsync got empty content");
                        fallbackReminder(title, workType, callback);
                        return;
                    }

                    final String finalContent = content;
                    getMainHandler().post(() -> callback.onSuccess(finalContent, AiSource.CLOUD_AI_SUCCESS));
                }

            } catch (IOException e) {
                Log.e(TAG, "generateReminderAsync network error: " + e.getMessage());
                fallbackReminder(title, workType, callback);
            } catch (Exception e) {
                Log.e(TAG, "generateReminderAsync error: " + e.getMessage());
                fallbackReminder(title, workType, callback);
            }
        });
    }

    private void fallbackReminder(String title, String workType, ReminderCallback callback) {
        Schedule tmp = new Schedule();
        tmp.setTitle(title);
        tmp.setWorkType(workType != null ? workType : "综合服务");
        String reminder = generatePersonalizedReminder(tmp, title);
        getMainHandler().post(() -> callback.onSuccess(reminder, AiSource.LOCAL_FALLBACK));
    }

    private void generateReminderWithCloudAi(Schedule schedule, AiCallback callback, AiSource source) {
        String title = schedule.getTitle() != null ? schedule.getTitle() : "";
        String workType = schedule.getWorkType() != null ? schedule.getWorkType() : "";
        String address = schedule.getAddress() != null ? schedule.getAddress() : "";

        String prompt = buildReminderPrompt(title, workType, address);

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("model", getModel());

            org.json.JSONArray messages = new org.json.JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个专业的家政服务管家，专门为维修师傅提供出发前准备建议。请用中文回答，语气专业友好。");
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);

            requestJson.put("messages", messages);
            requestJson.put("stream", false);
            requestJson.put("temperature", 0.3);
            requestJson.put("max_tokens", 800);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build reminder request: " + e.getMessage());
            String reminder = generatePersonalizedReminder(schedule, title);
            getMainHandler().post(() -> callback.onSuccess(schedule, reminder, source));
            return;
        }

        RequestBody body = RequestBody.create(requestJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(getApiUrl())
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + getApiKey())
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "generateReminderWithCloudAi failed HTTP " + response.code());
                String reminder = generatePersonalizedReminder(schedule, title);
                getMainHandler().post(() -> callback.onSuccess(schedule, reminder, source));
                return;
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            String content = extractContentFromResponse(responseBody);

            if (content == null || content.isEmpty()) {
                Log.w(TAG, "generateReminderWithCloudAi got empty content");
                String reminder = generatePersonalizedReminder(schedule, title);
                getMainHandler().post(() -> callback.onSuccess(schedule, reminder, source));
                return;
            }

            final String finalContent = content;
            Log.i(TAG, "Cloud AI reminder generated successfully");
            getMainHandler().post(() -> callback.onSuccess(schedule, finalContent, source));

        } catch (IOException e) {
            Log.e(TAG, "generateReminderWithCloudAi network error: " + e.getMessage());
            String reminder = generatePersonalizedReminder(schedule, title);
            getMainHandler().post(() -> callback.onSuccess(schedule, reminder, source));
        } catch (Exception e) {
            Log.e(TAG, "generateReminderWithCloudAi error: " + e.getMessage());
            String reminder = generatePersonalizedReminder(schedule, title);
            getMainHandler().post(() -> callback.onSuccess(schedule, reminder, source));
        }
    }

    private String buildReminderPrompt(String title, String workType, String address) {
        String deviceType = extractDeviceFromTitle(title);

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的家政服务管家，专门为维修师傅提供出发前准备建议。请根据任务信息生成详细、实用的准备清单。\n\n");
        prompt.append("【任务信息】\n");
        prompt.append("- 任务标题：").append(title).append("\n");
        prompt.append("- 工作类型：").append(workType).append("\n");
        if (deviceType != null) {
            prompt.append("- 设备类型：").append(deviceType).append("\n");
        }
        prompt.append("- 目的地址：").append(address).append("\n\n");
        prompt.append("【要求】\n");
        prompt.append("1. 请用中文回答，语气专业友好\n");
        prompt.append("2. 根据工作类型和设备类型，提供针对性的建议\n");
        prompt.append("3. 清单要具体实用，避免泛泛而谈\n");
        prompt.append("4. 安全注意事项要符合实际操作规范\n\n");
        prompt.append("【输出格式】\n");
        prompt.append("请按以下结构输出：\n\n");
        prompt.append("- 工具准备：列出具体需要的工具（3-5项）\n");
        prompt.append("- 材料配件：列出可能需要更换的配件或材料\n");
        prompt.append("- 安全事项：操作前的安全检查要点\n");
        prompt.append("- 客户沟通：出发前和客户确认的事项\n");
        prompt.append("- 验收标准：服务完成后需要客户确认的项目\n\n");
        if (deviceType != null) {
            prompt.append("【设备特别提示】\n");
            prompt.append("针对").append(deviceType).append("的操作，请注意：\n");
            prompt.append(getDeviceSpecificTips(deviceType)).append("\n\n");
        }
        prompt.append("请直接输出清单内容，不要添加额外的说明文字。");
        return prompt.toString();
    }

    private String extractDeviceFromTitle(String title) {
        if (title == null) return null;
        for (String device : DEVICE_KEYWORDS) {
            if (title.contains(device)) {
                return device;
            }
        }
        return null;
    }

    private String getDeviceSpecificTips(String deviceType) {
        switch (deviceType) {
            case "热水器": return "1. 关闭进水阀和电源总阀\n2. 准备防水垫布\n3. 检查旧机固定螺丝位置\n4. 注意镁棒腐蚀情况";
            case "空调": return "1. 检查外机支架牢固度\n2. 准备制冷剂（如需要加氟）\n3. 注意高空作业安全\n4. 清理排水管";
            case "油烟机": return "1. 准备重油污清洁剂\n2. 铺设防油垫布\n3. 检查止回阀是否堵塞\n4. 建议客户预约深度清洗";
            case "洗衣机": return "1. 检查进水管老化情况\n2. 测试排水是否通畅\n3. 准备水平尺调平\n4. 检查减震脚垫";
            case "下水道": return "1. 准备疏通弹簧（多种规格）\n2. 检查管道是否破裂\n3. 准备防水围裙\n4. 注意异味防护";
            case "燃气灶": return "1. 检查燃气接口是否漏气\n2. 测试点火装置\n3. 检查熄火保护装置\n4. 确认通风条件";
            case "电路": return "1. 务必断电操作\n2. 使用验电笔确认无电\n3. 检查线路老化情况\n4. 注意漏电保护器";
            default: return "1. 检查设备当前状态\n2. 准备常用工具\n3. 注意操作安全\n4. 做好防护措施";
        }
    }

    // ========== 本地解析 ==========

    private Schedule parseLocally(String text) {
        try {
            Schedule schedule = new Schedule();
            schedule.setId(String.valueOf(System.currentTimeMillis()));
            String address = extractAddress(text);
            // 标题 = 提取任务内容，如果包含地址则不重复拼接
            String title = extractTitle(text).replaceFirst("^[去在到]", "").trim();
            if (title == null || title.isEmpty()) {
                return null;
            }
            // 如果标题已经以地址开头，不再重复添加
            String fullTitle = title;
            if (address != null && !address.isEmpty() && !"地址待确认".equals(address)) {
                if (!title.startsWith(address)) {
                    fullTitle = address + title;
                }
            }
            schedule.setTitle(fullTitle);
            schedule.setAddress(address);
            schedule.setWorkType(extractWorkType(text));
            Date dateTime = parseDateTimeImproved(text);
            if (dateTime == null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.HOUR_OF_DAY, 2);
                dateTime = cal.getTime();
            }
            schedule.setTime(dateTime);
            schedule.setStatus("待出发");
            return schedule;
        } catch (Exception e) {
            Log.e(TAG, "Local parse error: " + e.getMessage());
            return null;
        }
    }

    private void callCloudAiForParsing(String userInput, AiCallback callback) {
        executor.execute(() -> {
            try {
                Calendar now = Calendar.getInstance();
                SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);
                SimpleDateFormat sdfWeekday = new SimpleDateFormat("EEEE", Locale.CHINA);
                String currentTime = sdfFull.format(now.getTime());
                String currentDate = sdfDate.format(now.getTime());
                String currentWeekday = sdfWeekday.format(now.getTime());

                String extractPrompt = buildExtractionPrompt(userInput, currentTime, currentDate, currentWeekday, now);
                Log.d(TAG, "=== 蓝心大模型 调用：信息提取（优化：仅提取，不生成提醒）===");

                String extractResponse = callCloudAiSync(extractPrompt, 0.05, 300);
                if (extractResponse == null) {
                    Log.w(TAG, "提取调用失败，回退本地解析");
                    fallbackToLocalParse(userInput, callback);
                    return;
                }

                Log.d(TAG, "提取原始响应: " + extractResponse.substring(0, Math.min(300, extractResponse.length())));

                Schedule schedule = parseExtractionResponse(extractResponse, userInput);
                if (schedule == null) {
                    Log.w(TAG, "JSON 解析失败，回退本地解析");
                    fallbackToLocalParse(userInput, callback);
                    return;
                }

                Log.i(TAG, "提取成功 -> 标题:" + schedule.getTitle()
                        + " 地址:" + schedule.getAddress()
                        + " 类型:" + schedule.getWorkType()
                        + " 时间:" + schedule.getTime());

                // 优化：不再做第二次调用生成提醒，直接返回，提醒将在详情页流式生成
                String reminder = generatePersonalizedReminder(schedule, schedule.getTitle());
                getMainHandler().post(() -> callback.onSuccess(schedule, reminder, AiSource.CLOUD_AI_SUCCESS));

            } catch (Exception e) {
                Log.e(TAG, "callCloudAiForParsing error: " + e.getMessage());
                fallbackToLocalParse(userInput, callback);
            }
        });
    }

    private String callCloudAiSync(String prompt, double temperature, int maxTokens) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", getModel());

            org.json.JSONArray messages = new org.json.JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个精确的日程信息提取助手。严格按JSON格式输出，禁止输出任何解释、思考过程或JSON外的文字。");
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);

            requestJson.put("messages", messages);
            requestJson.put("stream", false);
            requestJson.put("temperature", temperature);
            requestJson.put("max_tokens", maxTokens);

            RequestBody body = RequestBody.create(requestJson.toString(), JSON);
            Request request = new Request.Builder()
                    .url(getApiUrl())
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + getApiKey())
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = "(empty)";
                    if (response.body() != null) {
                        errBody = response.body().string();
                    }
                    Log.e(TAG, "Cloud AI HTTP " + response.code() + ": " + errBody);
                    return null;
                }
                String responseBody = "";
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
                String content = extractContentFromResponse(responseBody);
                return content == null || content.isEmpty() ? null : content;
            }
        } catch (Exception e) {
            Log.e(TAG, "callCloudAiSync error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从 OpenAI 兼容格式的响应中提取 content
     */
    private String extractContentFromResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            // 优先从 choices[0].message.content 读取
            org.json.JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.optJSONObject("message");
                if (message != null) {
                    String content = message.optString("content", "").trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                }
                // 兼容 delta 格式（流式响应的聚合结果）
                JSONObject delta = firstChoice.optJSONObject("delta");
                if (delta != null) {
                    String content = delta.optString("content", "").trim();
                    if (!content.isEmpty()) {
                        return content;
                    }
                }
            }
            // 降级：直接读取 content 字段
            String content = json.optString("content", "").trim();
            if (!content.isEmpty()) {
                return content;
            }
            Log.w(TAG, "extractContentFromResponse: 无法从响应中提取内容, body=" + responseBody.substring(0, Math.min(200, responseBody.length())));
            return null;
        } catch (Exception e) {
            Log.e(TAG, "extractContentFromResponse error: " + e.getMessage());
            return null;
        }
    }

    private String buildExtractionPrompt(String userInput, String currentTime, String currentDate,
                                          String currentWeekday, Calendar now) {
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;

        String today = formatDate(now, 0);
        String tomorrow = formatDate(now, 1);
        String dayAfter = formatDate(now, 2);
        String dayAfter2 = formatDate(now, 3);

        // 生成下周每天的日期（简化版，只列出下周工作日）
        Calendar nextWeekCal = (Calendar) now.clone();
        nextWeekCal.add(Calendar.WEEK_OF_YEAR, 1);
        String nextMonday = formatDate(nextWeekCal, 0);
        nextWeekCal.add(Calendar.DAY_OF_MONTH, 1);
        String nextTuesday = formatDate(nextWeekCal, 0);
        nextWeekCal.add(Calendar.DAY_OF_MONTH, 1);
        String nextWednesday = formatDate(nextWeekCal, 0);
        nextWeekCal.add(Calendar.DAY_OF_MONTH, 1);
        String nextThursday = formatDate(nextWeekCal, 0);
        nextWeekCal.add(Calendar.DAY_OF_MONTH, 1);
        String nextFriday = formatDate(nextWeekCal, 0);

        return "你是一个精确的日程信息提取助手。严格按以下JSON格式输出，禁止输出任何解释、思考过程或JSON外的文字。\n\n"
             + "当前时间参考：" + currentDate + "（" + currentWeekday + "）" + currentTime + "\n"
             + "速查日期：今天=" + today + "，明天=" + tomorrow + "，后天=" + dayAfter + "，大后天=" + dayAfter2 + "\n"
             + "下周参考：下周一=" + nextMonday + "，下周二=" + nextTuesday + "，下周三=" + nextWednesday + "，下周四=" + nextThursday + "，下周五=" + nextFriday + "\n\n"
             + "【必须严格遵循的规则】\n"
             + "1. 只输出这一行JSON，不要有任何其他内容：\n"
             + "   {\"title\":\"任务名称（核心动作+对象，如'安装空调'、'维修热水器'）\",\"address\":\"地点关键词\",\"work_type\":\"服务类型\",\"date\":\"yyyy-MM-dd\",\"time\":\"HH:mm\"}\n\n"
             + "2. title提取规则：\n"
             + "   - 只保留任务核心内容（动作+对象），去掉所有地址、时间、语气词\n"
             + "   - 常见动作：安装、维修、清洗、疏通、检修、保养、拆装、拆除、调试、检测\n"
             + "   - 示例：'去华美居小区安装空调' → title='安装空调'\n\n"
             + "3. address提取规则：\n"
             + "   - 只提取地点关键词（小区名/大厦名/街道名）\n"
             + "   - title中绝对不能包含address的内容\n"
             + "   - 示例：'去华美居小区' → address='华美居小区'\n\n"
             + "4. work_type规则：从[安装,维修,疏通,清洗,检修,更换,保养,拆装,拆除,调试,检测]选最匹配的，加'服务'\n"
             + "   - '修理'归类为'维修服务'\n"
             + "   - '加装'归类为'安装服务'\n\n"
             + "5. date解析规则（严格按以下优先级）：\n"
             + "   a) 如果输入包含具体日期（如5月20日），直接用年月+'月日'\n"
             + "   b) 如果日期在速查表中，使用对应日期\n"
             + "   c) 如果日期跨月（如4月说5月），直接用年月+'月日'\n"
             + "   d) 如果只说时间没提日期，用今天\n\n"
             + "6. time时间转换规则（必须严格遵守）：\n"
             + "   - 数字时间：五点=05:00，十七点=17:00\n"
             + "   - 中文数字：五点二十=05:20，五点二十分=05:20，十七点二十=17:20\n"
             + "   - 带分：三点十五=03:15，晚上八点半=20:30\n"
             + "   - 下午/晚上/傍晚：小时+12（如果小时<12）\n"
             + "   - 上午/早上：保持原小时\n"
             + "   - 中午12点：12:00\n\n"
             + "【完整示例】（输入→输出）\n"
             + "输入：'5月20日下午五点二十分我要去华美居小区修理燃气灶'\n"
             + "输出：{\"title\":\"维修燃气灶\",\"address\":\"华美居小区\",\"work_type\":\"维修服务\",\"date\":\"" + year + "-05-20\",\"time\":\"17:20\"}\n\n"
             + "输入：'明天下午3点去幸福小区维修洗衣机'\n"
             + "输出：{\"title\":\"维修洗衣机\",\"address\":\"幸福小区\",\"work_type\":\"维修服务\",\"date\":\"" + tomorrow + "\",\"time\":\"15:00\"}\n\n"
             + "输入：'今天晚上8点半我要去青年公寓疏通下水道'\n"
             + "输出：{\"title\":\"疏通下水道\",\"address\":\"青年公寓\",\"work_type\":\"疏通服务\",\"date\":\"" + today + "\",\"time\":\"20:30\"}\n\n"
             + "输入：'4月17日我要去华美居小区安装空调'\n"
             + "输出：{\"title\":\"安装空调\",\"address\":\"华美居小区\",\"work_type\":\"安装服务\",\"date\":\"" + year + "-04-17\",\"time\":\"09:00\"}\n\n"
             + "输入：'5月3日上午十点去华美居小区清洗油烟机'\n"
             + "输出：{\"title\":\"清洗油烟机\",\"address\":\"华美居小区\",\"work_type\":\"清洗服务\",\"date\":\"" + year + "-05-03\",\"time\":\"10:00\"}\n\n"
             + "现在处理这个输入：\n"
             + "输入内容：'" + userInput + "'\n"
             + "直接输出JSON（不要解释，不要思考，只输出JSON）：";
    }

    private String formatDate(Calendar base, int daysOffset) {
        Calendar c = (Calendar) base.clone();
        c.add(Calendar.DAY_OF_MONTH, daysOffset);
        return String.format(Locale.CHINA, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private Schedule parseExtractionResponse(String rawContent, String originalInput) {
        try {
            String cleaned = rawContent.trim();
            cleaned = cleaned.replaceAll("```json", "").replaceAll("```", "").trim();

            int jsonStart = cleaned.indexOf("{");
            int jsonEnd = cleaned.lastIndexOf("}");
            if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
                Log.e(TAG, "parseExtractionResponse: 找不到 JSON 括号，内容=" + cleaned);
                return null;
            }
            String jsonStr = cleaned.substring(jsonStart, jsonEnd + 1);
            Log.d(TAG, "parseExtractionResponse JSON: " + jsonStr);

            JSONObject data = new JSONObject(jsonStr);

            String title = data.optString("title", "").trim();
            String address = data.optString("address", "").trim();
            String workType = data.optString("work_type", "").trim();
            String dateStr = data.optString("date", "").trim();
            String timeStr = data.optString("time", "").trim();

            if (title.isEmpty()) {
                Log.w(TAG, "parseExtractionResponse: title 为空，原始 JSON=" + jsonStr);
                return null;
            }

            if (timeStr.isEmpty()) timeStr = "09:00";
            if (dateStr.isEmpty()) {
                dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
                Log.w(TAG, "parseExtractionResponse: 模型未返回 date，使用今天=" + dateStr);
            }

            String fullDateTimeStr = dateStr + " " + timeStr;
            Date parsedTime;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                parsedTime = sdf.parse(fullDateTimeStr);
                if (parsedTime == null) throw new Exception("parse returned null");
                Log.i(TAG, "parseExtractionResponse 时间解析成功: " + fullDateTimeStr);
            } catch (Exception e) {
                Log.e(TAG, "parseExtractionResponse 时间解析失败: " + fullDateTimeStr + ", error=" + e.getMessage());
                return null;
            }

            if (!workType.isEmpty() && !workType.endsWith("服务")) {
                workType = workType + "服务";
            }
            if (workType.isEmpty()) workType = "综合服务";

            // UI 显示用标题：地址 + 任务（组合显示，避免重复）
            String displayTitle = title;
            if (address != null && !address.isEmpty() && !address.equals("地址待确认")) {
                // 如果 title 已经包含地址，直接使用；否则拼接
                if (!title.contains(address)) {
                    displayTitle = address + title;
                }
            }

            Schedule schedule = new Schedule();
            schedule.setId(String.valueOf(System.currentTimeMillis()));
            schedule.setTitle(displayTitle);
            schedule.setAddress(address);
            schedule.setWorkType(workType);
            schedule.setTime(parsedTime);
            schedule.setStatus("待出发");

            return schedule;

        } catch (Exception e) {
            Log.e(TAG, "parseExtractionResponse error: " + e.getMessage());
            return null;
        }
    }

    private void fallbackToLocalParse(String userInput, AiCallback callback) {
        Log.i(TAG, "Falling back to local regex parsing (Cloud AI unavailable or failed)");
        Schedule schedule = parseLocally(userInput);
        if (schedule != null) {
            String reminder = generatePersonalizedReminder(schedule, userInput);
            getMainHandler().post(() -> callback.onSuccess(schedule, reminder, AiSource.LOCAL_FALLBACK));
        } else {
            getMainHandler().post(() -> callback.onError("无法解析此日程，请输入更详细的描述，例如：明天下午3点去XX小区安装热水器"));
        }
    }

    private Date parseDateTimeImproved(String text) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int originalYear = cal.get(Calendar.YEAR);
        int originalMonth = cal.get(Calendar.MONTH);
        int originalDay = cal.get(Calendar.DAY_OF_MONTH);
        boolean dateExplicitlySet = false;
        boolean timeExplicitlySet = false;

        Matcher fullDateMatcher = DATE_FULL.matcher(text);
        if (fullDateMatcher.find()) {
            int year = Integer.parseInt(fullDateMatcher.group(1));
            int month = Integer.parseInt(fullDateMatcher.group(2));
            int day = Integer.parseInt(fullDateMatcher.group(3));
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, day);
            dateExplicitlySet = true;
        } else {
            Matcher monthDayMatcher = DATE_MONTH_DAY_CN.matcher(text);
            if (!monthDayMatcher.find()) {
                monthDayMatcher = DATE_MONTH_DAY.matcher(text);
            }
            if (monthDayMatcher.find()) {
                int month = Integer.parseInt(monthDayMatcher.group(1));
                int day = Integer.parseInt(monthDayMatcher.group(2));
                if (month < (originalMonth + 1)) {
                    cal.set(Calendar.YEAR, originalYear + 1);
                } else if (month == (originalMonth + 1) && day < originalDay) {
                    cal.set(Calendar.YEAR, originalYear + 1);
                }
                cal.set(Calendar.MONTH, month - 1);
                cal.set(Calendar.DAY_OF_MONTH, day);
                dateExplicitlySet = true;
            }
        }

        if (!dateExplicitlySet) {
            if (DATE_DAY_AFTER_TOMORROW.matcher(text).find()) {
                cal.add(Calendar.DAY_OF_MONTH, 3);
                dateExplicitlySet = true;
            } else if (DATE_DAY_AFTER.matcher(text).find()) {
                cal.add(Calendar.DAY_OF_MONTH, 2);
                dateExplicitlySet = true;
            } else if (DATE_TOMORROW.matcher(text).find()) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
                dateExplicitlySet = true;
            } else if (DATE_TODAY.matcher(text).find()) {
                dateExplicitlySet = true;
            } else if (DATE_NEXT_WEEK.matcher(text).find()) {
                cal.add(Calendar.WEEK_OF_YEAR, 1);
                dateExplicitlySet = true;
            } else if (DATE_NEXT_MONTH.matcher(text).find()) {
                cal.add(Calendar.MONTH, 1);
                dateExplicitlySet = true;
            } else {
                Matcher weekdayMatcher = NEXT_WEEKDAY_PATTERN.matcher(text);
                if (weekdayMatcher.find()) {
                    int targetWeekday = parseWeekday(weekdayMatcher.group(3));
                    if (targetWeekday > 0) {
                        cal.add(Calendar.WEEK_OF_YEAR, 1);
                        cal.set(Calendar.DAY_OF_WEEK, targetWeekday);
                        dateExplicitlySet = true;
                    }
                } else {
                    Matcher singleWeekdayMatcher = WEEKDAY_PATTERN.matcher(text);
                    if (singleWeekdayMatcher.find()) {
                        int targetWeekday = parseWeekday(singleWeekdayMatcher.group(2));
                        if (targetWeekday > 0) {
                            cal.set(Calendar.DAY_OF_WEEK, targetWeekday);
                            if (cal.getTime().before(new Date())) {
                                cal.add(Calendar.WEEK_OF_YEAR, 1);
                            }
                            dateExplicitlySet = true;
                        }
                    }
                }
            }
        }

        Matcher amPmMatcher = TIME_AM_PM.matcher(text);
        if (amPmMatcher.find()) {
            String period = amPmMatcher.group(1);
            // 使用 parseNumber 支持中文数字如 "五点"
            int hour = parseNumber(amPmMatcher.group(2));
            String minuteStr = amPmMatcher.group(3);
            int minute = (minuteStr != null && !minuteStr.isEmpty()) ? parseNumber(minuteStr) : 0;
            if ("下午".equals(period) || "晚上".equals(period) || "傍晚".equals(period)) {
                if (hour < 12) hour += 12;
            } else if ("中午".equals(period)) {
                if (hour <= 2) hour += 12;
                else if (hour < 12) hour = 12;
            }
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            timeExplicitlySet = true;
        }

        if (!timeExplicitlySet) {
            Matcher timeMatcher = TIME_HOUR_MIN.matcher(text);
            if (timeMatcher.find()) {
                int hour = Integer.parseInt(timeMatcher.group(1));
                String minuteStr = timeMatcher.group(2);
                int minute = (minuteStr != null && !minuteStr.isEmpty()) ? Integer.parseInt(minuteStr) : 0;
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                timeExplicitlySet = true;
            }
        }

        if (!timeExplicitlySet) {
            Matcher halfMatcher = TIME_HALF.matcher(text);
            if (halfMatcher.find()) {
                int hour = Integer.parseInt(halfMatcher.group(1));
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, 30);
                timeExplicitlySet = true;
            }
        }

        if (!timeExplicitlySet) {
            Matcher quarterMatcher = TIME_QUARTER.matcher(text);
            if (quarterMatcher.find()) {
                int hour = Integer.parseInt(quarterMatcher.group(1));
                String quarterStr = quarterMatcher.group(2);
                int minute = 0;
                if ("一".equals(quarterStr)) minute = 15;
                else if ("二".equals(quarterStr)) minute = 30;
                else if ("三".equals(quarterStr)) minute = 45;
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                timeExplicitlySet = true;
            }
        }

        Matcher hourMatcher = RELATIVE_HOUR.matcher(text);
        if (!hourMatcher.find()) hourMatcher = RELATIVE_HOUR_SHORT.matcher(text);
        if (hourMatcher.find()) {
            int hours = Integer.parseInt(hourMatcher.group(1));
            cal = Calendar.getInstance();
            cal.add(Calendar.HOUR_OF_DAY, hours);
            timeExplicitlySet = true;
        }

        Matcher minuteMatcher = RELATIVE_MINUTE.matcher(text);
        if (!minuteMatcher.find()) minuteMatcher = RELATIVE_MINUTE_SHORT.matcher(text);
        if (minuteMatcher.find()) {
            int minutes = Integer.parseInt(minuteMatcher.group(1));
            cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, minutes);
            timeExplicitlySet = true;
        }

        if (!dateExplicitlySet && timeExplicitlySet && cal.getTime().before(new Date())) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (!timeExplicitlySet) {
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
        }

        if (!dateExplicitlySet && !timeExplicitlySet) {
            cal = Calendar.getInstance();
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
        }

        return cal.getTime();
    }

    private int parseWeekday(String weekdayStr) {
        if (weekdayStr == null) return -1;
        switch (weekdayStr) {
            case "一": case "1": return Calendar.MONDAY;
            case "二": case "2": return Calendar.TUESDAY;
            case "三": case "3": return Calendar.WEDNESDAY;
            case "四": case "4": return Calendar.THURSDAY;
            case "五": case "5": return Calendar.FRIDAY;
            case "六": case "6": return Calendar.SATURDAY;
            case "日": case "天": case "7": return Calendar.SUNDAY;
            default: return -1;
        }
    }

    /**
     * 中文数字转阿拉伯数字
     * 支持：零一二三四五六七八九十百千万亿 -> 0-9及组合
     */
    private int chineseToNumber(String chinese) {
        if (chinese == null || chinese.isEmpty()) return 0;
        chinese = chinese.trim();
        
        // 如果已经是阿拉伯数字，直接返回
        try {
            return Integer.parseInt(chinese);
        } catch (NumberFormatException ignored) {}
        
        int result = 0;
        int temp = 0;
        int lastUnit = 1;
        
        for (int i = 0; i < chinese.length(); i++) {
            char c = chinese.charAt(i);
            int num = -1;
            
            switch (c) {
                case '零': num = 0; break;
                case '一': case '壹': num = 1; break;
                case '二': case '两': case '贰': num = 2; break;
                case '三': case '叁': num = 3; break;
                case '四': case '肆': num = 4; break;
                case '五': case '伍': num = 5; break;
                case '六': case '陆': num = 6; break;
                case '七': case '柒': num = 7; break;
                case '八': case '捌': num = 8; break;
                case '九': case '玖': num = 9; break;
                case '十': case '拾': num = 10; break;
                case '百': case '佰': num = 100; break;
                case '千': case '仟': num = 1000; break;
                case '万': num = 10000; break;
                case '亿': num = 100000000; break;
            }
            
            if (num == -1) continue;
            
            if (num >= 10) {
                if (temp == 0) temp = 1;
                result += temp * num;
                temp = 0;
            } else {
                temp = temp * 10 + num;
            }
        }
        
        return result + temp;
    }

    /**
     * 解析数字字符串（支持中文数字）
     */
    private int parseNumber(String numStr) {
        if (numStr == null || numStr.isEmpty()) return 0;
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            return chineseToNumber(numStr);
        }
    }

    private String extractTitle(String text) {
        // 提取任务核心内容：去掉时间日期、去掉地址前缀
        String cleaned = text;
        // 1. 先去掉各种时间日期
        cleaned = DATE_TODAY.matcher(cleaned).replaceAll("");
        cleaned = DATE_TOMORROW.matcher(cleaned).replaceAll("");
        cleaned = DATE_DAY_AFTER.matcher(cleaned).replaceAll("");
        cleaned = DATE_DAY_AFTER_TOMORROW.matcher(cleaned).replaceAll("");
        cleaned = DATE_WEEK.matcher(cleaned).replaceAll("");
        cleaned = DATE_NEXT_WEEK.matcher(cleaned).replaceAll("");
        cleaned = DATE_NEXT_MONTH.matcher(cleaned).replaceAll("");
        cleaned = WEEKDAY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = NEXT_WEEKDAY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = DATE_MONTH_DAY.matcher(cleaned).replaceAll("");
        cleaned = DATE_FULL.matcher(cleaned).replaceAll("");
        cleaned = TIME_HOUR_MIN.matcher(cleaned).replaceAll("");
        cleaned = TIME_AM_PM.matcher(cleaned).replaceAll("");
        cleaned = TIME_HALF.matcher(cleaned).replaceAll("");
        cleaned = TIME_QUARTER.matcher(cleaned).replaceAll("");
        cleaned = TIME_ONLY_HOUR.matcher(cleaned).replaceAll("");
        cleaned = RELATIVE_HOUR.matcher(cleaned).replaceAll("");
        cleaned = RELATIVE_MINUTE.matcher(cleaned).replaceAll("");
        // 2. 去掉动作词（去、到、在等）
        cleaned = cleaned.replaceAll("^[去在到]", "").trim();
        cleaned = cleaned.replaceAll("^(我?(要|想)?)?[去在到]", "").trim();
        // 3. 去掉"帮我"、"帮我预约"等语气词
        cleaned = cleaned.replaceAll("^(帮我|帮我预约|预约个|帮我|请|我要)" , "").trim();
        // 4. 去掉开头的空格
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? "新任务" : cleaned;
    }

    private String extractAddress(String text) {
        String[] addressKeywords = {"去", "到", "在", "地址", "位置"};
        for (String keyword : addressKeywords) {
            int index = text.indexOf(keyword);
            if (index >= 0 && index < text.length() - 1) {
                String address = text.substring(index + 1).trim();
                address = DATE_MONTH_DAY.matcher(address).replaceAll("");
                address = TIME_HOUR_MIN.matcher(address).replaceAll("");
                address = TIME_AM_PM.matcher(address).replaceAll("");
                return address.trim();
            }
        }
        return "地址待确认";
    }

    private String extractWorkType(String text) {
        if (text == null || text.isEmpty()) return "综合服务";
        String lowerText = text.toLowerCase();
        for (String type : WORK_TYPES) {
            if (lowerText.contains(type)) {
                String mappedType = WORK_TYPE_MAPPING.get(type);
                return mappedType != null ? mappedType : type + "服务";
            }
        }
        if (lowerText.contains("新装")) return "安装服务";
        if (lowerText.contains("拆旧")) return "拆除服务";
        if (lowerText.contains("移机")) return "拆装服务";
        return "综合服务";
    }

    // ========== 本地提醒生成 ==========

    private String generatePersonalizedReminder(Schedule schedule, String originalInput) {
        StringBuilder sb = new StringBuilder();
        String workType = schedule.getWorkType();
        String title = schedule.getTitle();
        String deviceType = extractDeviceFromTitle(title);
        String[] tools = getToolsForWorkTypeAndDevice(workType, deviceType);

        sb.append(title != null ? title : "任务").append(" 出发前准备清单\n\n");
        sb.append("【工具准备】\n");
        for (String tool : tools) {
            sb.append("- ").append(tool).append("\n");
        }
        sb.append("\n");

        if (workType != null && workType.contains("安装")) {
            sb.append("【材料配件】\n").append(getInstallMaterials(deviceType));
            sb.append("\n【安全检查】\n").append(getInstallSafetyChecks(deviceType));
        } else if (workType != null && workType.contains("维修")) {
            sb.append("【材料配件】\n").append(getRepairMaterials(deviceType));
            sb.append("\n【安全检查】\n").append(getRepairSafetyChecks(deviceType));
        } else if (workType != null && workType.contains("清洗")) {
            sb.append("【材料配件】\n");
            sb.append("- 专业清洗剂（针对").append(deviceType != null ? deviceType : "设备").append("类型）\n");
            sb.append("- 防护手套、口罩\n");
            sb.append("- 吸水毛巾、防水垫布\n\n");
            sb.append("【安全检查】\n");
            sb.append("- 断电后再清洗\n");
            sb.append("- 确认防水措施到位\n");
            sb.append("- 准备污水收集容器\n");
        } else if (workType != null && workType.contains("疏通")) {
            sb.append("【材料配件】\n");
            sb.append("- 管道疏通剂\n");
            sb.append("- 备用水管接头\n");
            sb.append("- 密封胶带\n\n");
            sb.append("【安全检查】\n");
            sb.append("- 做好地面防水措施\n");
            sb.append("- 保持通风（疏通剂气味）\n");
            sb.append("- 佩戴防护手套和口罩\n");
        } else if (workType != null && workType.contains("拆装")) {
            sb.append("【材料配件】\n");
            sb.append("- 新安装所需的固定件\n");
            sb.append("- 密封材料（生料带、密封胶）\n");
            sb.append("- 备用螺丝和垫圈\n\n");
            sb.append("【安全检查】\n");
            sb.append("- 先拆除旧设备，检查接口状态\n");
            sb.append("- 断电/断水操作\n");
            sb.append("- 注意旧设备可能有残留物\n");
        } else {
            sb.append("【材料配件】\n");
            sb.append("- 常用配件和耗材\n");
            sb.append("- 备用密封件\n\n");
            sb.append("【安全检查】\n");
            sb.append("- 断电/断水后再操作\n");
            sb.append("- 做好个人防护\n");
        }

        sb.append("\n【客户沟通】\n");
        sb.append("- 出发前电话确认客户是否在家\n");
        sb.append("- 告知预计到达时间\n");
        sb.append("- 确认具体楼层和门牌号\n");
        if (deviceType != null) {
            sb.append("- 询问").append(deviceType).append("的型号和故障现象\n");
        }

        sb.append("\n【服务要点】\n");
        sb.append("- 服务前说明预计费用\n");
        sb.append("- 穿戴整洁工服，佩戴工牌\n");
        sb.append("- 服务后请客户验收确认\n");
        if (deviceType != null) {
            sb.append("- 演示").append(deviceType).append("的正常使用方法\n");
        }

        if (deviceType != null) {
            sb.append("\n【").append(deviceType).append("特别提示】\n");
            sb.append(getDeviceSpecificTips(deviceType));
        }

        return sb.toString();
    }

    private String[] getToolsForWorkTypeAndDevice(String workType, String deviceType) {
        if (deviceType != null && DEVICE_TOOLS_MAP.containsKey(deviceType)) {
            return DEVICE_TOOLS_MAP.get(deviceType);
        }
        if (workType != null) {
            if (workType.contains("安装")) return new String[]{"冲击钻", "扳手套装", "水平尺", "记号笔", "螺丝刀组"};
            if (workType.contains("维修")) return new String[]{"工具箱", "螺丝刀组", "万用表", "测电笔", "绝缘胶带"};
            if (workType.contains("清洗")) return new String[]{"清洁刷", "海绵", "清洗剂", "防护手套", "吸水毛巾"};
            if (workType.contains("疏通")) return new String[]{"管道疏通器", "疏通剂", "橡胶手套", "水桶", "手电筒"};
        }
        return new String[]{"工具箱", "常用螺丝刀", "手机充满电", "防护手套"};
    }

    private String getInstallMaterials(String deviceType) {
        if ("热水器".equals(deviceType)) return "- 膨胀螺栓（根据墙体材质选择）\n- 生料带、密封胶\n- 冷热进水管（如需更换）\n- 电源线（如需要）\n";
        if ("空调".equals(deviceType)) return "- 铜管保温套\n- 膨胀螺栓\n- 制冷剂（如需要加氟）\n- 排水管\n";
        if ("油烟机".equals(deviceType)) return "- 止回阀\n- 排烟管\n- 膨胀螺栓\n- 密封胶\n";
        return "- 膨胀螺栓、生料带\n- 密封胶、垫圈\n- 根据设备准备专用配件\n";
    }

    private String getInstallSafetyChecks(String deviceType) {
        if ("热水器".equals(deviceType)) return "- 确认安装位置墙体承重能力\n- 关闭进水阀和电源总阀\n- 检查旧机固定螺丝位置\n- 注意燃气管道位置（燃气热水器）\n";
        if ("空调".equals(deviceType)) return "- 检查外机支架牢固度\n- 确认排水孔位置\n- 注意高空作业安全\n- 检查电源线径是否足够\n";
        if ("油烟机".equals(deviceType)) return "- 确认烟道通畅\n- 检查止回阀安装方向\n- 注意灶台安全距离\n- 确认电源位置\n";
        return "- 确认安装位置稳固\n- 关闭相关电源/水源总阀\n- 检查安装环境安全性\n";
    }

    private String getRepairMaterials(String deviceType) {
        if ("热水器".equals(deviceType)) return "- 镁棒（如需更换）\n- 密封垫圈\n- 加热管（如需更换）\n- 温控器备件\n";
        if ("空调".equals(deviceType)) return "- 制冷剂\n- 电容（压缩机/风扇）\n- 过滤网（如需更换）\n- 排水管\n";
        if ("洗衣机".equals(deviceType)) return "- 进水管\n- 排水管\n- 密封圈\n- 减震脚垫\n";
        return "- 常见维修配件\n- 密封垫圈\n- 绝缘胶带、扎带\n";
    }

    private String getRepairSafetyChecks(String deviceType) {
        if ("热水器".equals(deviceType)) return "- 断电并关闭进水阀\n- 排空水箱余水\n- 检查漏电保护器\n- 注意高温部件\n";
        if ("空调".equals(deviceType)) return "- 断电操作\n- 制冷剂回收（如需拆机）\n- 注意电容放电\n- 检查外机支架\n";
        if ("燃气灶".equals(deviceType) || "电路".equals(deviceType)) return "- 务必断电/断气操作\n- 使用验电笔确认安全\n- 检查线路/管道老化\n- 注意通风条件\n";
        return "- 断电后再操作\n- 确认管道无压后再拆卸\n- 使用验电笔检测\n";
    }

    // ========== 对外入口：解析自然语言日程（MainActivity 调用）==========
    public void parseScheduleFromText(String text, AiCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            getMainHandler().post(() -> callback.onError("输入内容为空，请描述你的日程安排"));
            return;
        }
        String trimmedText = text.trim();
        Log.d(TAG, "=== 开始解析日程 ===");
        Log.d(TAG, "原始输入: " + trimmedText);
        callCloudAiForParsing(trimmedText, callback);
    }

    // ========== 意图识别：判断输入是日程还是问答 ==========

    public enum InputIntent {
        SCHEDULE,   // 日程任务
        CHAT        // 普通问答
    }

    public interface IntentCallback {
        void onResult(InputIntent intent);
    }

    /**
     * 意图识别：判断用户输入是日程还是普通问答
     * 先用本地关键词快速判断，无法确定时再调用大模型
     */
    public void classifyInput(String text, IntentCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            getMainHandler().post(() -> callback.onResult(InputIntent.CHAT));
            return;
        }
        String trimmed = text.trim();

        // ---------- 本地关键词快速判断 ----------
        // 日程关键词：包含时间 + 动作 + 地点等组合
        boolean hasTimeKeyword = DATE_TODAY.matcher(trimmed).find()
                || DATE_TOMORROW.matcher(trimmed).find()
                || DATE_DAY_AFTER.matcher(trimmed).find()
                || DATE_DAY_AFTER_TOMORROW.matcher(trimmed).find()
                || DATE_NEXT_WEEK.matcher(trimmed).find()
                || DATE_NEXT_MONTH.matcher(trimmed).find()
                || WEEKDAY_PATTERN.matcher(trimmed).find()
                || NEXT_WEEKDAY_PATTERN.matcher(trimmed).find()
                || DATE_MONTH_DAY.matcher(trimmed).find()
                || DATE_MONTH_DAY_CN.matcher(trimmed).find()
                || DATE_FULL.matcher(trimmed).find()
                || TIME_HOUR_MIN.matcher(trimmed).find()
                || TIME_AM_PM.matcher(trimmed).find()
                || TIME_HALF.matcher(trimmed).find()
                || RELATIVE_HOUR.matcher(trimmed).find()
                || RELATIVE_MINUTE.matcher(trimmed).find();

        boolean hasWorkKeyword = false;
        for (String type : WORK_TYPES) {
            if (trimmed.contains(type)) {
                hasWorkKeyword = true;
                break;
            }
        }

        boolean hasScheduleVerb = trimmed.contains("去") || trimmed.contains("要到")
                || trimmed.contains("要去") || trimmed.contains("安排")
                || trimmed.contains("预约") || trimmed.contains("提醒我")
                || trimmed.contains("有个任务") || trimmed.contains("有活")
                || trimmed.contains("接了个") || trimmed.contains("接单");

        boolean hasDeviceKeyword = false;
        for (String device : DEVICE_KEYWORDS) {
            if (trimmed.contains(device)) {
                hasDeviceKeyword = true;
                break;
            }
        }

        // 强日程特征：有时间 + (工作动作 或 设备)
        if (hasTimeKeyword && (hasWorkKeyword || hasDeviceKeyword || hasScheduleVerb)) {
            Log.d(TAG, "意图识别(本地): 日程 - 时间+工作/设备/动作");
            getMainHandler().post(() -> callback.onResult(InputIntent.SCHEDULE));
            return;
        }

        // 强日程特征：工作动作 + 地点
        boolean hasAddressKeyword = trimmed.contains("小区") || trimmed.contains("大厦")
                || trimmed.contains("公寓") || trimmed.contains("花园")
                || trimmed.contains("广场") || trimmed.contains("栋")
                || trimmed.contains("楼") || trimmed.contains("号");
        if ((hasWorkKeyword || hasScheduleVerb) && hasAddressKeyword) {
            Log.d(TAG, "意图识别(本地): 日程 - 工作/动作+地点");
            getMainHandler().post(() -> callback.onResult(InputIntent.SCHEDULE));
            return;
        }

        // 纯时间 + 动作（如"明天3点去修空调"）
        if (hasTimeKeyword && hasScheduleVerb) {
            Log.d(TAG, "意图识别(本地): 日程 - 时间+动作词");
            getMainHandler().post(() -> callback.onResult(InputIntent.SCHEDULE));
            return;
        }

        // ---------- 模糊情况：调用大模型判断 ----------
        Log.d(TAG, "意图识别(本地未确定)，调用大模型判断: " + trimmed);
        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", getModel());

                org.json.JSONArray messages = new org.json.JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一个意图分类器。判断用户输入是「日程任务」还是「普通问答」。只输出一个词：SCHEDULE 或 CHAT，禁止输出任何其他内容。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", "判断以下输入的意图：\n"
                    + "- 如果是安排工作、预约服务、提醒做某事、描述时间地点的任务 → 输出 SCHEDULE\n"
                    + "- 如果是提问、闲聊、知识咨询、求助问题 → 输出 CHAT\n\n"
                    + "输入：「" + trimmed + "」\n\n"
                    + "只输出 SCHEDULE 或 CHAT：");
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.0);
                requestJson.put("max_tokens", 10);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(getApiUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + getApiKey())
                        .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        String content = extractContentFromResponse(responseBody);
                        if (content != null) {
                            String upper = content.toUpperCase().trim();
                            if (upper.contains("SCHEDULE")) {
                                Log.d(TAG, "意图识别(AI): 日程");
                                getMainHandler().post(() -> callback.onResult(InputIntent.SCHEDULE));
                                return;
                            }
                        }
                    }
                }

                // AI 判断失败时默认走问答
                Log.d(TAG, "意图识别(AI判断失败): 默认问答");
                getMainHandler().post(() -> callback.onResult(InputIntent.CHAT));

            } catch (Exception e) {
                Log.e(TAG, "意图识别异常: " + e.getMessage());
                getMainHandler().post(() -> callback.onResult(InputIntent.CHAT));
            }
        });
    }

    // ========== 问答对话能力 ==========

    public interface ChatCallback {
        void onSuccess(String reply);
        void onError(String errorMessage);
    }

    /**
     * 流式对话回调：逐字输出
     */
    public interface StreamChatCallback {
        void onStart();                          // 开始生成
        void onDelta(String text, boolean isDone); // 逐字/逐段输出，isDone=true 表示结束
        void onError(String errorMessage);
    }

    /**
     * 构建 messages 数组，包含 system + 历史对话 + 当前用户输入
     */
    private org.json.JSONArray buildChatMessages(String userInput) {
        org.json.JSONArray messages = new org.json.JSONArray();
        try {
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是工友通的智能助手，基于蓝心大模型。你擅长家政维修领域的知识问答和逻辑推理，"
                + "也能回答日常问题。请用简洁友好的中文回答，如果涉及维修安装类问题，提供专业实用的建议。");
            messages.put(systemMsg);

            // 加入历史对话
            for (ChatMessage msg : chatHistory) {
                JSONObject m = new JSONObject();
                m.put("role", msg.role);
                m.put("content", msg.content);
                messages.put(m);
            }

            // 当前用户输入
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userInput.trim());
            messages.put(userMsg);
        } catch (Exception e) {
            Log.e(TAG, "buildChatMessages error: " + e.getMessage());
        }
        return messages;
    }

    /**
     * 通用问答对话：非流式（保留用于简单场景）
     */
    public void chatWithAi(String userInput, ChatCallback callback) {
        if (userInput == null || userInput.trim().isEmpty()) {
            getMainHandler().post(() -> callback.onError("输入内容为空"));
            return;
        }

        Log.d(TAG, "=== 问答模式(非流式) === 输入: " + userInput.trim());

        // 保存用户输入到历史
        addToHistory("user", userInput.trim());

        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", getModel());
                requestJson.put("messages", buildChatMessages(userInput));
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.7);
                requestJson.put("max_tokens", 1024);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(getApiUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + getApiKey())
                        .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "chatWithAi HTTP " + response.code() + ": " + errBody);
                        getMainHandler().post(() -> callback.onError("蓝心大模型暂时不可用，请稍后重试"));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    String content = extractContentFromResponse(responseBody);

                    if (content == null || content.isEmpty()) {
                        getMainHandler().post(() -> callback.onError("蓝心大模型未返回有效内容"));
                        return;
                    }

                    // 保存 AI 回复到历史
                    addToHistory("assistant", content);

                    Log.d(TAG, "问答模式回复成功, 长度=" + content.length());
                    String finalContent = content;
                    getMainHandler().post(() -> callback.onSuccess(finalContent));
                }

            } catch (IOException e) {
                Log.e(TAG, "chatWithAi 网络错误: " + e.getMessage());
                getMainHandler().post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "chatWithAi 错误: " + e.getMessage());
                getMainHandler().post(() -> callback.onError("对话出错，请重试"));
            }
        });
    }

    /**
     * 流式问答对话：SSE 逐字输出，带对话记忆
     */
    public void chatWithAiStream(String userInput, StreamChatCallback callback) {
        if (userInput == null || userInput.trim().isEmpty()) {
            getMainHandler().post(() -> callback.onError("输入内容为空"));
            return;
        }

        Log.d(TAG, "=== 问答模式(流式) === 输入: " + userInput.trim());

        // 保存用户输入到历史
        addToHistory("user", userInput.trim());

        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", getModel());
                requestJson.put("messages", buildChatMessages(userInput));
                requestJson.put("stream", true);  // 启用流式
                requestJson.put("temperature", 0.7);
                requestJson.put("max_tokens", 1024);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(getApiUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + getApiKey())
                        .addHeader("Accept", "text/event-stream")
                        .build();

                StringBuilder fullReply = new StringBuilder();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "chatWithAiStream HTTP " + response.code() + ": " + errBody);
                        getMainHandler().post(() -> callback.onError("蓝心大模型暂时不可用，请稍后重试"));
                        return;
                    }

                    getMainHandler().post(callback::onStart);

                    if (response.body() == null) {
                        getMainHandler().post(() -> callback.onError("响应为空"));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(response.body().charStream());
                    String line;
                    boolean receivedAnyContent = false;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "SSE raw line: " + line);
                        // 蓝心大模型 SSE 格式: "data:{...}" (无空格)
                        // 标准 OpenAI SSE 格式: "data: {...}" (有空格)
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
                                org.json.JSONArray choices = json.optJSONArray("choices");
                                if (choices != null && choices.length() > 0) {
                                    JSONObject choice = choices.getJSONObject(0);
                                    // 尝试 delta 格式（流式标准）
                                    JSONObject delta = choice.optJSONObject("delta");
                                    if (delta != null) {
                                        String content = delta.optString("content", "");
                                        if (!content.isEmpty()) {
                                            receivedAnyContent = true;
                                            fullReply.append(content);
                                            String currentText = fullReply.toString();
                                            getMainHandler().post(() -> callback.onDelta(currentText, false));
                                        }
                                    } else {
                                        // 兼容：某些接口可能直接返回 message.content
                                        JSONObject message = choice.optJSONObject("message");
                                        if (message != null) {
                                            String content = message.optString("content", "");
                                            if (!content.isEmpty()) {
                                                receivedAnyContent = true;
                                                fullReply.append(content);
                                                String currentText = fullReply.toString();
                                                getMainHandler().post(() -> callback.onDelta(currentText, false));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "SSE parse error: " + e.getMessage() + " | data=" + data);
                            }
                        }
                    }

                    // 保存完整回复到历史
                    String finalReply = fullReply.toString();
                    if (!finalReply.isEmpty()) {
                        addToHistory("assistant", finalReply);
                    }

                    Log.d(TAG, "流式回复完成, 总长度=" + finalReply.length() + ", receivedAnyContent=" + receivedAnyContent);
                    getMainHandler().post(() -> callback.onDelta(finalReply, true));
                }

            } catch (IOException e) {
                Log.e(TAG, "chatWithAiStream 网络错误: " + e.getMessage());
                getMainHandler().post(() -> callback.onError("网络连接失败，请检查网络后重试"));
            } catch (Exception e) {
                Log.e(TAG, "chatWithAiStream 错误: " + e.getMessage());
                getMainHandler().post(() -> callback.onError("对话出错，请重试"));
            }
        });
    }

    public Schedule parseScheduleFromTextSync(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return parseLocally(text.trim());
    }

    // ========== 静态工具方法 ==========
    public String generateAiReminder(Schedule schedule) {
        return generatePersonalizedReminder(schedule, schedule.getTitle());
    }

    public static String generateAiReminderText(String workType) {
        VivoAiService service = new VivoAiService();
        Schedule schedule = new Schedule();
        schedule.setWorkType(workType);
        schedule.setTitle(workType);
        return service.generatePersonalizedReminder(schedule, workType);
    }

    public static String[] getToolsForWorkType(String workType) {
        VivoAiService service = new VivoAiService();
        return service.getToolsForWorkTypeAndDevice(workType, null);
    }

    // ==================== 知识库 AI 处理 ====================

    /**
     * 知识提取回调
     */
    public interface KnowledgeCallback {
        /** @param title    AI 提取的标题（约 20 字内）
         *  @param summary  AI 生成的摘要（约 100 字）
         */
        void onSuccess(String title, String summary);
        void onError(String errorMessage);
    }

    /**
     * 调用 AI 从原始内容中提取标题和摘要
     * @param rawContent 用户录入的原始内容
     * @param callback   结果回调（主线程）
     */
    public void extractKnowledgeSummary(String rawContent, KnowledgeCallback callback) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            getMainHandler().post(() -> callback.onError("内容为空"));
            return;
        }

        new Thread(() -> {
            try {
                String prompt = "请从以下内容中提取一个简洁标题（不超过20字）和一段摘要（100字以内，突出核心要点）。\n"
                        + "按如下格式输出，不要添加其他内容：\n"
                        + "标题：xxx\n"
                        + "摘要：xxx\n\n"
                        + "内容：\n" + rawContent.trim();

                org.json.JSONArray messages = new org.json.JSONArray();
                org.json.JSONObject sysMsg = new org.json.JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", "你是一个专业的知识整理助手，善于提炼要点。");
                messages.put(sysMsg);
                org.json.JSONObject userMsg = new org.json.JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("model", AiConfig.VIVO_MODEL);
                body.put("messages", messages);
                body.put("max_tokens", 300);
                body.put("temperature", 0.3);

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(AiConfig.VIVO_API_URL)
                        .post(okhttp3.RequestBody.create(body.toString(), JSON))
                        .addHeader("Authorization", AiConfig.authHeader())
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (okhttp3.Response resp = getHttpClient().newCall(request).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        getMainHandler().post(() -> callback.onError("AI 请求失败"));
                        return;
                    }
                    String respStr = resp.body().string();
                    org.json.JSONObject json = new org.json.JSONObject(respStr);
                    String content = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content").trim();

                    // 解析标题和摘要
                    String title = "";
                    String summary = "";
                    for (String line : content.split("\n")) {
                        line = line.trim();
                        if (line.startsWith("标题：") || line.startsWith("标题:")) {
                            title = line.substring(3).trim();
                        } else if (line.startsWith("摘要：") || line.startsWith("摘要:")) {
                            summary = line.substring(3).trim();
                        }
                    }
                    // 兜底：如果解析失败，用原文前20字作标题，原文前100字作摘要
                    if (title.isEmpty()) {
                        title = rawContent.length() > 20 ? rawContent.substring(0, 20) + "..." : rawContent;
                    }
                    if (summary.isEmpty()) {
                        summary = rawContent.length() > 100 ? rawContent.substring(0, 100) + "..." : rawContent;
                    }
                    final String finalTitle = title;
                    final String finalSummary = summary;
                    getMainHandler().post(() -> callback.onSuccess(finalTitle, finalSummary));
                }
            } catch (Exception e) {
                Log.e(TAG, "extractKnowledgeSummary error", e);
                getMainHandler().post(() -> callback.onError("AI 解析出错: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 知识提取回调（含思维导图）
     */
    public interface KnowledgeWithMindMapCallback {
        void onSuccess(String title, String summary, String mindMapJson);
        void onError(String errorMessage);
    }

    /**
     * 调用 AI 从原始内容中提取标题、摘要和思维导图
     */
    public void extractKnowledgeWithMindMap(String rawContent, KnowledgeWithMindMapCallback callback) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            getMainHandler().post(() -> callback.onError("内容为空"));
            return;
        }

        new Thread(() -> {
            try {
                String systemPrompt =
                    "你是专业的知识整理助手。将用户提供的维修知识内容整理为结构化数据。\n\n"
                    + "必须严格返回以下 JSON 格式，不要添加任何其他文字：\n"
                    + "{\n"
                    + "  \"title\": \"提炼的标题（20字以内）\",\n"
                    + "  \"summary\": \"知识总结（150-200字）\",\n"
                    + "  \"mindMap\": {\n"
                    + "    \"root\": \"中心主题\",\n"
                    + "    \"children\": [\n"
                    + "      {\"text\": \"一级分支1\", \"children\": [{\"text\":\"二级要点1\"},{\"text\":\"二级要点2\"}]},\n"
                    + "      {\"text\": \"一级分支2\", \"children\": [{\"text\":\"二级要点1\"}]}\n"
                    + "    ]\n"
                    + "  }\n"
                    + "}";

                JSONObject requestJson = new JSONObject();
                requestJson.put("model", AiConfig.VIVO_MODEL);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 1500);

                JSONArray messages = new JSONArray();
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                messages.put(sys);

                JSONObject user = new JSONObject();
                user.put("role", "user");
                user.put("content", rawContent);
                messages.put(user);

                requestJson.put("messages", messages);

                OkHttpClient client = getHttpClient().newBuilder()
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                        requestJson.toString(), JSON);
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(AiConfig.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", AiConfig.authHeader())
                        .build();

                try (okhttp3.Response response = client.newCall(req).execute()) {
                    if (!response.isSuccessful()) {
                        getMainHandler().post(() -> callback.onError("AI 服务不可用"));
                        return;
                    }
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    String content = extractContentFromResponse(bodyStr);
                    if (content == null || content.isEmpty()) {
                        getMainHandler().post(() -> callback.onError("AI 未返回内容"));
                        return;
                    }

                    String json = content.trim();
                    if (json.startsWith("```json")) json = json.substring(7);
                    else if (json.startsWith("```")) json = json.substring(3);
                    if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
                    json = json.trim();
                    JSONObject obj = new JSONObject(json);
                    String title = obj.optString("title", "");
                    String summary = obj.optString("summary", "");
                    JSONObject mindMap = obj.optJSONObject("mindMap");
                    String mindMapJson = mindMap != null ? mindMap.toString() : "";

                    getMainHandler().post(() -> callback.onSuccess(title, summary, mindMapJson));
                }
            } catch (Exception e) {
                Log.e(TAG, "extractKnowledgeWithMindMap error", e);
                getMainHandler().post(() -> callback.onError("AI 解析出错: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 流式生成 AI 个性化提醒（SSE 逐字输出，复用 StreamChatCallback）
     */
    public void generateReminderStream(String title, String workType, String address,
                                        StreamChatCallback callback) {
        String prompt = buildReminderPrompt(
            title != null ? title : "",
            workType != null ? workType : "",
            address != null ? address : ""
        );

        Log.d(TAG, "generateReminderStream: calling stream API");

        executor.execute(() -> {
            try {
                JSONObject requestJson = new JSONObject();
                requestJson.put("model", getModel());

                org.json.JSONArray messages = new org.json.JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是一个专业的家政服务管家，专门为维修师傅提供出发前准备建议。请用中文回答，语气专业友好。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", true);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 800);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON);
                Request request = new Request.Builder()
                        .url(getApiUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + getApiKey())
                        .addHeader("Accept", "text/event-stream")
                        .build();

                StringBuilder fullReply = new StringBuilder();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "(empty)";
                        Log.e(TAG, "generateReminderStream HTTP " + response.code() + ": " + errBody);
                        getMainHandler().post(() -> callback.onError("蓝心大模型暂时不可用"));
                        return;
                    }

                    getMainHandler().post(callback::onStart);

                    if (response.body() == null) {
                        getMainHandler().post(() -> callback.onError("响应为空"));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(response.body().charStream());
                    String line;
                    while ((line = reader.readLine()) != null) {
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
                                org.json.JSONArray choices = json.optJSONArray("choices");
                                if (choices != null && choices.length() > 0) {
                                    JSONObject choice = choices.getJSONObject(0);
                                    JSONObject delta = choice.optJSONObject("delta");
                                    if (delta != null) {
                                        String content = delta.optString("content", "");
                                        if (!content.isEmpty()) {
                                            fullReply.append(content);
                                            String currentText = fullReply.toString();
                                            getMainHandler().post(() -> callback.onDelta(currentText, false));
                                        }
                                    } else {
                                        JSONObject message = choice.optJSONObject("message");
                                        if (message != null) {
                                            String content = message.optString("content", "");
                                            if (!content.isEmpty()) {
                                                fullReply.append(content);
                                                String currentText = fullReply.toString();
                                                getMainHandler().post(() -> callback.onDelta(currentText, false));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "SSE parse error: " + e.getMessage());
                            }
                        }
                    }

                    String finalReply = fullReply.toString();
                    if (finalReply.isEmpty()) {
                        Schedule tmp = new Schedule();
                        tmp.setWorkType(workType);
                        tmp.setTitle(title);
                        String fallback = generatePersonalizedReminder(tmp, title);
                        getMainHandler().post(() -> callback.onDelta(fallback, true));
                    } else {
                        getMainHandler().post(() -> callback.onDelta(finalReply, true));
                    }

                    Log.d(TAG, "generateReminderStream 完成, 长度=" + finalReply.length());
                }

            } catch (IOException e) {
                Log.e(TAG, "generateReminderStream 网络错误: " + e.getMessage());
                Schedule tmp = new Schedule();
                tmp.setWorkType(workType);
                tmp.setTitle(title);
                String fallback = generatePersonalizedReminder(tmp, title);
                getMainHandler().post(() -> callback.onDelta(fallback, true));
            } catch (Exception e) {
                Log.e(TAG, "generateReminderStream 错误: " + e.getMessage());
                getMainHandler().post(() -> callback.onError("AI 生成出错，请重试"));
            }
        });
    }
}
