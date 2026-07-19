package com.gongyoutong.app;

/**
 * 应用配置类
 * 存放所有第三方服务和业务配置信息
 *
 * 注意：大模型能力配置已转移到 AiConfig（com.gongyoutong.app.ai），
 *       请统一通过 AiConfig.XXX 访问。
 */
public class Config {

    // ==================== 百度地图 配置 ====================
    // 请在 https://lbsyun.baidu.com/ 注册应用获取 AK
    public static final String BAIDU_MAP_AK = "vzPA8jeqjVqTabbT8nLJap4Bw5Kg2imn";

    // ==================== 数据库配置 ====================
    public static final String DATABASE_NAME = "gongyoutong_db";
    public static final int DATABASE_VERSION = 11;

    // ==================== SharedPreferences 配置 ====================
    public static final String PREFS_NAME = "gongyoutong_prefs";

    // ==================== Intent Extra Keys ====================
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_WORKORDER_ID = "workorder_id";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";

    // ==================== 维修业务配置 ====================
    public static final int MAX_PHOTOS = 9;               // 最大照片数量
    public static final int PHOTO_MAX_SIZE_PX = 800;      // 照片长边最大像素
    public static final int PHOTO_QUALITY = 70;            // JPEG 压缩质量 (0-100)
    public static final String EXTRA_DIAGNOSIS_ID = "extra_diagnosis_id"; // 诊断记录ID Extra Key

    // ========== CameraX 配置 ==========
    public static final int CAMERA_FRAME_INTERVAL_MS = 2500;       // 帧捕获间隔 2.5秒
    public static final int CAMERA_FRAME_MAX_WIDTH = 640;          // 帧缩放最大宽度
    public static final int CAMERA_FRAME_QUALITY = 70;             // JPEG 压缩质量
}
