plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.gongyoutong.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gongyoutong.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ✅ 签名配置（正确 Kotlin DSL 写法）
    signingConfigs {
        create("release") {
            storeFile = file("../GongyouTong")
            storePassword = "12345678"
            keyAlias = "GongyouTongkey"
            keyPassword = "12345678"
        }
    }

    buildTypes {
        release {
            // ✅ 绑定签名
            signingConfig = signingConfigs.getByName("release")
            // 启用代码压缩和混淆（减小 APK 大小）
            isMinifyEnabled = true
            // 启用资源压缩（移除未使用的资源）
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 调试版本不混淆，便于排查问题
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // 修复 16 KB 页面大小对齐问题（Android 15+ 要求）
    // 百度地图 SDK 7.6.0 的 .so 库未对齐，使用 legacy packaging 避免安装失败
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // 忽略 16KB 对齐警告（临时方案，待百度地图 SDK 升级后移除）
    androidResources {
        noCompress += listOf("tflite", "lite")
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.swiperefreshlayout)

    // Material Design
    implementation(libs.material)
    implementation(libs.flexbox)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Room Database
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    // =========================================================
    // 百度地图 SDK（统一使用 Maven AAR，不再依赖 libs/BaiduLBS_Android.jar）
    // Maven AAR 已包含地图、定位、搜索、工具类，不会与 JAR 冲突
    // =========================================================
    // ⚠️ 注意：如果你之前手动配置了 BaiduLBS_Android.jar，请删除 libs/BaiduLBS_Android.jar
    //         以避免 com.baidu.bdhttpdns.* 重复类冲突
    //         当前 AAR 版本已完整包含所有功能：
    //         地图 SDK（7.6.0）+ 定位 SDK（9.6.0）+ 搜索 SDK（7.6.0）+ 工具类（7.6.0）
    // =========================================================

    // Gson & OkHttp
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Glide for image loading
    implementation(libs.glide)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // 百度地图 SDK
    implementation("com.baidu.lbsyun:BaiduMapSDK_Map:7.6.0")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Location:9.6.0")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Search:7.6.0")
    implementation("com.baidu.lbsyun:BaiduMapSDK_Util:7.6.0")

    // CameraX (视频流 + 帧捕获 + 拍照)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// 修复 Android Studio "testClasses not found" 问题
tasks.whenTaskAdded {
    if (name == "testClasses") {
        // 任务已存在，无需额外操作
    }
}

// 如果 testClasses 任务不存在则注册一个空任务
if (tasks.findByName("testClasses") == null) {
    tasks.register("testClasses")
}