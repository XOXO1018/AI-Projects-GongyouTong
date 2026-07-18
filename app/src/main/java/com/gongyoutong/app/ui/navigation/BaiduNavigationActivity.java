package com.gongyoutong.app.ui.navigation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.RouteLine;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.route.DrivingRouteLine;
import com.baidu.mapapi.search.route.DrivingRoutePlanOption;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRouteResult;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.gongyoutong.app.R;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleDao;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.ui.detail.ScheduleDetailActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BaiduNavigationActivity extends AppCompatActivity {

    private static final String TAG = "BaiduNav";
    private static final int REQUEST_LOCATION_PERMISSION = 1001;

    private MapView mapView;
    private BaiduMap baiduMap;
    private LocationClient locationClient;
    private GeoCoder geoCoder;
    private RoutePlanSearch routePlanSearch;

    private TextView tvNavSubtitle;
    private MaterialToolbar toolbar;
    private TextView tvTurnInstruction, tvNextTurn, tvRemaining;
    private TextView tvProgress, tvEta, tvRemainingTime, tvSpeed;
    private ImageView ivTurnIcon;
    private MaterialButton btnArrived;
    private FloatingActionButton fabRecenter;
    private LinearLayout btnReport, btnReportIssue;
    private com.google.android.material.button.MaterialButton btnStartNav;

    private LatLng currentLocation;
    private LatLng destinationLocation;
    private String destinationAddress;
    private String scheduleId;
    private String navState = "idle"; // idle | loading | routed | navigating | navigating_paused

    private DrivingRouteLine currentRouteLine;
    private List<Overlay> routeOverlays = new ArrayList<>();

    private AppDatabase database;
    private ScheduleDao scheduleDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentCityName = "全国"; // 默认城市，定位成功后会更新
    private boolean isGeoCoderReady = false;
    private boolean isRouteSearchReady = false;
    private boolean isLocationReady = false;
    private volatile boolean isActivityActive = true;
    private BDLocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate started");
        super.onCreate(savedInstanceState);

        try {
            // ========== 1. 隐私政策同意 ==========
            try {
                com.baidu.location.LocationClient.setAgreePrivacy(true);
                SDKInitializer.setAgreePrivacy(getApplicationContext(), true);
            } catch (Exception e) {
                Log.w(TAG, "Privacy setup failed: " + e.getMessage());
            }

            // ========== 2. 加载布局 ==========
            setContentView(R.layout.activity_baidu_navigation);
            Log.d(TAG, "Layout set successfully");

            // ========== 3. 获取Intent参数 ==========
            destinationAddress = getIntent().getStringExtra("address");
            scheduleId = getIntent().getStringExtra("schedule_id");
            double destLat = getIntent().getDoubleExtra("latitude", 0);
            double destLng = getIntent().getDoubleExtra("longitude", 0);
            if (destLat != 0 && destLng != 0) {
                destinationLocation = new LatLng(destLat, destLng);
                Log.d(TAG, "Direct coordinates: " + destLat + "," + destLng);
            }
            Log.d(TAG, "Address: " + destinationAddress + ", ID: " + scheduleId);

            // ========== 4. 初始化数据库 ==========
            database = AppDatabase.getInstance(this);
            scheduleDao = database.scheduleDao();

            // ========== 5. 初始化视图 ==========
            initViews();
            Log.d(TAG, "Views initialized");

            // ========== 6. 设置Toolbar ==========
            setupToolbar();

            // ========== 7. 设置地图 ==========
            setupMap();

            // ========== 8. 设置地理编码器 ==========
            setupGeoCoder();

            // ========== 9. 设置路线规划 ==========
            setupRoutePlanSearch();

            // ========== 10. 设置定位 ==========
            setupLocation();

            // ========== 11. 设置点击事件 ==========
            setupClickListeners();
            setupBackHandler();

            // ========== 12. 初始化UI状态 ==========
            initUIState();

            // ========== 13. 检查定位权限并开始定位 ==========
            checkLocationPermission();

            Log.d(TAG, "onCreate completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "CRASH in onCreate: " + e.getMessage(), e);
            showErrorAndFinish("导航页面初始化失败: " + e.getMessage());
        }
    }

    /**
     * 显示错误并安全退出
     */
    private void showErrorAndFinish(String message) {
        try {
            if (isActivityActive) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {}
        try {
            if (!isFinishing()) {
                finish();
            }
        } catch (Exception ignored) {}
    }

    /**
     * 检查Activity是否处于活跃状态
     */
    private boolean isActivityActive() {
        return isActivityActive && !isFinishing() && !isDestroyed();
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        tvNavSubtitle = findViewById(R.id.tvNavSubtitle);
        tvTurnInstruction = findViewById(R.id.tvTurnInstruction);
        tvNextTurn = findViewById(R.id.tvNextTurn);
        tvRemaining = findViewById(R.id.tvRemaining);
        tvProgress = findViewById(R.id.tvProgress);
        tvEta = findViewById(R.id.tvEta);
        tvRemainingTime = findViewById(R.id.tvRemainingTime);
        tvSpeed = findViewById(R.id.tvSpeed);
        ivTurnIcon = findViewById(R.id.ivTurnIcon);
        btnArrived = findViewById(R.id.btnArrived);
        fabRecenter = findViewById(R.id.fabRecenter);
        btnReport = findViewById(R.id.btnReport);
        btnReportIssue = findViewById(R.id.btnReportIssue);
        btnStartNav = findViewById(R.id.btnStartNav);

        // 安全检查
        if (mapView == null || tvTurnInstruction == null || btnStartNav == null) {
            throw new RuntimeException("Required views not found in layout");
        }
    }

    private void initUIState() {
        if (destinationAddress != null && !destinationAddress.isEmpty()) {
            toolbar.setTitle(destinationAddress);
            if (destinationLocation != null) {
                tvTurnInstruction.setText("已获取目的地坐标");
                tvNextTurn.setText("定位后自动规划路线");
                addDestinationMarker(destinationLocation);
            } else {
                tvTurnInstruction.setText("正在定位...");
                tvNextTurn.setText("定位后自动规划路线");
            }
            tvRemaining.setText("--");
        } else {
            tvTurnInstruction.setText("未获取到目的地地址");
            tvNextTurn.setText("请返回重新选择日程");
            Toast.makeText(this, "地址为空，无法规划路线", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupToolbar() {
        try {
            toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "setupToolbar failed: " + e.getMessage());
        }
    }

    private void setupMap() {
        try {
            if (mapView == null) {
                Log.e(TAG, "mapView is null");
                return;
            }
            baiduMap = mapView.getMap();
            if (baiduMap != null) {
                baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
                baiduMap.setMyLocationEnabled(true);
                baiduMap.setMyLocationConfiguration(
                    new MyLocationConfiguration(
                        MyLocationConfiguration.LocationMode.NORMAL, true, null
                    )
                );
                MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.zoomTo(16f);
                baiduMap.setMapStatus(mapStatusUpdate);
                Log.d(TAG, "MapView initialized successfully");
            } else {
                Log.w(TAG, "baiduMap is null - SDK may not be properly initialized");
                // 即使地图初始化失败，也继续显示页面
                mainHandler.post(() ->
                    Toast.makeText(this, "地图加载失败，但您仍可使用其他功能", Toast.LENGTH_SHORT).show()
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "setupMap exception: " + e.getMessage(), e);
            // 不崩溃，继续显示页面
        }
    }

    private void setupGeoCoder() {
        try {
            geoCoder = GeoCoder.newInstance();
            if (geoCoder == null) {
                Log.w(TAG, "GeoCoder.newInstance() returned null");
                return;
            }

            geoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
                @Override
                public void onGetGeoCodeResult(GeoCodeResult result) {
                    if (!isActivityActive()) {
                        Log.w(TAG, "onGetGeoCodeResult: Activity not active, ignoring");
                        return;
                    }

                    if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                        mainHandler.post(() -> {
                            if (!isActivityActive()) return;
                            safeSetText(tvTurnInstruction, "地址解析失败");
                            safeSetText(tvNextTurn, "请检查地址是否正确");
                            safeSetText(tvRemaining, "--");
                            safeToast("无法定位目的地地址，请确认地址正确");
                        });
                        return;
                    }

                    destinationLocation = result.getLocation();
                    Log.d(TAG, "Geocoding success: " + destinationLocation.latitude + "," + destinationLocation.longitude);

                    mainHandler.post(() -> {
                        if (!isActivityActive()) return;
                        try {
                            addDestinationMarker(destinationLocation);
                            if (currentLocation != null) {
                                planRoute(currentLocation, destinationLocation);
                            } else {
                                safeSetText(tvNextTurn, "定位中，定位完成后自动规划路线...");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing geocode result: " + e.getMessage());
                        }
                    });
                }

                @Override
                public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
                    // 暂不需要
                }
            });

            isGeoCoderReady = true;
            Log.d(TAG, "GeoCoder setup complete");

        } catch (Exception e) {
            Log.e(TAG, "setupGeoCoder failed: " + e.getMessage(), e);
            // 即使GeoCoder失败，也继续显示页面
        }
    }

    private void setupRoutePlanSearch() {
        try {
            routePlanSearch = RoutePlanSearch.newInstance();
            if (routePlanSearch == null) {
                Log.w(TAG, "RoutePlanSearch.newInstance() returned null");
                return;
            }

            routePlanSearch.setOnGetRoutePlanResultListener(new OnGetRoutePlanResultListener() {
                @Override
                public void onGetDrivingRouteResult(DrivingRouteResult result) {
                    if (!isActivityActive()) {
                        Log.w(TAG, "onGetDrivingRouteResult: Activity not active, ignoring");
                        return;
                    }

                    if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                        mainHandler.post(() -> {
                            if (!isActivityActive()) return;
                            safeSetText(tvTurnInstruction, "路线规划失败");
                            safeSetText(tvNextTurn, "请检查网络或地址");
                            navState = "idle";
                            safeToast("路线规划失败，请确认地址正确");
                        });
                        return;
                    }

                    List<DrivingRouteLine> routeLines = result.getRouteLines();
                    if (routeLines == null || routeLines.isEmpty()) {
                        mainHandler.post(() -> {
                            if (!isActivityActive()) return;
                            safeSetText(tvTurnInstruction, "路线规划失败");
                            safeSetText(tvNextTurn, "未找到可用路线");
                            navState = "idle";
                            safeToast("未找到可用路线");
                        });
                        return;
                    }

                    DrivingRouteLine routeLine = routeLines.get(0);
                    if (routeLine == null) {
                        mainHandler.post(() -> {
                            if (!isActivityActive()) return;
                            safeSetText(tvTurnInstruction, "路线规划失败");
                            safeSetText(tvNextTurn, "未找到可用路线");
                            navState = "idle";
                            safeToast("未找到可用路线");
                        });
                        return;
                    }

                    final DrivingRouteLine finalRouteLine = routeLine;
                    mainHandler.post(() -> {
                        if (!isActivityActive()) return;
                        try {
                            currentRouteLine = finalRouteLine;
                            displayRoute(finalRouteLine);
                            updateRouteInfo(finalRouteLine.getDistance(), finalRouteLine.getDuration());
                        } catch (Exception e) {
                            Log.e(TAG, "Error displaying route: " + e.getMessage());
                        }
                    });
                }

                @Override
                public void onGetWalkingRouteResult(WalkingRouteResult result) {}
                @Override
                public void onGetTransitRouteResult(TransitRouteResult result) {}
                @Override
                public void onGetBikingRouteResult(com.baidu.mapapi.search.route.BikingRouteResult result) {}
                @Override
                public void onGetIndoorRouteResult(com.baidu.mapapi.search.route.IndoorRouteResult result) {}
                @Override
                public void onGetMassTransitRouteResult(com.baidu.mapapi.search.route.MassTransitRouteResult result) {}
            });

            isRouteSearchReady = true;
            Log.d(TAG, "RoutePlanSearch setup complete");

        } catch (Exception e) {
            Log.e(TAG, "setupRoutePlanSearch failed: " + e.getMessage(), e);
        }
    }

    private void setupLocation() {
        try {
            Log.d(TAG, "Starting location setup...");

            try {
                com.baidu.location.LocationClient.setAgreePrivacy(true);
            } catch (Exception e) {
                Log.w(TAG, "setAgreePrivacy already set: " + e.getMessage());
            }

            locationClient = new LocationClient(getApplicationContext());
            Log.d(TAG, "LocationClient instantiated");

        } catch (Exception e) {
            Log.e(TAG, "LocationClient instantiation failed: " + e.getMessage(), e);
            mainHandler.post(() -> {
                if (isActivityActive()) {
                    safeToast("定位服务初始化失败，请检查权限设置");
                }
            });
            return;
        }

        try {
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll");
            option.setScanSpan(5000);
            option.setOpenGps(true);
            option.setNeedDeviceDirect(true);
            option.setIsNeedAddress(true);
            option.setLocationNotify(false);
            option.setIgnoreKillProcess(true);
            option.SetIgnoreCacheException(false);
            option.setEnableSimulateGps(false);
            locationClient.setLocOption(option);
            Log.d(TAG, "LocationClientOption configured");

            locationListener = new BDLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation bdLocation) {
                    if (!isActivityActive() || bdLocation == null) {
                        Log.w(TAG, "onReceiveLocation: Activity not active or null location");
                        return;
                    }

                    int locType = bdLocation.getLocType();
                    Log.d(TAG, "Location callback: type=" + locType +
                        ", lat=" + bdLocation.getLatitude() +
                        ", lng=" + bdLocation.getLongitude());

                    // 只处理有效定位结果
                    if (locType != BDLocation.TypeGpsLocation
                        && locType != BDLocation.TypeNetWorkLocation
                        && locType != BDLocation.TypeOffLineLocation) {
                        // 定位类型无效，不处理
                        return;
                    }

                    currentLocation = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                    isLocationReady = true;

                    // 获取城市信息
                    if (bdLocation.getCity() != null && !bdLocation.getCity().isEmpty()) {
                        currentCityName = bdLocation.getCity();
                    } else if (bdLocation.getDistrict() != null && !bdLocation.getDistrict().isEmpty()) {
                        currentCityName = bdLocation.getDistrict();
                    }

                    mainHandler.post(() -> {
                        if (!isActivityActive()) return;
                        try {
                            updateMyLocation(bdLocation);
                            handleLocationUpdate();
                        } catch (Exception e) {
                            Log.e(TAG, "Error in location update: " + e.getMessage());
                        }
                    });
                }

                public void onConnectHotSpotMessage(String s, String s1) {}

                public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
                    Log.w(TAG, "Location diagnostics: type=" + locType +
                        ", diag=" + diagnosticType + ", msg=" + diagnosticMessage);
                }
            };
            locationClient.registerLocationListener(locationListener);

            Log.d(TAG, "Location listener registered");

        } catch (Exception e) {
            Log.e(TAG, "Location setup failed: " + e.getMessage(), e);
            mainHandler.post(() -> {
                if (isActivityActive()) {
                    safeToast("定位选项设置失败");
                }
            });
        }
    }

    private void updateMyLocation(BDLocation bdLocation) {
        if (baiduMap == null || bdLocation == null) return;

        try {
            MyLocationData locData = new MyLocationData.Builder()
                .accuracy(bdLocation.getRadius())
                .direction(bdLocation.getDirection())
                .latitude(bdLocation.getLatitude())
                .longitude(bdLocation.getLongitude())
                .build();
            baiduMap.setMyLocationData(locData);

            if (bdLocation.getSpeed() >= 0) {
                safeSetText(tvSpeed, (int) bdLocation.getSpeed() + " km/h");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating my location: " + e.getMessage());
        }
    }

    private void handleLocationUpdate() {
        if (navState.equals("idle")) {
            safeSetText(tvTurnInstruction, "已定位到当前位置");
            safeSetText(tvNextTurn, "正在规划路线...");
            navState = "loading";

            if (destinationLocation != null) {
                planRoute(currentLocation, destinationLocation);
            } else if (destinationAddress != null && !destinationAddress.isEmpty()) {
                searchDestination();
            }
        }

        if (navState.equals("navigating")) {
            updateNavigationProgress();
        }
    }

    private void setupClickListeners() {
        // Re-center FAB
        fabRecenter.setOnClickListener(v -> {
            if (currentLocation != null && baiduMap != null) {
                try {
                    MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(currentLocation);
                    baiduMap.animateMapStatus(update);
                } catch (Exception e) {
                    Log.e(TAG, "Error recentering: " + e.getMessage());
                }
            } else {
                safeToast("正在定位...");
            }
        });

        // Arrived button
        btnArrived.setOnClickListener(v -> showArrivalDialog());

        // Start Navigation button
        btnStartNav.setOnClickListener(v -> {
            if ("routed".equals(navState) || "navigating_paused".equals(navState)) {
                startInAppNavigation();
            } else if ("navigating".equals(navState)) {
                pauseNavigation();
            }
        });

        // Report button
        btnReport.setOnClickListener(v -> safeToast("位置已上报"));

        // Report Issue button
        btnReportIssue.setOnClickListener(v -> {
            String[] options = {"路况问题", "导航偏离", "定位不准", "目的地无法到达", "其他"};
            if (!isActivityActive()) return;
            try {
                new AlertDialog.Builder(this)
                    .setTitle("上报问题")
                    .setItems(options, (d, which) -> safeToast("已提交: " + options[which]))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing dialog: " + e.getMessage());
            }
        });
    }

    private void startInAppNavigation() {
        navState = "navigating";

        if (baiduMap != null) {
            try {
                baiduMap.setMyLocationConfiguration(
                    new MyLocationConfiguration(
                        MyLocationConfiguration.LocationMode.FOLLOWING, true, null
                    )
                );

                if (currentLocation != null) {
                    MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(currentLocation, 17f);
                    baiduMap.animateMapStatus(update);
                }
            } catch (Exception e) {
                Log.e(TAG, "startInAppNavigation error: " + e.getMessage());
            }
        }

        safeSetText(btnStartNav, "暂停导航");
        safeToast("导航已开始，地图将跟随您的位置");

        if (currentLocation != null && destinationLocation != null) {
            updateNavigationProgress();
        }
    }

    private void pauseNavigation() {
        navState = "navigating_paused";

        if (baiduMap != null) {
            try {
                baiduMap.setMyLocationConfiguration(
                    new MyLocationConfiguration(
                        MyLocationConfiguration.LocationMode.NORMAL, true, null
                    )
                );

                if (currentLocation != null && destinationLocation != null) {
                    double centerLat = (currentLocation.latitude + destinationLocation.latitude) / 2.0;
                    double centerLng = (currentLocation.longitude + destinationLocation.longitude) / 2.0;
                    double latDiff = Math.abs(currentLocation.latitude - destinationLocation.latitude);
                    double lngDiff = Math.abs(currentLocation.longitude - destinationLocation.longitude);
                    double maxDiff = Math.max(latDiff, lngDiff);
                    float zoomLevel = maxDiff > 0.1f ? 10f : maxDiff > 0.05f ? 12f : maxDiff > 0.02f ? 14f : 16f;
                    baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(new LatLng(centerLat, centerLng), zoomLevel));
                }
            } catch (Exception e) {
                Log.e(TAG, "pauseNavigation error: " + e.getMessage());
            }
        }

        safeSetText(btnStartNav, "继续导航");
        safeToast("导航已暂停");
    }

    private void searchDestination() {
        if (destinationAddress == null || destinationAddress.isEmpty()) {
            safeSetText(tvTurnInstruction, "地址为空");
            safeSetText(tvNextTurn, "无法规划路线");
            return;
        }

        if (!isGeoCoderReady || geoCoder == null) {
            Log.w(TAG, "GeoCoder not ready, cannot search destination");
            safeSetText(tvTurnInstruction, "地理编码服务未就绪");
            safeSetText(tvNextTurn, "请检查网络连接后重试");
            return;
        }

        safeSetText(tvTurnInstruction, "正在解析地址...");
        safeSetText(tvNextTurn, "请稍候...");

        try {
            GeoCodeOption geoCodeOption = new GeoCodeOption();
            geoCodeOption.address(destinationAddress);
            geoCodeOption.city(currentCityName);
            Log.d(TAG, "Geocoding: " + destinationAddress + " in " + currentCityName);
            geoCoder.geocode(geoCodeOption);
        } catch (Exception e) {
            Log.e(TAG, "Geocoding failed: " + e.getMessage());
            safeSetText(tvTurnInstruction, "地址解析出错");
            safeSetText(tvNextTurn, "请检查网络连接");
            safeToast("地理编码失败: " + e.getMessage());
        }
    }

    private void planRoute(LatLng start, LatLng end) {
        if (start == null || end == null) return;

        if (baiduMap == null) {
            Log.w(TAG, "planRoute: baiduMap is null");
            safeSetText(tvTurnInstruction, "地图未就绪");
            safeSetText(tvNextTurn, "请检查地图配置");
            return;
        }

        if (!isRouteSearchReady || routePlanSearch == null) {
            Log.w(TAG, "planRoute: routePlanSearch not ready");
            safeSetText(tvTurnInstruction, "路线规划服务未就绪");
            safeSetText(tvNextTurn, "请检查网络连接");
            return;
        }

        safeSetText(tvTurnInstruction, "正在规划路线...");
        safeSetText(tvNextTurn, "请稍候...");
        navState = "loading";

        try {
            PlanNode startNode = PlanNode.withLocation(start);
            PlanNode endNode = PlanNode.withLocation(end);

            DrivingRoutePlanOption drivingOption = new DrivingRoutePlanOption()
                .from(startNode)
                .to(endNode);

            routePlanSearch.drivingSearch(drivingOption);
        } catch (Exception e) {
            Log.e(TAG, "planRoute failed: " + e.getMessage());
            safeSetText(tvTurnInstruction, "路线规划失败");
            safeSetText(tvNextTurn, "请检查网络连接");
            navState = "idle";
            safeToast("路线规划失败: " + e.getMessage());
        }
    }

    private void displayRoute(DrivingRouteLine routeLine) {
        if (baiduMap == null) {
            Log.w(TAG, "displayRoute: baiduMap is null");
            return;
        }
        clearRouteOverlays();

        try {
            List<DrivingRouteLine.DrivingStep> steps = routeLine.getAllStep();
            int stepIndex = 0;

            for (DrivingRouteLine.DrivingStep step : steps) {
                if (step.getWayPoints() == null || step.getWayPoints().isEmpty()) continue;

                PolylineOptions polylineOptions = new PolylineOptions()
                    .points(step.getWayPoints())
                    .width(16)
                    .dottedLine(false)
                    .color(Color.parseColor("#FF6B35"))
                    .zIndex(7);

                Polyline polyline = (Polyline) baiduMap.addOverlay(polylineOptions);
                routeOverlays.add(polyline);

                if (stepIndex == 0) {
                    try {
                        BitmapDescriptor startIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_location);
                        MarkerOptions startMarker = new MarkerOptions()
                            .position(step.getWayPoints().get(0))
                            .icon(startIcon)
                            .zIndex(10);
                        routeOverlays.add(baiduMap.addOverlay(startMarker));
                    } catch (Exception e) {
                        Log.w(TAG, "Error adding start marker: " + e.getMessage());
                    }
                }

                stepIndex++;
            }

            // Zoom to show full route
            if (currentLocation != null && destinationLocation != null) {
                double centerLat = (currentLocation.latitude + destinationLocation.latitude) / 2.0;
                double centerLng = (currentLocation.longitude + destinationLocation.longitude) / 2.0;
                LatLng center = new LatLng(centerLat, centerLng);

                double latDiff = Math.abs(currentLocation.latitude - destinationLocation.latitude);
                double lngDiff = Math.abs(currentLocation.longitude - destinationLocation.longitude);
                double maxDiff = Math.max(latDiff, lngDiff);

                float zoomLevel;
                if (maxDiff > 0.1) zoomLevel = 10f;
                else if (maxDiff > 0.05) zoomLevel = 12f;
                else if (maxDiff > 0.02) zoomLevel = 14f;
                else zoomLevel = 16f;

                MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(center, zoomLevel);
                baiduMap.setMapStatus(update);
            }

            navState = "routed";

            // Show first step instruction
            if (steps != null && !steps.isEmpty()) {
                DrivingRouteLine.DrivingStep firstStep = steps.get(0);
                String instruction = firstStep.getInstructions();
                safeSetText(tvTurnInstruction, instruction != null && !instruction.isEmpty()
                    ? instruction : "路线规划完成，可以出发");

                if (steps.size() > 1) {
                    String nextInstruction = steps.get(1).getInstructions();
                    safeSetText(tvNextTurn, "下一路口: " + (nextInstruction != null ? nextInstruction : "继续直行"));
                } else {
                    safeSetText(tvNextTurn, "即将到达目的地");
                }
            } else {
                safeSetText(tvTurnInstruction, "路线规划完成");
                safeSetText(tvNextTurn, "点击「开始导航」出发");
            }

            btnStartNav.setEnabled(true);
            safeSetText(btnStartNav, "开始导航");

        } catch (Exception e) {
            Log.e(TAG, "displayRoute error: " + e.getMessage(), e);
            safeSetText(tvTurnInstruction, "路线显示失败");
            navState = "idle";
        }
    }

    private void clearRouteOverlays() {
        for (Overlay overlay : routeOverlays) {
            if (overlay != null) {
                try {
                    overlay.remove();
                } catch (Exception e) {
                    Log.w(TAG, "Error removing overlay: " + e.getMessage());
                }
            }
        }
        routeOverlays.clear();
    }

    private void addDestinationMarker(LatLng position) {
        if (baiduMap == null || position == null) return;
        try {
            BitmapDescriptor bitmap = BitmapDescriptorFactory.fromResource(R.drawable.ic_location);
            MarkerOptions options = new MarkerOptions()
                .position(position)
                .icon(bitmap)
                .zIndex(9);
            routeOverlays.add(baiduMap.addOverlay(options));
        } catch (Exception e) {
            Log.w(TAG, "Error adding destination marker: " + e.getMessage());
        }
    }

    private void updateRouteInfo(int distanceMeters, int durationSeconds) {
        String distanceStr;
        if (distanceMeters >= 1000) {
            distanceStr = String.format("%.1f km", distanceMeters / 1000.0);
        } else {
            distanceStr = distanceMeters + " m";
        }

        String timeStr;
        if (durationSeconds >= 3600) {
            int hours = durationSeconds / 3600;
            int mins = (durationSeconds % 3600) / 60;
            timeStr = hours + "小时" + mins + "分钟";
        } else if (durationSeconds >= 60) {
            timeStr = durationSeconds / 60 + "分钟";
        } else {
            timeStr = durationSeconds + "秒";
        }

        safeSetText(tvEta, timeStr);
        safeSetText(tvRemaining, distanceStr);
        safeSetText(tvRemainingTime, "预计 " + timeStr);
        Log.d(TAG, "Route info: " + distanceStr + " / " + timeStr);
    }

    private void updateNavigationProgress() {
        if (currentLocation == null || destinationLocation == null) return;

        try {
            double latDiff = destinationLocation.latitude - currentLocation.latitude;
            double lngDiff = destinationLocation.longitude - currentLocation.longitude;
            double distDeg = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff);
            double distMeters = distDeg * 111000;

            if (distMeters < 50) {
                safeSetText(tvRemaining, "到达");
                safeSetText(tvTurnInstruction, "已到达目的地附近");
                safeSetText(tvNextTurn, "请注意寻找目的地入口");
            } else {
                String distStr = distMeters >= 1000
                    ? String.format("%.1f km", distMeters / 1000)
                    : (int) distMeters + " m";
                safeSetText(tvRemaining, distStr);

                int etaSeconds = (int) (distMeters / 13.9);
                int mins = etaSeconds / 60;
                safeSetText(tvRemainingTime, "预计 " + mins + " 分钟");
            }

            // Map follows current location
            if ("navigating".equals(navState) && baiduMap != null) {
                MapStatusUpdate update = MapStatusUpdateFactory.newLatLngZoom(currentLocation, 17f);
                baiduMap.animateMapStatus(update);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating navigation progress: " + e.getMessage());
        }
    }

    private void showArrivalDialog() {
        if (!isActivityActive()) return;
        try {
            new AlertDialog.Builder(this)
                .setTitle(R.string.arrival_tip)
                .setMessage(getString(R.string.arrival_message))
                .setPositiveButton(getString(R.string.end_work), (d, w) -> {
                    executor.execute(() -> {
                        if (scheduleId != null) {
                            ScheduleEntity entity = scheduleDao.getById(scheduleId);
                            if (entity != null) {
                                entity.setStatus("已完成");
                                entity.setUpdatedAt(System.currentTimeMillis());
                                scheduleDao.update(entity);
                            }
                        }
                        mainHandler.post(() -> {
                            if (!isActivityActive()) return;
                            safeToast(R.string.schedule_completed);
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("distance", tvRemaining.getText().toString());
                            setResult(RESULT_OK, resultIntent);
                            if (scheduleId != null) {
                                Intent intent = new Intent(BaiduNavigationActivity.this,
                                    ScheduleDetailActivity.class);
                                intent.putExtra("schedule_id", scheduleId);
                                startActivity(intent);
                            }
                            finish();
                        });
                    });
                })
                .setNegativeButton(R.string.continue_navigation, null)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing arrival dialog: " + e.getMessage());
        }
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isActivityActive()) return;
                try {
                    new AlertDialog.Builder(BaiduNavigationActivity.this)
                        .setTitle(R.string.exit_navigation)
                        .setMessage(R.string.exit_navigation_message)
                        .setPositiveButton(R.string.confirm, (d, w) -> finish())
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                } catch (Exception e) {
                    Log.e(TAG, "Error showing back dialog: " + e.getMessage());
                    finish();
                }
            }
        });
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
        if (locationClient == null) {
            Log.w(TAG, "startLocation: locationClient is null");
            // 即使定位服务不可用，也保持页面显示
            safeSetText(tvTurnInstruction, "定位服务不可用");
            safeSetText(tvNextTurn, "请检查定位权限");
            return;
        }
        try {
            if (locationClient.isStarted()) {
                locationClient.stop();
            }
            locationClient.start();
            Log.d(TAG, "LocationClient started");
        } catch (Exception e) {
            Log.e(TAG, "startLocation failed: " + e.getMessage());
            safeToast("启动定位服务失败");
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
                safeToast("需要定位权限才能使用导航功能");
                // 即使没有权限，也保持在页面
                safeSetText(tvTurnInstruction, "定位权限未授予");
                safeSetText(tvNextTurn, "请在设置中开启定位权限");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        if (mapView != null) {
            try {
                mapView.onResume();
            } catch (Exception e) {
                Log.w(TAG, "mapView.onResume failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            try {
                mapView.onPause();
            } catch (Exception e) {
                Log.w(TAG, "mapView.onPause failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy started");
        isActivityActive = false;

        // Stop location client
        if (locationClient != null) {
            try {
                if (locationClient.isStarted()) {
                    locationClient.stop();
                }
                if (locationListener != null) {
                    locationClient.unRegisterLocationListener(locationListener);
                }
            } catch (Exception e) {
                Log.w(TAG, "LocationClient cleanup failed: " + e.getMessage());
            }
        }

        // Destroy GeoCoder
        if (geoCoder != null) {
            try {
                geoCoder.destroy();
            } catch (Exception e) {
                Log.w(TAG, "GeoCoder destroy failed: " + e.getMessage());
            }
        }

        // Destroy RoutePlanSearch
        if (routePlanSearch != null) {
            try {
                routePlanSearch.destroy();
            } catch (Exception e) {
                Log.w(TAG, "RoutePlanSearch destroy failed: " + e.getMessage());
            }
        }

        // Clear overlays
        clearRouteOverlays();

        // Destroy MapView
        if (mapView != null) {
            try {
                mapView.onDestroy();
            } catch (Exception e) {
                Log.w(TAG, "MapView destroy failed: " + e.getMessage());
            }
        }

        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            if (executor != null) executor.shutdown();
        }

        super.onDestroy();
        Log.d(TAG, "onDestroy completed");
    }

    // ========== Safe helper methods ==========

    private void safeSetText(TextView textView, String text) {
        if (!isActivityActive() || textView == null) return;
        try {
            textView.setText(text);
        } catch (Exception e) {
            Log.w(TAG, "safeSetText failed: " + e.getMessage());
        }
    }

    private void safeToast(String message) {
        if (!isActivityActive()) return;
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "safeToast failed: " + e.getMessage());
        }
    }

    private void safeToast(int resId) {
        if (!isActivityActive()) return;
        try {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "safeToast(int) failed: " + e.getMessage());
        }
    }

    // ========== Legacy method for compatibility ==========
    private void startTurnByTurnNavigation() {
        startInAppNavigation();
    }
}
