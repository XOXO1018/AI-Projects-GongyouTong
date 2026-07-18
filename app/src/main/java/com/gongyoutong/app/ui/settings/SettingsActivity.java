package com.gongyoutong.app.ui.settings;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.gongyoutong.app.R;
import com.gongyoutong.app.utils.ThemeManager;

import java.util.Locale;

/**
 * 地图设置页面
 *
 * 功能：
 * 1. SDK 初始化（setAgreePrivacy + initialize）— 在此页面调用
 * 2. 地图预览 + 地图类型切换（普通/卫星/实时路况）
 * 3. 缩放控制（+/- 按钮 + 滑块）
 * 4. 当前位置显示（经纬度 + 地址）
 * 5. 导航设置（跟随模式 / 语音播报 / 缩放级别）
 *
 * 注意：SDK 已在 GongyouTongApplication 中全局初始化一次，
 * 此页面再次调用是幂等的，仅用于在此触发隐私同意确认。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_LOCATION_PERMISSION = 1001;

    // === 地图核心 ===
    private MapView mapView;
    private BaiduMap baiduMap;
    private LocationClient locationClient;

    // === 控件 ===
    private TextView tvCurrentCoords;
    private TextView tvInitStatus;
    private TextView tvSdkVersion;
    private TextView tvSdkStatus;
    private TextView tvCoordType;
    private TextView tvAppPackage;
    private TextView tvZoomLevel;
    private ImageButton btnZoomIn;
    private ImageButton btnZoomOut;
    private FloatingActionButton fabResetMap;
    private LinearLayout rgMapType;
    private SwitchMaterial switchFollowLocation;
    private SwitchMaterial switchVoice;
    private Slider sliderZoom;

    // === 数据 ===
    private SharedPreferences prefs;
    private LatLng currentLocation;
    private String currentAddress = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private float currentZoom = 16f;
    private boolean firstLocationReceived = false;
    private ThemeManager themeManager;
    private LinearLayout rgThemeMode;
    private RadioButton rbDay, rbNight, rbSystem;
    private RadioButton rbNormal, rbSatellite, rbTraffic;
    private BDLocationListener locationListener;

    // === 百度地图 SDK 两个核心初始化方法 ===
    // SDKInitializer.setAgreePrivacy()  和 SDKInitializer.initialize()
    // 这些方法已在 Application.onCreate() 中被全局调用一次，
    // 此处再次调用是幂等的（安全），同时触发隐私同意确认。

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // =============================================
        // 百度地图 SDK 初始化（全部包裹在 try-catch 中，防止崩溃）
        // =============================================
        try {
            SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
            com.baidu.location.LocationClient.setAgreePrivacy(true);
            Log.i(TAG, "setAgreePrivacy(true) 调用成功");
        } catch (Exception e) {
            Log.e(TAG, "setAgreePrivacy failed: " + e.getMessage(), e);
        }

        try {
            SDKInitializer.initialize(getApplicationContext());
            Log.i(TAG, "SDKInitializer.initialize() 调用成功");
        } catch (Exception e) {
            Log.e(TAG, "SDKInitializer.initialize failed: " + e.getMessage(), e);
        }

        try {
            SDKInitializer.setCoordType(CoordType.BD09LL);
            Log.i(TAG, "setCoordType(BD09LL) 设置完成");
        } catch (Exception e) {
            Log.e(TAG, "setCoordType failed: " + e.getMessage(), e);
        }

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("map_settings", MODE_PRIVATE);
        themeManager = ThemeManager.getInstance(this);

        initViews();
        setupThemeToggle();
        setupToolbar();
        try {
            setupMap();
        } catch (Exception e) {
            Log.e(TAG, "setupMap failed: " + e.getMessage(), e);
        }
        try {
            setupMapControls();
        } catch (Exception e) {
            Log.e(TAG, "setupMapControls failed: " + e.getMessage(), e);
        }
        try {
            setupMapTypeToggle();
        } catch (Exception e) {
            Log.e(TAG, "setupMapTypeToggle failed: " + e.getMessage(), e);
        }
        setupSettingsControls();
        try {
            checkLocationPermission();
        } catch (Exception e) {
            Log.e(TAG, "checkLocationPermission failed: " + e.getMessage(), e);
        }
        updateSdkInfo();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        tvInitStatus.setText("SDK 已初始化");
        tvInitStatus.setTextColor(0xFF4CAF50);
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        tvCurrentCoords = findViewById(R.id.tvCurrentCoords);
        tvInitStatus = findViewById(R.id.tvInitStatus);
        tvSdkVersion = findViewById(R.id.tvSdkVersion);
        tvSdkStatus = findViewById(R.id.tvSdkStatus);
        tvCoordType = findViewById(R.id.tvCoordType);
        tvAppPackage = findViewById(R.id.tvAppPackage);
        tvZoomLevel = findViewById(R.id.tvZoomLevel);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        fabResetMap = findViewById(R.id.fabResetMap);
        rgMapType = findViewById(R.id.rgMapType);
        switchFollowLocation = findViewById(R.id.switchFollowLocation);
        switchVoice = findViewById(R.id.switchVoice);
        sliderZoom = findViewById(R.id.sliderZoom);

        // 恢复保存的设置
        switchFollowLocation.setChecked(prefs.getBoolean("follow_location", true));
        switchVoice.setChecked(prefs.getBoolean("voice_nav", true));
        currentZoom = prefs.getFloat("zoom_level", 16f);
        sliderZoom.setValue(currentZoom);
        tvZoomLevel.setText((int) currentZoom + "x");
    }

    private void setupThemeToggle() {
        rgThemeMode = findViewById(R.id.rgThemeMode);
        rbDay = findViewById(R.id.rbDay);
        rbNight = findViewById(R.id.rbNight);
        rbSystem = findViewById(R.id.rbSystem);

        LinearLayout layoutDay = findViewById(R.id.layoutThemeDay);
        LinearLayout layoutNight = findViewById(R.id.layoutThemeNight);
        LinearLayout layoutSystem = findViewById(R.id.layoutThemeSystem);

        // 恢复保存的主题设置
        int savedMode = themeManager.getThemeMode();
        updateThemeRadioButtons(savedMode);

        layoutDay.setOnClickListener(v -> selectTheme(ThemeManager.MODE_DAY));
        layoutNight.setOnClickListener(v -> selectTheme(ThemeManager.MODE_NIGHT));
        layoutSystem.setOnClickListener(v -> selectTheme(ThemeManager.MODE_SYSTEM));
    }

    private void selectTheme(int mode) {
        if (themeManager.getThemeMode() == mode) return;
        updateThemeRadioButtons(mode);
        themeManager.setThemeMode(mode);
        recreate();
    }

    private void updateThemeRadioButtons(int mode) {
        rbDay.setChecked(mode == ThemeManager.MODE_DAY);
        rbNight.setChecked(mode == ThemeManager.MODE_NIGHT);
        rbSystem.setChecked(mode == ThemeManager.MODE_SYSTEM);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v ->
            getOnBackPressedDispatcher().onBackPressed()
        );
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void setupMap() {
        try {
            baiduMap = mapView.getMap();
            if (baiduMap != null) {
                // 设置地图类型（默认普通）
                baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
                // 开启我的位置图层
                baiduMap.setMyLocationEnabled(true);
                // 设置定位模式
                baiduMap.setMyLocationConfiguration(
                    new MyLocationConfiguration(
                        MyLocationConfiguration.LocationMode.NORMAL, true, null
                    )
                );
                // 设置缩放级别
                currentZoom = prefs.getFloat("zoom_level", 16f);
                MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.zoomTo(currentZoom);
                baiduMap.setMapStatus(mapStatusUpdate);
                Log.d(TAG, "Map initialized, zoom=" + currentZoom);
            } else {
                Log.e(TAG, "baiduMap is null — SDK 可能未正确初始化");
                tvInitStatus.setText("地图初始化失败");
                tvInitStatus.setTextColor(0xFFFF5722);
            }
        } catch (Exception e) {
            Log.e(TAG, "setupMap exception: " + e.getMessage(), e);
            tvInitStatus.setText("地图加载异常: " + e.getMessage());
            tvInitStatus.setTextColor(0xFFFF5722);
        }
    }

    private void setupMapControls() {
        // 放大
        btnZoomIn.setOnClickListener(v -> {
            if (currentZoom < 21f) {
                currentZoom++;
                applyZoom(currentZoom);
                sliderZoom.setValue(currentZoom);
                tvZoomLevel.setText((int) currentZoom + "x");
            }
        });

        // 缩小
        btnZoomOut.setOnClickListener(v -> {
            if (currentZoom > 3f) {
                currentZoom--;
                applyZoom(currentZoom);
                sliderZoom.setValue(currentZoom);
                tvZoomLevel.setText((int) currentZoom + "x");
            }
        });

        // 重置地图到当前位置
        fabResetMap.setOnClickListener(v -> {
            if (currentLocation != null) {
                MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(currentLocation, currentZoom);
                baiduMap.animateMapStatus(update);
                // Toast.makeText(this, "地图已重置", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "正在定位，请稍候...", Toast.LENGTH_SHORT).show();
            }
        });

        // 缩放滑块
        sliderZoom.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                currentZoom = value;
                applyZoom(currentZoom);
                tvZoomLevel.setText((int) currentZoom + "x");
                prefs.edit().putFloat("zoom_level", value).apply();
            }
        });

        // 监听地图状态变化（实时同步缩放级别）
        if (baiduMap != null) {
            baiduMap.setOnMapStatusChangeListener(new BaiduMap.OnMapStatusChangeListener() {
                @Override
                public void onMapStatusChangeStart(MapStatus mapStatus) {}
                @Override
                public void onMapStatusChangeStart(MapStatus mapStatus, int i) {}
                @Override
                public void onMapStatusChange(MapStatus mapStatus) {
                    currentZoom = mapStatus.zoom;
                    if (!sliderZoom.isPressed()) {
                        sliderZoom.setValue(currentZoom);
                        tvZoomLevel.setText((int) currentZoom + "x");
                    }
                }
                @Override
                public void onMapStatusChangeFinish(MapStatus mapStatus) {}
            });
        }
    }

    private void applyZoom(float zoom) {
        if (baiduMap != null) {
            MapStatusUpdate update = MapStatusUpdateFactory.zoomTo(zoom);
            baiduMap.setMapStatus(update);
        }
    }

    private void setupMapTypeToggle() {
        rbNormal = findViewById(R.id.rbNormal);
        rbSatellite = findViewById(R.id.rbSatellite);
        rbTraffic = findViewById(R.id.rbTraffic);

        LinearLayout layoutNormal = findViewById(R.id.layoutMapNormal);
        LinearLayout layoutSatellite = findViewById(R.id.layoutMapSatellite);
        LinearLayout layoutTraffic = findViewById(R.id.layoutMapTraffic);

        // 恢复保存的地图类型
        String savedType = prefs.getString("map_type", "normal");
        updateMapRadioButtons(savedType);

        layoutNormal.setOnClickListener(v -> applyMapType("normal"));
        layoutSatellite.setOnClickListener(v -> applyMapType("satellite"));
        layoutTraffic.setOnClickListener(v -> applyMapType("traffic"));
    }

    private void updateMapRadioButtons(String type) {
        rbNormal.setChecked("normal".equals(type));
        rbSatellite.setChecked("satellite".equals(type));
        rbTraffic.setChecked("traffic".equals(type));
    }

    private void applyMapType(String type) {
        updateMapRadioButtons(type);
        prefs.edit().putString("map_type", type).apply();

        if (baiduMap == null) return;

        if ("satellite".equals(type)) {
            baiduMap.setMapType(BaiduMap.MAP_TYPE_SATELLITE);
            baiduMap.setTrafficEnabled(false);
        } else if ("traffic".equals(type)) {
            baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
            baiduMap.setTrafficEnabled(true);
        } else {
            baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
            baiduMap.setTrafficEnabled(false);
        }
    }

    private void setupSettingsControls() {
        // 自动跟随定位开关
        switchFollowLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("follow_location", isChecked).apply();
            Log.d(TAG, "follow_location = " + isChecked);
        });

        // 语音播报开关
        switchVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("voice_nav", isChecked).apply();
            Log.d(TAG, "voice_nav = " + isChecked);
        });
    }

    private void updateSdkInfo() {
        tvSdkVersion.setText("百度地图 SDK 版本：7.6.0（地图）/ 9.6.0（定位）/ 7.6.0（搜索）");
        tvSdkStatus.setText("AK：vzPA8jeqjVqTabbT8nLJap4Bw5Kg2imn");
        tvCoordType.setText("坐标系：bd09ll（百度经纬度）");
        tvAppPackage.setText("应用包名：" + getPackageName());
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMISSION);
        } else {
            startLocation();
        }
    }

    private void startLocation() {
        try {
            Log.d(TAG, "开始初始化定位服务...");
            locationClient = new LocationClient(getApplicationContext());
            Log.d(TAG, "LocationClient 实例化成功");
        } catch (Exception e) {
            Log.e(TAG, "定位服务初始化失败: " + e.getMessage(), e);
            mainHandler.post(() ->
                Toast.makeText(this, "定位服务初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
            return;
        }

        // =============================================
        // 【关键配置】LocationClientOption — 以下参数缺一不可
        // =============================================
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy); // 高精度（GPS + 网络）
        option.setCoorType("bd09ll"); // 百度坐标系
        option.setScanSpan(3000); // 定位间隔 3 秒
        option.setOpenGps(true); // 开启 GPS
        option.setNeedDeviceDirect(true); // 【重要】获取设备方向（需要陀螺仪）
        option.setIsNeedAddress(true); // 【重要】获取地址信息
        option.setLocationNotify(false); // 定位结果变化时不通知（避免频繁）
        option.setIgnoreKillProcess(false); // 进程被杀死时停止定位
        option.SetIgnoreCacheException(false);
        locationClient.setLocOption(option);
        Log.d(TAG, "LocationClientOption 配置完成");

        // 注册定位回调 — 必须在 start() 之前注册
        locationListener = new BDLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation bdLocation) {
                if (bdLocation == null || mapView == null) {
                    Log.w(TAG, "onReceiveLocation: bdLocation=" + bdLocation + ", mapView=" + mapView);
                    return;
                }

                // 打印定位结果日志
                Log.d(TAG, "定位成功: lat=" + bdLocation.getLatitude()
                    + ", lng=" + bdLocation.getLongitude()
                    + ", addr=" + bdLocation.getAddrStr()
                    + ", locType=" + bdLocation.getLocType()
                    + ", radius=" + bdLocation.getRadius());

                // 打印定位类型说明
                int locType = bdLocation.getLocType();
                String locTypeDesc = getLocTypeDesc(locType);
                Log.d(TAG, "定位类型: " + locType + " (" + locTypeDesc + ")");

                // 只处理有效定位结果（GPS或网络定位成功）
                if (locType != BDLocation.TypeGpsLocation
                    && locType != BDLocation.TypeNetWorkLocation
                    && locType != BDLocation.TypeOffLineLocation) {
                    Log.w(TAG, "无效定位类型，跳过: " + locTypeDesc);
                    mainHandler.post(() ->
                        tvCurrentCoords.setText("定位中...（" + locTypeDesc + "）")
                    );
                    return;
                }

                currentLocation = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                currentAddress = bdLocation.getAddrStr() != null ? bdLocation.getAddrStr() : "";

                mainHandler.post(() -> {
                    // 更新坐标显示
                    String coordStr = String.format(Locale.US, "%.6f, %.6f",
                        bdLocation.getLatitude(), bdLocation.getLongitude());
                    String displayAddr = currentAddress.isEmpty() ? coordStr : currentAddress + " (" + coordStr + ")";
                    tvCurrentCoords.setText(displayAddr);

                    // 更新地图我的位置
                    if (baiduMap != null) {
                        MyLocationData locData = new MyLocationData.Builder()
                            .accuracy(bdLocation.getRadius())
                            .direction(bdLocation.getDirection())
                            .latitude(bdLocation.getLatitude())
                            .longitude(bdLocation.getLongitude())
                            .build();
                        baiduMap.setMyLocationData(locData);
                    }

                    // 首次定位自动移动到当前位置
                    if (!firstLocationReceived) {
                        firstLocationReceived = true;
                        if (currentLocation != null && baiduMap != null) {
                            MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(currentLocation, currentZoom);
                            baiduMap.animateMapStatus(update);
                            Log.d(TAG, "地图已移动到当前位置");
                        }
                    }
                });
            }

            public void onConnectHotSpotMessage(String s, String s1) {
                // 热点连接状态回调，忽略
            }

            public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
                // 诊断信息回调
                Log.w(TAG, "定位诊断: type=" + locType + ", diag=" + diagnosticType + ", msg=" + diagnosticMessage);
            }
        };
        locationClient.registerLocationListener(locationListener);

        // 启动定位服务
        locationClient.start();
        Log.d(TAG, "LocationClient.start() called");

        // 如果启用了自动跟随，将地图设为跟随模式
        if (prefs.getBoolean("follow_location", true) && baiduMap != null) {
            baiduMap.setMyLocationConfiguration(
                new MyLocationConfiguration(
                    MyLocationConfiguration.LocationMode.FOLLOWING, true, null
                )
            );
        }
    }

    /**
     * 将百度定位类型代码转为可读描述
     */
    private String getLocTypeDesc(int locType) {
        switch (locType) {
            case BDLocation.TypeGpsLocation: return "GPS定位";
            case BDLocation.TypeNetWorkLocation: return "网络定位";
            case BDLocation.TypeOffLineLocation: return "离线定位";
            case BDLocation.TypeCacheLocation: return "缓存定位";
            default: return "未知(" + locType + ")";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocation();
            } else {
                tvCurrentCoords.setText("定位权限被拒绝");
                Toast.makeText(this, "需要定位权限才能显示当前位置", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (locationClient != null) {
            if (locationListener != null) {
                locationClient.unRegisterLocationListener(locationListener);
            }
            locationClient.stop();
        }
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }
}
