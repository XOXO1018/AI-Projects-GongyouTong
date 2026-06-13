package com.gongyoutong.app.repair;

import android.content.Context;
import android.content.SharedPreferences;

import com.gongyoutong.app.Config;
import com.gongyoutong.app.ai.OnlineKnowledgeService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 每日知识推荐管理器
 * 根据日期哈希从主题池中轮换选取不同的维修主题，支持已读标记和历史记录
 */
public class DailyRecommendationManager {

    private static final String PREFS_NAME = "daily_recommendation_prefs";
    private static final String KEY_LAST_DATE = "last_recommendation_date";
    private static final String KEY_DAILY_TOPICS = "daily_topics_json";
    private static final String KEY_READ_IDS = "read_recommendation_ids";
    private static final String KEY_HISTORY = "recommendation_history_json";

    private static final int DAILY_COUNT = 3;  // 每天推荐3个主题
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final SharedPreferences prefs;
    private final SimpleDateFormat dateFormat;

    public DailyRecommendationManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.CHINA);
    }

    /**
     * 获取今日推荐的主题列表
     * 如果今天还没生成，则从主题池中随机选取并持久化
     */
    public String[] getTodayTopics() {
        String today = dateFormat.format(new Date());
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (!today.equals(lastDate)) {
            // 新的一天，生成新推荐
            String[] newTopics = pickDailyTopics();
            saveTodayTopics(today, newTopics);
            return newTopics;
        }

        // 读取已保存的今日主题
        return loadTodayTopics();
    }

    /**
     * 获取今天的日期字符串（用于传递给 OnlineKnowledgeService）
     */
    public int getDayOffset() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 标记某条推荐为已读
     */
    public void markAsRead(String itemId) {
        String readStr = prefs.getString(KEY_READ_IDS, "");
        if (!readStr.contains(itemId)) {
            readStr += (readStr.isEmpty() ? "" : ",") + itemId;
            prefs.edit().putString(KEY_READ_IDS, readStr).apply();
        }
    }

    /**
     * 检查是否已读
     */
    public boolean isRead(String itemId) {
        String readStr = prefs.getString(KEY_READ_IDS, "");
        return readStr.contains(itemId);
    }

    /**
     * 获取推荐历史（最近7天）
     */
    public String getHistorySummary() {
        return prefs.getString(KEY_HISTORY, "暂无推荐记录");
    }

    /**
     * 添加历史记录
     */
    public void addHistory(String topic) {
        String today = dateFormat.format(new Date());
        String history = prefs.getString(KEY_HISTORY, "");
        String entry = today + " - " + topic;
        history = entry + "\n" + history;
        // 只保留最近 30 条
        String[] lines = history.split("\n");
        if (lines.length > 30) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append(lines[i]).append("\n");
            }
            history = sb.toString().trim();
        }
        prefs.edit().putString(KEY_HISTORY, history).apply();
    }

    /**
     * 获取所有推荐主题的历史列表
     */
    public List<String> getAllRecommendedTopics() {
        List<String> all = new ArrayList<>();
        String history = prefs.getString(KEY_HISTORY, "");
        if (!history.isEmpty()) {
            for (String line : history.split("\n")) {
                if (line.contains(" - ")) {
                    all.add(line.substring(line.indexOf(" - ") + 3));
                }
            }
        }
        // 加上今天的
        String[] today = loadTodayTopics();
        if (today != null) {
            for (String t : today) {
                if (!all.contains(t)) all.add(0, t);
            }
        }
        return all;
    }

    // ==================== 内部 ====================

    private String[] pickDailyTopics() {
        long seed = System.currentTimeMillis() / (24 * 3600 * 1000); // 按天取种子
        Random random = new Random(seed);

        String[] pool = OnlineKnowledgeService.TOPIC_POOL;
        int poolLen = pool.length;
        int count = Math.min(DAILY_COUNT, poolLen);

        // 使用 Fisher-Yates 部分洗牌
        String[] selected = new String[count];
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < poolLen; i++) indices.add(i);

        for (int i = 0; i < count; i++) {
            int pick = random.nextInt(indices.size());
            selected[i] = pool[indices.remove(pick)];
        }
        return selected;
    }

    private void saveTodayTopics(String date, String[] topics) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LAST_DATE, date);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topics.length; i++) {
            if (i > 0) sb.append("|||");
            sb.append(topics[i]);
        }
        editor.putString(KEY_DAILY_TOPICS, sb.toString());
        editor.apply();
    }

    private String[] loadTodayTopics() {
        String saved = prefs.getString(KEY_DAILY_TOPICS, "");
        if (saved.isEmpty()) return pickDailyTopics();
        return saved.split("\\|\\|\\|");
    }
}
