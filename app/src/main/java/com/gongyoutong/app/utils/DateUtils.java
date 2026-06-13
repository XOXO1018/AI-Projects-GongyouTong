package com.gongyoutong.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日期工具类
 * 统一管理日期相关的工具方法，避免代码重复
 */
public final class DateUtils {

    // ========== 静态日期格式化器（线程安全）==========
    private static final ConcurrentHashMap<String, SimpleDateFormat> FORMATTERS =
            new ConcurrentHashMap<>();

    private static final String FMT_TIME = "HH:mm";
    private static final String FMT_DATE = "yyyy-MM-dd";
    private static final String FMT_DATETIME = "yyyy-MM-dd HH:mm";
    private static final String FMT_DISPLAY_DATE = "MM/dd HH:mm";
    private static final String FMT_MONTH_DAY = "M月d日";
    private static final String FMT_MONTH_DAY_TIME = "M月d日 HH:mm";

    private DateUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取日期格式化器（线程安全）
     */
    private static SimpleDateFormat getFormatter(String pattern) {
        return FORMATTERS.computeIfAbsent(pattern,
                k -> new SimpleDateFormat(pattern, Locale.CHINA));
    }

    // ========== 格式化方法 ==========

    /**
     * 格式化时间为 HH:mm
     */
    public static String formatTime(Date date) {
        if (date == null) return "--:--";
        return getFormatter(FMT_TIME).format(date);
    }

    /**
     * 格式化日期为 yyyy-MM-dd
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        return getFormatter(FMT_DATE).format(date);
    }

    /**
     * 格式化日期时间为 yyyy-MM-dd HH:mm
     */
    public static String formatDateTime(Date date) {
        if (date == null) return "";
        return getFormatter(FMT_DATETIME).format(date);
    }

    /**
     * 格式化显示日期为 MM/dd HH:mm
     */
    public static String formatDisplayDateTime(Date date) {
        if (date == null) return "时间待定";
        return getFormatter(FMT_DISPLAY_DATE).format(date);
    }

    /**
     * 格式化显示日期为 M月d日
     */
    public static String formatMonthDay(Date date) {
        if (date == null) return "";
        return getFormatter(FMT_MONTH_DAY).format(date);
    }

    /**
     * 格式化显示日期时间为 M月d日 HH:mm
     */
    public static String formatMonthDayTime(Date date) {
        if (date == null) return "时间待定";
        return getFormatter(FMT_MONTH_DAY_TIME).format(date);
    }

    // ========== 日期比较方法 ==========

    /**
     * 判断两个日期是否是同一天
     */
    public static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) return false;
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 判断两个日期是否是同一天（使用 Date 对象）
     */
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        return isSameDay(cal1, cal2);
    }

    /**
     * 判断指定日期是否是今天
     */
    public static boolean isToday(Date date) {
        if (date == null) return false;
        Calendar today = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        return isSameDay(today, target);
    }

    /**
     * 判断指定日期是否是明天
     */
    public static boolean isTomorrow(Date date) {
        if (date == null) return false;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        return isSameDay(tomorrow, target);
    }

    /**
     * 判断指定日期是否是昨天
     */
    public static boolean isYesterday(Date date) {
        if (date == null) return false;
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        return isSameDay(yesterday, target);
    }

    /**
     * 获取友好的日期时间显示（如：今天 10:00）
     */
    public static String getFriendlyDateTime(Date date) {
        if (date == null) return "时间待定";

        Calendar today = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTime(date);

        String timeStr = formatTime(date);

        if (isSameDay(today, target)) {
            return "今天 " + timeStr;
        } else {
            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            if (isSameDay(tomorrow, target)) {
                return "明天 " + timeStr;
            } else {
                return formatDisplayDateTime(date);
            }
        }
    }

    /**
     * 计算两个日期之间的时间差（毫秒）
     */
    public static long getTimeDiff(Date date1, Date date2) {
        if (date1 == null || date2 == null) return 0;
        return date1.getTime() - date2.getTime();
    }

    /**
     * 计算距离现在的时间差描述
     */
    public static String getTimeUntil(Date targetDate) {
        if (targetDate == null) return "";

        long diff = targetDate.getTime() - System.currentTimeMillis();
        if (diff <= 0) return "已到期";

        long hours = diff / (60 * 60 * 1000);
        long minutes = (diff % (60 * 60 * 1000)) / (60 * 1000);
        long days = hours / 24;

        if (days >= 1) {
            return days + "天后";
        } else if (hours >= 1) {
            return hours + "小时后";
        } else if (minutes >= 1) {
            return minutes + "分钟后";
        } else {
            return "即将到期";
        }
    }

    /**
     * 判断日期是否已过期
     */
    public static boolean isExpired(Date date) {
        if (date == null) return false;
        return date.getTime() < System.currentTimeMillis();
    }

    /**
     * 获取日期中的小时（24小时制）
     */
    public static int getHour(Date date) {
        if (date == null) return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取日期中的分钟
     */
    public static int getMinute(Date date) {
        if (date == null) return 0;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal.get(Calendar.MINUTE);
    }
}
