package com.gongyoutong.app.ui.repair;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.gongyoutong.app.Config;
import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.JoyAiVlConfig;
import com.gongyoutong.app.ai.JoyAiVlService;
import com.gongyoutong.app.ai.RepairLlmService;
import com.gongyoutong.app.ai.VideoGenerationService;
import com.gongyoutong.app.ai.VisionAnalysisService;
import com.gongyoutong.app.ai.VivoOcrService;
import com.gongyoutong.app.repair.ErrorDetector;
import com.gongyoutong.app.repair.FrameAnalysisResult;
import com.gongyoutong.app.repair.KnowledgeBaseService;
import com.gongyoutong.app.database.KnowledgeVectorEntity;
import com.gongyoutong.app.repair.RepairIntention;
import com.gongyoutong.app.repair.RepairState;
import com.gongyoutong.app.repair.RepairStateMachine;
import com.gongyoutong.app.repair.RepairStep;
import com.gongyoutong.app.repair.VoiceInteractionManager;
import com.gongyoutong.app.ui.repair.widget.ArOverlayView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 视频维修主界面 Activity。
 * 集成 CameraX 实时预览、AR 叠加标注、语音交互、AI 视觉分析和维修状态机。
 */
public class VideoRepairActivity extends AppCompatActivity
        implements RepairStateMachine.StateChangeListener {

    private static final String TAG = "VideoRepairActivity";
    private static final int CAMERA_PERMISSION_CODE = 200;

    // ========== UI 组件 ==========
    private PreviewView previewView;
    private ArOverlayView arOverlayView;
    private TextView tvRecordStatus;
    private TextView tvDeviceInfo;
    private TextView tvGuideText;
    private TextView tvStepProgress;
    private View layoutTopBar;
    private ImageButton btnPrevStep;
    private ImageButton btnSkipStep;
    private ImageButton btnTtsToggle;
    private ImageButton btnTakePhoto;
    private ImageButton btnGenerateVideo;
    private ImageButton btnCloseVideoRepair;
    private View btnVoiceInput;
    private TextView tvJoyAiStatus;
    private ProcessCameraProvider cameraProvider;

    // ========== 核心模块 ==========
    private RepairStateMachine stateMachine;
    private VoiceInteractionManager voiceManager;
    private ErrorDetector errorDetector;
    private KnowledgeBaseService kbService;
    private RepairLlmService llmService;
    private VivoOcrService ocrService;
    private VisionAnalysisService visionService;
    private VideoGenerationService videoGenService;
    private JoyAiVlService joyAiVlService;

    // ========== CameraX ==========
    private ExecutorService cameraExecutor;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private long lastFrameTime = 0L;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);

    // ========== 状态 ==========
    private boolean ttsEnabled = true;
    private boolean demoMode = false;
    private String deviceModel = "";
    private String faultDescription = "";
    private final Handler demoHandler = new Handler(Looper.getMainLooper());
    private int demoStepIndex = 0;
    private String lastSpokenDemoText = "";

    private final String[][] demoGuides = new String[][]{
            {"家用空调", "制冷效果差，出风口有异响", "请先断开空调电源，确认插头已拔下", "安全提醒：未断电前不要拆开外壳", "断电后检查滤网和出风口"},
            {"家用空调", "滤网积灰或风道堵塞", "取下前盖，观察滤网是否积灰", "注意不要用力掰断卡扣", "用软刷清理滤网并晾干"},
            {"家用空调", "排水管可能堵塞", "沿着排水管检查是否有弯折或积水", "安全提醒：不要让水进入电控盒", "疏通排水管并擦干周围水迹"},
            {"家用空调", "风扇或压缩机状态待确认", "短暂通电测试，听是否有异常噪声", "如出现焦味或异响，立即断电", "记录现象并准备生成维修报告"}
    };

    // ========================================================================
    // 生命周期
    // ========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_repair);

        // 接收从 RepairActivity 传入的上下文
        Intent intent = getIntent();
        faultDescription = intent.getStringExtra("fault_description");
        if (faultDescription == null) faultDescription = "";
        deviceModel = intent.getStringExtra("device_model");
        if (deviceModel == null) deviceModel = "";
        demoMode = intent.getBooleanExtra("demo_mode", false);

        initViews();
        setupStatusBarAvoidance();
        initModules();
        setupClickListeners();

        // 请求相机权限后启动 CameraX
        if (checkCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_PERMISSION_CODE);
        }

        if (demoMode) {
            tvJoyAiStatus.setText("DEMO");
            tvJoyAiStatus.setTextColor(0xFF4CAF50);
            startDemoGuidance();
            Toast.makeText(this, "已进入 AI 视频维修演示模式", Toast.LENGTH_SHORT).show();
        } else if (joyAiVlService.isEnabled()) {
            joyAiVlService.startSession();
            joyAiVlService.checkHealth(new JoyAiVlService.HealthCallback() {
                @Override
                public void onHealthy() {
                    Log.d(TAG, "JoyAI-VL 服务就绪");
                    runOnUiThread(() -> {
                        tvJoyAiStatus.setText("VL: \u2705");
                        tvJoyAiStatus.setTextColor(0xFF4CAF50);
                        Toast.makeText(VideoRepairActivity.this,
                                "JoyAI-VL 实时指导已连接", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onUnhealthy(String msg) {
                    Log.w(TAG, "JoyAI-VL 服务不可用: " + msg);
                    runOnUiThread(() -> {
                        tvJoyAiStatus.setText("VL: \u274C");
                        tvJoyAiStatus.setTextColor(0x80FFFFFF);
                        Toast.makeText(VideoRepairActivity.this,
                                "JoyAI-VL 服务未连接，使用本地分析", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            tvJoyAiStatus.setText("VL: OFF");
            tvJoyAiStatus.setTextColor(0x80FFFFFF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能使用视频维修功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseVideoRepairResources();
    }

    @Override
    public void onBackPressed() {
        exitVideoRepair();
    }

    // ========================================================================
    // 初始化
    // ========================================================================

    /** 绑定布局视图 */
    private void initViews() {
        previewView = findViewById(R.id.previewView);
        arOverlayView = findViewById(R.id.arOverlayView);
        tvRecordStatus = findViewById(R.id.tvRecordStatus);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        tvGuideText = findViewById(R.id.tvGuideText);
        tvStepProgress = findViewById(R.id.tvStepProgress);
        layoutTopBar = findViewById(R.id.layoutTopBar);
        btnPrevStep = findViewById(R.id.btnPrevStep);
        btnSkipStep = findViewById(R.id.btnSkipStep);
        btnTtsToggle = findViewById(R.id.btnTtsToggle);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnGenerateVideo = findViewById(R.id.btnGenerateVideo);
        btnCloseVideoRepair = findViewById(R.id.btnCloseVideoRepair);
        btnVoiceInput = findViewById(R.id.btnVoiceInput);
        tvJoyAiStatus = findViewById(R.id.tvJoyAiStatus);
    }

    /** 初始化所有核心模块 */
    private void initModules() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        stateMachine = new RepairStateMachine();
        stateMachine.addListener(this);

        errorDetector = new ErrorDetector();

        kbService = KnowledgeBaseService.getInstance();
        kbService.init(getApplicationContext());

        llmService = RepairLlmService.getInstance();
        ocrService = VivoOcrService.getInstance();
        visionService = VisionAnalysisService.getInstance();
        videoGenService = VideoGenerationService.getInstance();
        joyAiVlService = JoyAiVlService.getInstance();

        voiceManager = new VoiceInteractionManager(stateMachine);
        voiceManager.setCallback(new VoiceInteractionManager.VoiceCallback() {
            @Override
            public void onRecognized(String text, boolean isFinal) {
                if (isFinal && text != null && !text.isEmpty()) {
                    // 将识别结果赋值给 faultDescription，供后续 LLM 诊断使用
                    faultDescription = text;
                    runOnUiThread(() -> {
                        tvGuideText.setText("故障描述: " + text);
                        // 如果在故障诊断阶段收到描述，自动触发 LLM 步骤规划
                        if (stateMachine.getCurrentState() == RepairState.FAULT_DIAGNOSIS) {
                            loadRepairSteps();
                        }
                    });
                } else if (!isFinal) {
                    runOnUiThread(() -> tvGuideText.setText("识别中: " + text));
                }
            }

            @Override
            public void onIntent(RepairIntention intention) {
                Log.d(TAG, "用户意图: " + intention.getType());
            }

            @Override
            public void onTtsStart() {
                /* TTS 开始播报 */
            }

            @Override
            public void onTtsComplete() {
                /* TTS 播报完成 */
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() ->
                        Toast.makeText(VideoRepairActivity.this, msg, Toast.LENGTH_SHORT).show());
            }
        });

        // 初始状态 → 设备识别
        stateMachine.transitionTo(RepairState.DEVICE_IDENTIFY);
    }

    /** 让顶部退出按钮避开系统状态栏、刘海和电池时间区域。 */
    private void setupStatusBarAvoidance() {
        int statusBarHeight = getStatusBarHeight();
        layoutTopBar.setPadding(
                layoutTopBar.getPaddingLeft(),
                statusBarHeight + dpToPx(8),
                layoutTopBar.getPaddingRight(),
                dpToPx(8));
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dpToPx(24);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ========================================================================
    // 按钮事件
    // ========================================================================

    private void setupClickListeners() {
        btnCloseVideoRepair.setOnClickListener(v -> exitVideoRepair());

        // 按住说话
        btnVoiceInput.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        voiceManager.startListening();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        voiceManager.stopListening();
                        return true;
                    default:
                        return false;
                }
            }
        });

        // TTS 开关
        btnTtsToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsEnabled = !ttsEnabled;
                voiceManager.setTtsEnabled(ttsEnabled);
                btnTtsToggle.setImageResource(ttsEnabled
                        ? android.R.drawable.ic_lock_silent_mode_off
                        : android.R.drawable.ic_lock_silent_mode);
            }
        });

        // 上一步 / 回退
        btnPrevStep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stateMachine.goBack();
            }
        });

        // 下一步 / 跳过
        btnSkipStep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stateMachine.skipCurrent();
            }
        });

        // 拍照（保存当前帧到相册）
        btnTakePhoto.setOnClickListener(v -> capturePhoto());

        // 生成维修指导视频
        btnGenerateVideo.setOnClickListener(v -> generateRepairVideo());
    }

    // ========================================================================
    // 拍照
    // ========================================================================

    private void capturePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "相机尚未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = "GYT_" + System.currentTimeMillis() + ".jpg";
        ImageCapture.OutputFileOptions outputOptions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/工友通");
            outputOptions = new ImageCapture.OutputFileOptions.Builder(
                    getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues).build();
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "工友通");
            if (!dir.exists()) dir.mkdirs();
            outputOptions = new ImageCapture.OutputFileOptions.Builder(
                    new File(dir, fileName)).build();
        }

        imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        runOnUiThread(() ->
                            Toast.makeText(VideoRepairActivity.this,
                                    "维修照片已保存", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() ->
                            Toast.makeText(VideoRepairActivity.this,
                                    "拍照失败: " + exception.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ========================================================================
    // 视频生成
    // ========================================================================

    /**
     * 生成维修指导视频
     * 根据当前故障描述和维修步骤生成视频
     */
    private void generateRepairVideo() {
        // 构建视频描述
        StringBuilder videoPrompt = new StringBuilder();
        videoPrompt.append("这是一个设备维修指导视频。");

        if (!faultDescription.isEmpty()) {
            videoPrompt.append("故障描述：").append(faultDescription).append("。");
        }

        RepairStep currentStep = stateMachine.getCurrentStep();
        if (currentStep != null) {
            videoPrompt.append("当前步骤：").append(currentStep.getTitle())
                    .append(" - ").append(currentStep.getDescription()).append("。");
        }

        videoPrompt.append("请生成一个清晰的维修操作演示视频，包含工具使用和安全注意事项。");

        // 显示加载状态
        tvGuideText.setText(getString(R.string.video_repair_generating_video));
        btnGenerateVideo.setEnabled(false);

        videoGenService.generateFromText(videoPrompt.toString(),
                new VideoGenerationService.VideoGenerationCallback() {
                    @Override
                    public void onSuccess(String videoUrl) {
                        runOnUiThread(() -> {
                            btnGenerateVideo.setEnabled(true);
                            tvGuideText.setText(getString(R.string.video_repair_video_generated));
                            Toast.makeText(VideoRepairActivity.this,
                                    "视频已生成: " + videoUrl, Toast.LENGTH_LONG).show();
                            // TODO: 播放或保存生成的视频
                        });
                    }

                    @Override
                    public void onError(String msg) {
                        runOnUiThread(() -> {
                            btnGenerateVideo.setEnabled(true);
                            tvGuideText.setText(getString(R.string.video_repair_error_video_generation));
                            Toast.makeText(VideoRepairActivity.this,
                                    getString(R.string.video_repair_error_video_generation) + ": " + msg,
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onProgress(String status, int progress) {
                        runOnUiThread(() -> {
                            tvGuideText.setText("视频生成中: " + status + " (" + progress + "%)");
                        });
                    }
                });
    }

    // ========================================================================
    // CameraX
    // ========================================================================

    /** 检查相机权限 */
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 启动 CameraX 预览 + 帧分析 */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider provider = providerFuture.get();
                    cameraProvider = provider;
                    provider.unbindAll();

                    // Preview
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // ImageCapture - 拍照
                    imageCapture = new ImageCapture.Builder()
                            .setTargetResolution(new Size(1920, 1080))
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();

                    // ImageAnalysis：控制帧率 ≈ 1fps
                    imageAnalysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(new Size(
                                    Config.CAMERA_FRAME_MAX_WIDTH,
                                    Config.CAMERA_FRAME_MAX_WIDTH * 3 / 4))
                            .build();
                    imageAnalysis.setAnalyzer(cameraExecutor, VideoRepairActivity.this::analyzeFrame);

                    // 使用手机本地后置摄像头作为视频维修画面来源。
                    CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                    provider.bindToLifecycle(
                            VideoRepairActivity.this, cameraSelector, preview, imageAnalysis, imageCapture);

                    Log.d(TAG, "CameraX 已启动");
                } catch (Exception e) {
                    Log.e(TAG, "CameraX 启动失败: " + e.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 帧分析回调（后台线程）。
     * 双通道分析：
     * - JoyAI-VL：实时主动式指导（独立发送，不阻塞本地分析）
     * - 本地分析：OCR 设备识别 / Vision 动作验证（降级方案）
     */
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (demoMode) {
            imageProxy.close();
            return;
        }

        long now = System.currentTimeMillis();
        // 控制帧率：不低于 Config.CAMERA_FRAME_INTERVAL_MS 间隔
        if (now - lastFrameTime < Config.CAMERA_FRAME_INTERVAL_MS) {
            imageProxy.close();
            return;
        }
        lastFrameTime = now;

        if (isProcessing.get()) {
            imageProxy.close();
            return;
        }
        isProcessing.set(true);

        try {
            String base64 = imageProxyToBase64(imageProxy);
            if (base64 == null) {
                isProcessing.set(false);
                return;
            }

            RepairState state = stateMachine.getCurrentState();

            // ========== JoyAI-VL 实时主动指导（独立通道） ==========
            if (joyAiVlService.isEnabled() && joyAiVlService.isSessionActive()) {
                // 根据当前状态设置用户查询上下文
                updateJoyAiQuery(state);

                joyAiVlService.sendFrame(base64, new JoyAiVlService.JoyAiCallback() {
                    @Override
                    public void onProactiveGuidance(String guidance) {
                        // 模型主动开口指导
                        runOnUiThread(() -> {
                            tvGuideText.setText(guidance);
                            voiceManager.speak(guidance);
                        });
                    }

                    @Override
                    public void onQueryResponse(String response) {
                        // 对用户查询的响应
                        runOnUiThread(() -> {
                            tvGuideText.setText(response);
                            voiceManager.speak(response);
                        });
                    }

                    @Override
                    public void onSilent() {
                        // 模型判断当前无需指导，静默
                        Log.d(TAG, "JoyAI-VL: 当前帧无需指导");
                    }

                    @Override
                    public void onError(String msg) {
                        Log.w(TAG, "JoyAI-VL 分析失败: " + msg);
                    }
                });
            }

            // ========== 本地分析（降级方案，保留原有逻辑） ==========
            if (state == RepairState.DEVICE_IDENTIFY) {
                // 设备识别阶段：跑 OCR 提取铭牌文字
                ocrService.recognize(base64, new VivoOcrService.OcrCallback() {
                    @Override
                    public void onSuccess(String ocrText) {
                        isProcessing.set(false);
                        if (ocrText != null && !ocrText.isEmpty()) {
                            runOnUiThread(() -> onDeviceIdentified(ocrText));
                        }
                    }

                    @Override
                    public void onError(String msg) {
                        isProcessing.set(false);
                        Log.w(TAG, "OCR 识别失败: " + msg);
                    }
                });

            } else if (state == RepairState.STEP_GUIDE || state == RepairState.ACTION_VERIFY) {
                // 步骤引导 / 动作验证阶段：跑 Vision 分析
                RepairStep step = stateMachine.getCurrentStep();
                String context = (step != null)
                        ? step.getDescription()
                        : "分析当前维修场景";
                visionService.analyze(base64, context, new VisionAnalysisService.VisionCallback() {
                    @Override
                    public void onSuccess(String description,
                                          List<FrameAnalysisResult.BoundingBox> regions,
                                          float confidence) {
                        isProcessing.set(false);
                        runOnUiThread(() -> {
                            arOverlayView.setRegions(regions);
                            arOverlayView.invalidate();
                        });

                        // 错误检测
                        RepairStep currentStep = stateMachine.getCurrentStep();
                        FrameAnalysisResult result = new FrameAnalysisResult();
                        result.setDescription(description);
                        // Bug #1 fix: 填充检测信息供 ErrorDetector 使用
                        result.setSafetyPowerOff(isSafetyConfirmed(description, currentStep));
                        result.setVisionDetectedAction(extractActionKeyword(description, currentStep));
                        result.setVisionDetectedTool(extractToolKeyword(description, currentStep));
                        List<ErrorDetector.ErrorType> errors =
                                errorDetector.detect(result, currentStep);
                        if (!errors.isEmpty()) {
                            runOnUiThread(() -> onErrorDetected(errors));
                        }
                    }

                    @Override
                    public void onError(String msg) {
                        isProcessing.set(false);
                        Log.w(TAG, "Vision 分析失败: " + msg);
                    }
                });

            } else {
                isProcessing.set(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "帧分析异常: " + e.getMessage());
            isProcessing.set(false);
        } finally {
            imageProxy.close();
        }
    }

    /**
     * 根据当前维修状态更新 JoyAI-VL 的用户查询上下文
     */
    private void updateJoyAiQuery(RepairState state) {
        switch (state) {
            case DEVICE_IDENTIFY:
                joyAiVlService.setUserQuery("请观察设备铭牌，识别设备型号");
                break;
            case FAULT_DIAGNOSIS:
                if (!faultDescription.isEmpty()) {
                    joyAiVlService.setUserQuery("故障描述: " + faultDescription + "，请分析故障原因");
                } else {
                    joyAiVlService.setUserQuery("请观察设备状态，帮助用户诊断故障");
                }
                break;
            case STEP_GUIDE:
                RepairStep step = stateMachine.getCurrentStep();
                if (step != null) {
                    joyAiVlService.setUserQuery(
                            "当前维修步骤: " + step.getTitle() + " - " + step.getDescription() +
                            "，请观察操作是否正确，如有错误请指出");
                }
                break;
            case ACTION_VERIFY:
                RepairStep verifyStep = stateMachine.getCurrentStep();
                if (verifyStep != null) {
                    joyAiVlService.setUserQuery(
                            "请验证操作: " + verifyStep.getTitle() +
                            "，所需工具: " + verifyStep.getToolRequired() +
                            "，安全提示: " + verifyStep.getSafetyNote());
                }
                break;
            case ERROR_CORRECT:
                joyAiVlService.setUserQuery("检测到操作错误，请指导纠正");
                break;
            case COMPLETION_CHECK:
                joyAiVlService.setUserQuery("维修步骤已完成，请检查维修结果是否正常");
                break;
            default:
                joyAiVlService.setUserQuery("");
                break;
        }
    }

    /**
     * 将 ImageProxy（YUV_420_888）转换为 JPEG Base64 字符串。
     *
     * @param image CameraX 帧代理
     * @return Base64 编码的 JPEG 数据，失败返回 null
     */
    private String imageProxyToBase64(ImageProxy image) {
        ByteArrayOutputStream out = null;
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.w(TAG, "Plane 数量不足: " + planes.length);
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    nv21, android.graphics.ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()),
                    Config.CAMERA_FRAME_QUALITY, out);

            byte[] jpegBytes = out.toByteArray();
            return Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "帧转 Base64 失败: " + e.getMessage());
            return null;
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // 状态机回调
    // ========================================================================

    @Override
    public void onStateChanged(RepairState oldState, RepairState newState) {
        Log.d(TAG, "状态变更: " + oldState + " → " + newState);
        updateUIForState(newState);

        switch (newState) {
            case DEVICE_IDENTIFY:
                voiceManager.speak("请将摄像头对准设备铭牌");
                arOverlayView.clear();
                break;

            case FAULT_DIAGNOSIS:
                if (!faultDescription.isEmpty()) {
                    // 已有故障描述，直接开始 AI 诊断
                    voiceManager.speak("正在分析故障，请稍候");
                    tvGuideText.setText("AI 正在诊断: " + faultDescription);
                    loadRepairSteps();
                } else {
                    voiceManager.speak("请描述设备故障现象");
                    tvGuideText.setText("请按住说话按钮，描述设备故障现象");
                }
                break;

            case STEP_GUIDE:
                RepairStep step = stateMachine.getCurrentStep();
                if (step != null) {
                    voiceManager.speak(step.getDescription());
                    tvGuideText.setText(step.getTitle() + ": " + step.getDescription());
                }
                break;

            case ERROR_CORRECT:
                voiceManager.speak("检测到操作异常，请纠正");
                break;

            case COMPLETION_CHECK:
                voiceManager.speak("所有步骤已完成，请确认维修结果");
                break;

            case REPORT_GENERATE:
                voiceManager.speak("正在生成维修报告");
                break;

            default:
                break;
        }
    }

    @Override
    public void onStepChanged(int stepIndex, RepairStep step) {
        if (step != null) {
            int total = stateMachine.getTotalSteps();
            tvStepProgress.setText((stepIndex + 1) + "/" + total);
            // 在屏幕上显示当前步骤的标题和描述
            tvGuideText.setText(step.getTitle() + ": " + step.getDescription());
        }
    }

    // ========================================================================
    // 业务逻辑
    // ========================================================================

    /**
     * 设备识别成功后的处理。
     * 从 OCR 文本提取设备型号，搜索知识库，切换到故障诊断状态。
     */
    private void onDeviceIdentified(String ocrText) {
        deviceModel = extractDeviceModel(ocrText);
        tvDeviceInfo.setText(deviceModel);
        tvGuideText.setText("识别到设备: " + deviceModel);
        voiceManager.speak("检测到设备 " + deviceModel);

        // 搜索知识库（异步，结果可用于后续诊断）
        kbService.search(deviceModel, 3, new KnowledgeBaseService.SearchCallback() {
            @Override
            public void onResults(List<KnowledgeVectorEntity> results) {
                Log.d(TAG, "知识库匹配到 " + results.size() + " 条");
            }

            @Override
            public void onError(String msg) {
                Log.w(TAG, "知识库搜索失败: " + msg);
            }
        });

        // 切换到故障诊断
        stateMachine.transitionTo(RepairState.FAULT_DIAGNOSIS);
    }

    /** 执行故障诊断，引导用户描述故障后调用 LLM 规划步骤 */
    private void performDiagnosis() {
        // 已有故障描述则直接加载维修步骤
        if (!faultDescription.isEmpty()) {
            tvGuideText.setText("AI 正在诊断: " + faultDescription);
            loadRepairSteps();
            return;
        }
        // 无故障描述，提示用户通过语音输入描述
        tvGuideText.setText("请按住说话按钮，描述设备故障现象");
    }

    /** 调用 LLM 规划维修步骤，失败时使用默认步骤 */
    private void loadRepairSteps() {
        String faultText = faultDescription.isEmpty() ? "通用故障" : faultDescription;
        llmService.planSteps(deviceModel, faultText, new RepairLlmService.StepsCallback() {
            @Override
            public void onStepsReady(List<RepairStep> steps) {
                stateMachine.setSteps(steps);
                stateMachine.transitionTo(RepairState.STEP_GUIDE);
                tvGuideText.setText("加载了 " + steps.size() + " 个维修步骤");
            }

            @Override
            public void onError(String msg) {
                Log.w(TAG, "LLM 步骤规划失败，使用默认步骤: " + msg);
                List<RepairStep> defaultSteps = createDefaultSteps();
                stateMachine.setSteps(defaultSteps);
                stateMachine.transitionTo(RepairState.STEP_GUIDE);
                tvGuideText.setText("使用默认维修步骤");
            }
        });
    }

    /** 错误检测回调：进入错误纠正状态并播报警告 */
    private void onErrorDetected(List<ErrorDetector.ErrorType> errors) {
        if (errors.isEmpty()) {
            return;
        }
        stateMachine.transitionTo(RepairState.ERROR_CORRECT);

        StringBuilder sb = new StringBuilder();
        for (ErrorDetector.ErrorType err : errors) {
            sb.append(err.label).append(" ");
        }
        String allWarnings = sb.toString().trim();
        arOverlayView.setWarning(allWarnings);
        voiceManager.speak(allWarnings);
    }

    /**
     * 从 OCR 文本中提取设备型号。
     * 简单启发式：取第一行非空内容作为型号。
     */
    private String extractDeviceModel(String ocrText) {
        if (ocrText == null || ocrText.isEmpty()) {
            return "未知设备";
        }
        String[] lines = ocrText.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() >= 2) {
                return trimmed;
            }
        }
        return "未知设备";
    }

    /** 创建默认维修步骤（LLM 不可用时的降级方案） */
    private List<RepairStep> createDefaultSteps() {
        List<RepairStep> steps = new ArrayList<>();
        steps.add(new RepairStep(
                "安全检查", "断开设备电源，确保安全操作环境", "无", "务必断电后再操作"));
        steps.add(new RepairStep(
                "外观检查", "检查设备外观是否有明显损坏", "手电筒", "注意尖锐边缘"));
        steps.add(new RepairStep(
                "拆卸外壳", "使用螺丝刀拆卸外壳面板", "螺丝刀", "妥善保管拆下的螺丝"));
        steps.add(new RepairStep(
                "内部检查", "检查内部线路和部件连接", "万用表", "避免触碰带电部件"));
        steps.add(new RepairStep(
                "故障定位", "根据诊断结果定位故障部件", "无", ""));
        steps.add(new RepairStep(
                "部件更换", "更换或修复故障部件", "扳手", "使用原厂配件"));
        steps.add(new RepairStep(
                "组装复原", "按拆卸顺序反向组装", "螺丝刀", "确保所有螺丝拧紧"));
        steps.add(new RepairStep(
                "功能测试", "通电测试设备功能是否正常", "无", "首次通电注意观察异常"));
        return steps;
    }

    /** 启动不依赖云端接口的演示指导流。 */
    private void startDemoGuidance() {
        if (deviceModel.isEmpty()) {
            deviceModel = "家用空调";
        }
        if (faultDescription.isEmpty()) {
            faultDescription = "制冷效果差，出风口有异响";
        }

        tvDeviceInfo.setText(deviceModel);
        tvRecordStatus.setText("DEMO 指导中");
        stateMachine.setSteps(createDefaultSteps());
        stateMachine.transitionTo(RepairState.STEP_GUIDE);
        demoHandler.post(demoTick);
    }

    private final Runnable demoTick = new Runnable() {
        @Override
        public void run() {
            showDemoGuide();
            demoStepIndex = (demoStepIndex + 1) % demoGuides.length;
            demoHandler.postDelayed(this, 3500L);
        }
    };

    /** 展示一条模拟 AI 视频分析结果。 */
    private void showDemoGuide() {
        String[] item = demoGuides[demoStepIndex];
        String displayDevice = item[0];
        String faultGuess = item[1];
        String currentStep = item[2];
        String safetyWarning = item[3];
        String nextAction = item[4];

        tvDeviceInfo.setText(displayDevice);
        tvStepProgress.setText((demoStepIndex + 1) + "/" + demoGuides.length);
        tvGuideText.setText(
                "设备：" + displayDevice +
                "\n可能故障：" + faultGuess +
                "\n当前步骤：" + currentStep +
                "\n下一步：" + nextAction);

        List<FrameAnalysisResult.BoundingBox> boxes = new ArrayList<>();
        boxes.add(new FrameAnalysisResult.BoundingBox(
                0.18f, 0.20f, 0.64f, 0.34f,
                demoStepIndex == 0 ? "设备铭牌/电源区域" : "重点检查区域",
                0.86f));
        arOverlayView.setRegions(boxes);
        arOverlayView.setGuideText(currentStep);
        arOverlayView.setWarning(demoStepIndex == 0 || demoStepIndex == 3 ? safetyWarning : null);

        if (ttsEnabled && !currentStep.equals(lastSpokenDemoText)) {
            lastSpokenDemoText = currentStep;
            voiceManager.speak((demoStepIndex == 0 ? safetyWarning + "。" : "") + currentStep);
        }
    }

    /**
     * 从 Vision 描述和当前步骤中推断安全确认状态。
     * 如果描述中包含安全相关关键词，或当前步骤标题含"安全"即认为已确认。
     */
    private boolean isSafetyConfirmed(String description, RepairStep step) {
        if (description == null) {
            return false;
        }
        String lower = description.toLowerCase();
        if (lower.contains("断电") || lower.contains("安全") || lower.contains("电源")
                || lower.contains("power off") || lower.contains("safety")) {
            return true;
        }
        if (step != null && step.getTitle() != null && step.getTitle().contains("安全")) {
            return true;
        }
        return false;
    }

    /**
     * 从 Vision 描述中提取当前检测到的操作关键词。
     * 优先从描述中匹配当前步骤标题，降级使用步骤标题本身。
     */
    private String extractActionKeyword(String description, RepairStep step) {
        if (step == null || step.getTitle() == null || step.getTitle().isEmpty()) {
            return "未知操作";
        }
        // 尝试在描述中找步骤标题的匹配词
        if (description != null && description.contains(step.getTitle())) {
            return step.getTitle();
        }
        // 降级：使用常见动作关键词匹配
        String[] actionKeywords = {"拆卸", "检查", "更换", "安装", "测试",
                "断开", "连接", "清洁", "调整", "修复", "组装"};
        if (description != null) {
            for (String kw : actionKeywords) {
                if (description.contains(kw)) {
                    return kw;
                }
            }
        }
        // 最终降级：返回步骤标题
        return step.getTitle();
    }

    /**
     * 从 Vision 描述中提取当前检测到的工具关键词。
     * 优先匹配当前步骤的所需工具，降级使用常见工具关键词匹配。
     */
    private String extractToolKeyword(String description, RepairStep step) {
        if (description == null) {
            return "";
        }
        // 优先匹配当前步骤的所需工具
        if (step != null && step.getToolRequired() != null
                && !step.getToolRequired().isEmpty()
                && !"无".equals(step.getToolRequired())) {
            if (description.contains(step.getToolRequired())) {
                return step.getToolRequired();
            }
        }
        // 降级：常见工具关键词匹配
        String[] toolKeywords = {"螺丝刀", "扳手", "万用表", "手电筒", "电烙铁",
                "钳子", "锤子", "电钻", "剪刀", "胶带"};
        for (String tool : toolKeywords) {
            if (description.contains(tool)) {
                return tool;
            }
        }
        return "";
    }

    /** 点击退出或系统返回时，统一关闭视频维修页面。 */
    private void exitVideoRepair() {
        releaseVideoRepairResources();
        finish();
    }

    /** 释放相机、语音、Demo 轮询和 AI 会话资源。 */
    private void releaseVideoRepairResources() {
        isProcessing.set(false);
        demoHandler.removeCallbacksAndMessages(null);

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        if (stateMachine != null) {
            stateMachine.removeListener(this);
        }
        if (voiceManager != null) {
            voiceManager.release();
            voiceManager = null;
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        if (joyAiVlService != null && joyAiVlService.isSessionActive()) {
            joyAiVlService.endSession();
        }
    }

    /** 根据当前状态更新顶部状态指示 */
    private void updateUIForState(RepairState state) {
        switch (state) {
            case DEVICE_IDENTIFY:
                tvRecordStatus.setText("\uD83D\uDD0D 识别设备中");
                arOverlayView.clear();
                break;
            case FAULT_DIAGNOSIS:
                tvRecordStatus.setText("\uD83D\uDD34 诊断中");
                break;
            case STEP_GUIDE:
                tvRecordStatus.setText("\uD83D\uDD34 录制中");
                break;
            case ACTION_VERIFY:
                tvRecordStatus.setText("\uD83D\uDD0D 验证中");
                break;
            case ERROR_CORRECT:
                tvRecordStatus.setText("\u26A0\uFE0F 错误纠正");
                break;
            case COMPLETION_CHECK:
                tvRecordStatus.setText("\u2705 验证中");
                break;
            case REPORT_GENERATE:
                tvRecordStatus.setText("\uD83D\uDCDD 报告生成");
                break;
            default:
                break;
        }
    }
}
