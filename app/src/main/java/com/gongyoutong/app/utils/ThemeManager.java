package com.gongyoutong.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 主题管理器 - 管理日间/夜间模式切换
 */
public class ThemeManager {

    private static final String PREFS_NAME = "gyt_theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    public static final int MODE_DAY = 0;
    public static final int MODE_NIGHT = 1;
    public static final int MODE_SYSTEM = 2;

    private static ThemeManager instance;

    private final SharedPreferences prefs;

    private ThemeManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, MODE_DAY);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyTheme(mode);
    }

    public void applyTheme(int mode) {
        int nightMode;
        switch (mode) {
            case MODE_NIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case MODE_SYSTEM:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
            case MODE_DAY:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public void applySavedTheme() {
        applyTheme(getThemeMode());
    }

    public boolean isNightMode() {
        return getThemeMode() == MODE_NIGHT;
    }

    public String getModeLabel(int mode) {
        switch (mode) {
            case MODE_DAY: return "日间模式";
            case MODE_NIGHT: return "夜间模式";
            case MODE_SYSTEM: return "跟随系统";
            default: return "日间模式";
        }
    }
}
