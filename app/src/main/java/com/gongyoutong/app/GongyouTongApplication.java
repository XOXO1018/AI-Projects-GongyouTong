package com.gongyoutong.app;

import android.app.Application;
import android.util.Log;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.ISDKInitializerListener;
import com.baidu.mapapi.SDKInitializer;
import com.gongyoutong.app.utils.ThemeManager;

/**
 * 应用全局 Application 类
 * 百度地图 SDK 必须在这里做一次性初始化，不能在 Activity 中重复调用
 *
 * 如果地图显示空白/灰色，请检查：
 * 1. AK 是否在 https://lbsyun.baidu.com/ 控制台配置了包名(com.gongyoutong.app)和 SHA1
 * 2. 运行 get_sha1.bat 获取当前签名的 SHA1，填入控制台
 * 3. 手机需要联网才能验证 AK
 */
public class GongyouTongApplication extends Application {

    private static final String TAG = "GongyouTongApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化主题（必须在 setContentView 之前，所以在 Application 中设置）
        ThemeManager.getInstance(this).applySavedTheme();

        initBaiduMapSDK();
    }

    /**
     * 初始化百度地图 SDK
     * 注意：setAgreePrivacy 必须在 initialize 之前调用
     *
     * SDK 7.6.0 API 说明：
     * - setAgreePrivacy()  —— 同意隐私政策（必须最先调用）
     * - initialize()       —— 初始化 SDK
     * - setCoordType()     —— 设置坐标系
     * - ISDKInitializerListener —— SDK 初始化完成回调（无认证状态，仅表示 SDK 加载完毕）
     *
     * 注意：SDKInitializer 没有 SDKAuthListener / setAuthInfo，
     * AK 认证失败只会导致地图不显示，无法通过回调捕获，
     * 只能从 Logcat 过滤 "BaiduMapSDK" 查看认证错误。
     */
    private void initBaiduMapSDK() {
        try {
            // 0. LocationClient 隐私政策（必须在所有百度SDK初始化之前）
            com.baidu.location.LocationClient.setAgreePrivacy(true);

            // 1. 必须先同意隐私政策，否则 SDK 不会工作
            SDKInitializer.setAgreePrivacy(getApplicationContext(), true);

            // 2. 初始化 SDK（只调用一次），传入初始化完成监听
            SDKInitializer.initialize(getApplicationContext(), new ISDKInitializerListener() {
                @Override
                public void initializerFinish() {
                    Log.i(TAG, "✅ 百度地图 SDK 初始化完成（initializerFinish 回调）");
                    Log.i(TAG, "如果地图仍然空白，请检查 AK 是否在 lbsyun.baidu.com 配置了正确的包名和 SHA1");
                }
            });

            // 3. 设置坐标系为百度坐标系（bd09ll）
            SDKInitializer.setCoordType(CoordType.BD09LL);

            Log.i(TAG, "Baidu Map SDK initialize() called.");
        } catch (Exception e) {
            Log.e(TAG, "Baidu Map SDK init failed: " + e.getMessage(), e);
        }
    }
}
