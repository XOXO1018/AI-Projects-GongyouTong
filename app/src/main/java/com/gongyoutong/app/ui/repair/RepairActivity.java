package com.gongyoutong.app.ui.repair;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.gongyoutong.app.Config;
import com.gongyoutong.app.R;
import com.bumptech.glide.Glide;
import com.gongyoutong.app.ai.ImageGenerationService;
import com.gongyoutong.app.ai.VivoAsrService;
import com.gongyoutong.app.ai.WorkOrderAiService;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.DiagnosisRecordDao;
import com.gongyoutong.app.database.DiagnosisRecordEntity;
import com.gongyoutong.app.database.WorkOrderDao;
import com.gongyoutong.app.database.WorkOrderEntity;
import com.gongyoutong.app.repair.DiagnosisRequest;
import com.gongyoutong.app.repair.DiagnosisResult;
import com.gongyoutong.app.workorder.WorkOrderStatus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 维修界面 Activity
 * 提供 AI 辅助诊断功能，支持拍照、语音输入、多轮诊断
 */
public class RepairActivity extends AppCompatActivity {

    private static final String TAG = "RepairActivity";
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 1002;

    // ==================== Views ====================

    private MaterialToolbar toolbar;
    private TextView tvWorkOrderNo;
    private TextInputEditText etFaultDescription;
    private MaterialButton btnVoice;
    private RecyclerView rvPhotos;
    private ImageButton btnTakePhoto;
    private MaterialButton btnDiagnose;
    private LinearLayout layoutLoading;
    private LinearLayout layoutDiagnosisResult;

    // 图片生成相关 Views
    private MaterialButton btnGenerateImages;
    private MaterialButton btnVideoRepair;
    private LinearLayout layoutImageGenLoading;
    private LinearLayout layoutImageGenResult;

    // 诊断结果卡片
    private MaterialCardView cardFaultCauses;
    private MaterialCardView cardInspectionSteps;
    private MaterialCardView cardSafetyTips;
    private MaterialCardView cardRequiredTools;
    private MaterialCardView cardRequiredParts;
    private MaterialCardView cardEstimatedTime;

    // 排查步骤容器
    private LinearLayout layoutStepsContent;
    private ImageView ivStepsExpand;

    // 底部操作
    private MaterialButton btnRediagnose;
    private MaterialButton btnCompleteRepair;

    // ==================== Data ====================

    private AppDatabase database;
    private WorkOrderDao workOrderDao;
    private DiagnosisRecordDao diagnosisRecordDao;
    private WorkOrderAiService aiService;
    private ImageGenerationService imageGenService;
    private VivoAsrService asrService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String workOrderId;
    private WorkOrderEntity currentWorkOrder;
    private DiagnosisResult currentDiagnosisResult;

    private final List<String> photoBase64List = new ArrayList<>();
    private final List<String> photoFilePathList = new ArrayList<>();
    private final List<DiagnosisRecordEntity> diagnosisHistory = new ArrayList<>();

    private int currentRound = 1;
    private boolean isDiagnosing = false;
    private boolean isGenerating = false;
    private boolean isRecording = false;
    private final List<String> generatedImageUrls = new ArrayList<>();
    private Uri currentPhotoUri;  // 当前拍照的文件 URI

    // ==================== 拍照 Launcher ====================

    private final ActivityResultLauncher<Intent> takePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    handlePhotoResult();
                }
            });

    // ==================== Lifecycle ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            setContentView(R.layout.activity_repair);

            database = AppDatabase.getInstance(this);
            workOrderDao = database.workOrderDao();
            diagnosisRecordDao = database.diagnosisRecordDao();
            aiService = WorkOrderAiService.getInstance();
            imageGenService = ImageGenerationService.getInstance();
            asrService = new VivoAsrService();

            workOrderId = getIntent().getStringExtra(Config.EXTRA_WORKORDER_ID);
            if (workOrderId == null || workOrderId.isEmpty()) {
                Log.e(TAG, "onCreate: workOrderId is null or empty");
                Toast.makeText(this, R.string.repair_error_no_workorder, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            initViews();
            setupToolbar();
            setupClickListeners();
            loadWorkOrder();
        } catch (Exception e) {
            Log.e(TAG, "onCreate: fatal error", e);
            Toast.makeText(this, R.string.repair_error_load, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (asrService != null) {
            asrService.destroy();
        }
        if (executor != null) executor.shutdown();
    }

    // ==================== 初始化 ====================

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvWorkOrderNo = findViewById(R.id.tvWorkOrderNo);
        etFaultDescription = findViewById(R.id.etFaultDescription);
        btnVoice = findViewById(R.id.btnVoice);
        rvPhotos = findViewById(R.id.rvPhotos);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnDiagnose = findViewById(R.id.btnDiagnose);
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutDiagnosisResult = findViewById(R.id.layoutDiagnosisResult);

        cardFaultCauses = findViewById(R.id.cardFaultCauses);
        cardInspectionSteps = findViewById(R.id.cardInspectionSteps);
        cardSafetyTips = findViewById(R.id.cardSafetyTips);
        cardRequiredTools = findViewById(R.id.cardRequiredTools);
        cardRequiredParts = findViewById(R.id.cardRequiredParts);
        cardEstimatedTime = findViewById(R.id.cardEstimatedTime);

        layoutStepsContent = findViewById(R.id.layoutStepsContent);
        ivStepsExpand = findViewById(R.id.ivStepsExpand);

        btnRediagnose = findViewById(R.id.btnRediagnose);
        btnCompleteRepair = findViewById(R.id.btnCompleteRepair);

        // 图片生成区域
        btnGenerateImages = findViewById(R.id.btnGenerateImages);
        btnVideoRepair = findViewById(R.id.btnVideoRepair);
        layoutImageGenLoading = findViewById(R.id.layoutImageGenLoading);
        layoutImageGenResult = findViewById(R.id.layoutImageGenResult);

        // 照片列表横向排列
        rvPhotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPhotos.setAdapter(new PhotoAdapter());
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        // 拍照
        btnTakePhoto.setOnClickListener(v -> checkAndTakePhoto());

        // 语音输入
        btnVoice.setOnClickListener(v -> toggleVoiceInput());

        // AI 诊断
        btnDiagnose.setOnClickListener(v -> startDiagnosis());

        // 重新诊断
        btnRediagnose.setOnClickListener(v -> startRediagnosis());

        // AI 图片生成
        btnGenerateImages.setOnClickListener(v -> handleImageGeneration());

        // 跳转视频维修（传递当前上下文）
        btnVideoRepair.setOnClickListener(v -> {
            Intent intent = new Intent(RepairActivity.this, VideoRepairActivity.class);
            // 传递故障描述
            String desc = etFaultDescription.getText() != null
                    ? etFaultDescription.getText().toString().trim() : "";
            intent.putExtra("fault_description", desc);
            intent.putExtra("work_order_id", workOrderId);
            startActivity(intent);
        });

        // 完成维修
        btnCompleteRepair.setOnClickListener(v -> confirmCompleteRepair());
    }

    // ==================== 数据加载 ====================

    private void loadWorkOrder() {
        executor.execute(() -> {
            try {
                currentWorkOrder = workOrderDao.getById(workOrderId);
            } catch (Exception e) {
                Log.e(TAG, "loadWorkOrder: query error", e);
            }

            if (currentWorkOrder == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.repair_error_no_workorder, Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            // 加载历史诊断记录
            List<DiagnosisRecordEntity> records = null;
            try {
                records = diagnosisRecordDao.getByWorkOrderId(workOrderId);
            } catch (Exception e) {
                Log.e(TAG, "loadWorkOrder: diagnosis records query error", e);
            }

            if (records != null) {
                diagnosisHistory.clear();
                diagnosisHistory.addAll(records);
                currentRound = records.size() + 1;
            }

            List<DiagnosisRecordEntity> finalRecords = records;
            runOnUiThread(() -> {
                tvWorkOrderNo.setText(getString(R.string.repair_order_no, workOrderId));

                // 预填故障描述
                if (currentWorkOrder.getDescription() != null && !currentWorkOrder.getDescription().isEmpty()) {
                    etFaultDescription.setText(currentWorkOrder.getDescription());
                }

                // 如果有历史诊断，显示最近结果
                if (finalRecords != null && !finalRecords.isEmpty()) {
                    DiagnosisRecordEntity latest = finalRecords.get(finalRecords.size() - 1);
                    if (latest.getDiagnosisResult() != null && !latest.getDiagnosisResult().isEmpty()) {
                        // 重建 DiagnosisResult 并渲染
                        DiagnosisResult result = parseDiagnosisResultFromJson(latest.getDiagnosisResult());
                        if (result != null) {
                            currentDiagnosisResult = result;
                            renderDiagnosisResult(result);
                        }
                    }
                }
            });
        });
    }

    /**
     * 从保存的 JSON 字符串重建 DiagnosisResult
     */
    private DiagnosisResult parseDiagnosisResultFromJson(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            DiagnosisResult result = new DiagnosisResult();
            result.setFaultCauses(obj.optString("faultCauses", "暂无信息"));
            result.setInspectionSteps(obj.optString("inspectionSteps", "暂无信息"));
            result.setSafetyTips(obj.optString("safetyTips", "暂无信息"));
            result.setRequiredTools(obj.optString("requiredTools", "暂无信息"));
            result.setRequiredParts(obj.optString("requiredParts", "暂无信息"));
            result.setEstimatedTime(obj.optString("estimatedTime", "暂无信息"));
            result.setRawResponse(json);
            result.initStepCheckedStates(result.getInspectionSteps());
            return result;
        } catch (Exception e) {
            Log.e(TAG, "parseDiagnosisResultFromJson error: " + e.getMessage());
            return null;
        }
    }

    // ==================== 拍照 ====================

    private void checkAndTakePhoto() {
        if (photoBase64List.size() >= Config.MAX_PHOTOS) {
            Toast.makeText(this, R.string.repair_max_photos_reached, Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
            return;
        }

        dispatchTakePhotoIntent();
    }

    private void dispatchTakePhotoIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // 创建临时文件并通过 FileProvider 获取 URI
            File photoFile = createPhotoFile();
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePhotoLauncher.launch(takePictureIntent);
            } else {
                Toast.makeText(this, R.string.repair_error_save_photo, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.repair_error_no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void handlePhotoResult() {
        if (currentPhotoUri == null) {
            Toast.makeText(this, R.string.repair_error_save_photo, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 从 URI 读取照片
            InputStream inputStream = getContentResolver().openInputStream(currentPhotoUri);
            if (inputStream == null) {
                Toast.makeText(this, R.string.repair_error_save_photo, Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap == null) {
                Toast.makeText(this, R.string.repair_error_save_photo, Toast.LENGTH_SHORT).show();
                return;
            }

            compressAndAddPhoto(bitmap);

        } catch (Exception e) {
            Log.e(TAG, "handlePhotoResult error: " + e.getMessage());
            Toast.makeText(this, R.string.repair_error_save_photo, Toast.LENGTH_SHORT).show();
        } finally {
            currentPhotoUri = null;
        }
    }

    /**
     * 创建照片文件
     */
    private File createPhotoFile() {
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }
        if (storageDir != null) {
            return new File(storageDir, "repair_photo_" + System.currentTimeMillis() + ".jpg");
        }
        return null;
    }

    /**
     * 压缩照片并添加到列表
     */
    private void compressAndAddPhoto(Bitmap bitmap) {
        try {
            if (bitmap == null) {
                Log.e(TAG, "compressAndAddPhoto: bitmap is null");
                return;
            }

            // 缩放
            Bitmap scaled = scaleBitmap(bitmap, Config.PHOTO_MAX_SIZE_PX);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            bitmap = scaled;

            // 压缩为 JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, Config.PHOTO_QUALITY, baos);
            byte[] bytes = baos.toByteArray();

            // Base64 编码
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            photoBase64List.add(base64);

            // 保存到本地文件
            File photoFile = createPhotoFile();
            if (photoFile != null) {
                FileOutputStream fos = new FileOutputStream(photoFile);
                fos.write(bytes);
                fos.close();
                photoFilePathList.add(photoFile.getAbsolutePath());
            }

            // 更新 RecyclerView
            updatePhotoRecyclerView();

        } catch (Exception e) {
            Log.e(TAG, "compressAndAddPhoto error: " + e.getMessage());
            Toast.makeText(this, R.string.repair_error_save_photo, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 等比缩放 Bitmap，使长边不超过 maxPx
     */
    private Bitmap scaleBitmap(Bitmap bitmap, int maxPx) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = 1.0f;

        if (width > maxPx || height > maxPx) {
            if (width > height) {
                scale = (float) maxPx / width;
            } else {
                scale = (float) maxPx / height;
            }
        }

        if (scale < 1.0f) {
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }

        return bitmap;
    }

    /**
     * 更新照片列表 RecyclerView
     */
    private void updatePhotoRecyclerView() {
        if (rvPhotos.getAdapter() != null) {
            rvPhotos.getAdapter().notifyDataSetChanged();
        }

        // 超过最大数量时隐藏拍照按钮
        if (photoBase64List.size() >= Config.MAX_PHOTOS) {
            btnTakePhoto.setVisibility(View.GONE);
        } else {
            btnTakePhoto.setVisibility(View.VISIBLE);
        }
    }

    // ==================== 语音输入 ====================

    private void toggleVoiceInput() {
        if (isRecording) {
            stopVoiceInput();
        } else {
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_RECORD_AUDIO_PERMISSION);
            return;
        }

        isRecording = true;
        btnVoice.setText(R.string.repair_voice_stop);
        btnVoice.setIconTintResource(R.color.accent_red);

        asrService.startRecognition(new VivoAsrService.AsrCallback() {
            @Override
            public void onResult(String text, boolean isFinal) {
                runOnUiThread(() -> {
                    if (isFinal && text != null && !text.isEmpty()) {
                        String current = etFaultDescription.getText() != null
                                ? etFaultDescription.getText().toString() : "";
                        String newText = current.isEmpty() ? text : current + "，" + text;
                        etFaultDescription.setText(newText);
                        etFaultDescription.setSelection(newText.length());
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Log.e(TAG, "ASR error: " + errorMessage);
                    Toast.makeText(RepairActivity.this,
                            R.string.repair_error_voice, Toast.LENGTH_SHORT).show();
                    resetVoiceButton();
                });
            }

            @Override
            public void onRecordingStateChanged(boolean recording) {
                if (!recording) {
                    runOnUiThread(() -> resetVoiceButton());
                }
            }
        });
    }

    private void stopVoiceInput() {
        asrService.stopRecognition();
        resetVoiceButton();
    }

    private void resetVoiceButton() {
        isRecording = false;
        btnVoice.setText(R.string.repair_voice_input);
        btnVoice.setIconTintResource(R.color.primary);
    }

    // ==================== AI 诊断 ====================

    private void startDiagnosis() {
        String faultDescription = etFaultDescription.getText() != null
                ? etFaultDescription.getText().toString().trim() : "";

        if (faultDescription.isEmpty() && photoBase64List.isEmpty()) {
            Toast.makeText(this, R.string.repair_error_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDiagnosing) {
            return;
        }

        isDiagnosing = true;
        showLoading(true);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setFaultDescription(faultDescription);
        request.setPhotoBase64List(new ArrayList<>(photoBase64List));
        request.setWorkType(currentWorkOrder != null ? currentWorkOrder.getWorkType() : "");
        request.setWorkOrderTitle(currentWorkOrder != null ? currentWorkOrder.getTitle() : "");

        aiService.diagnoseFault(request, new WorkOrderAiService.DiagnosisCallback() {
            @Override
            public void onSuccess(DiagnosisResult result) {
                runOnUiThread(() -> {
                    isDiagnosing = false;
                    showLoading(false);
                    currentDiagnosisResult = result;
                    renderDiagnosisResult(result);
                    saveDiagnosisRecord(result);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    isDiagnosing = false;
                    showLoading(false);
                    Toast.makeText(RepairActivity.this,
                            errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 重新诊断：追加输入 + 历史上下文
     */
    private void startRediagnosis() {
        String faultDescription = etFaultDescription.getText() != null
                ? etFaultDescription.getText().toString().trim() : "";

        if (faultDescription.isEmpty() && photoBase64List.isEmpty()) {
            Toast.makeText(this, R.string.repair_error_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDiagnosing) {
            return;
        }

        isDiagnosing = true;
        showLoading(true);
        layoutDiagnosisResult.setVisibility(View.GONE);

        DiagnosisRequest request = new DiagnosisRequest();
        request.setFaultDescription(faultDescription);
        request.setPhotoBase64List(new ArrayList<>(photoBase64List));
        request.setWorkType(currentWorkOrder != null ? currentWorkOrder.getWorkType() : "");
        request.setWorkOrderTitle(currentWorkOrder != null ? currentWorkOrder.getTitle() : "");

        // 追加历史上下文（最近2轮）
        if (!diagnosisHistory.isEmpty()) {
            int startIndex = Math.max(0, diagnosisHistory.size() - 2);
            for (int i = startIndex; i < diagnosisHistory.size(); i++) {
                DiagnosisRecordEntity record = diagnosisHistory.get(i);
                // 用户消息（故障描述）
                if (record.getFaultDescription() != null && !record.getFaultDescription().isEmpty()) {
                    request.addHistoryMessage("user", record.getFaultDescription());
                }
                // AI 回复（原始返回）
                if (record.getRawAiResponse() != null && !record.getRawAiResponse().isEmpty()) {
                    request.addHistoryMessage("assistant", record.getRawAiResponse());
                }
            }
        }

        currentRound++;

        aiService.diagnoseFault(request, new WorkOrderAiService.DiagnosisCallback() {
            @Override
            public void onSuccess(DiagnosisResult result) {
                runOnUiThread(() -> {
                    isDiagnosing = false;
                    showLoading(false);
                    currentDiagnosisResult = result;
                    renderDiagnosisResult(result);
                    saveDiagnosisRecord(result);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    isDiagnosing = false;
                    showLoading(false);
                    currentRound--;
                    Toast.makeText(RepairActivity.this,
                            errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==================== 结果渲染 ====================

    /**
     * 渲染诊断结果到 6 个区块卡片
     */
    private void renderDiagnosisResult(DiagnosisResult result) {
        layoutDiagnosisResult.setVisibility(View.VISIBLE);

        // 故障原因
        setupSectionCard(cardFaultCauses,
                getString(R.string.repair_section_fault_causes),
                result.getFaultCauses(),
                true);

        // 排查步骤（特殊处理：CheckBox 列表）
        setupStepsCard(result.getInspectionSteps());

        // 安全提示
        setupSectionCard(cardSafetyTips,
                getString(R.string.repair_section_safety_tips),
                result.getSafetyTips(),
                true);

        // 所需工具
        setupSectionCard(cardRequiredTools,
                getString(R.string.repair_section_required_tools),
                result.getRequiredTools(),
                true);

        // 可能配件
        setupSectionCard(cardRequiredParts,
                getString(R.string.repair_section_required_parts),
                result.getRequiredParts(),
                true);

        // 时间预估
        setupSectionCard(cardEstimatedTime,
                getString(R.string.repair_section_estimated_time),
                result.getEstimatedTime(),
                true);
    }

    /**
     * 设置普通文本区块卡片
     */
    private void setupSectionCard(MaterialCardView card, String title, String content, boolean expanded) {
        TextView tvTitle = card.findViewById(R.id.tvSectionTitle);
        TextView tvContent = card.findViewById(R.id.tvSectionContent);
        ImageView ivExpand = card.findViewById(R.id.ivExpandToggle);
        LinearLayout layoutHeader = card.findViewById(R.id.layoutSectionHeader);

        tvTitle.setText(title);
        tvContent.setText(content != null ? content : "暂无信息");

        // 展开折叠
        tvContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        ivExpand.setImageResource(expanded
                ? android.R.drawable.arrow_up_float
                : android.R.drawable.arrow_down_float);

        layoutHeader.setOnClickListener(v -> {
            boolean isVisible = tvContent.getVisibility() == View.VISIBLE;
            tvContent.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            ivExpand.setImageResource(isVisible
                    ? android.R.drawable.arrow_down_float
                    : android.R.drawable.arrow_up_float);
        });
    }

    /**
     * 设置排查步骤卡片（CheckBox 列表）
     */
    private void setupStepsCard(String stepsText) {
        // 标题行点击展开/折叠
        LinearLayout layoutStepsHeader = findViewById(R.id.layoutStepsHeader);
        layoutStepsHeader.setOnClickListener(v -> {
            boolean isVisible = layoutStepsContent.getVisibility() == View.VISIBLE;
            layoutStepsContent.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            ivStepsExpand.setImageResource(isVisible
                    ? android.R.drawable.arrow_down_float
                    : android.R.drawable.arrow_up_float);
        });

        // 渲染步骤列表
        layoutStepsContent.removeAllViews();
        if (stepsText != null && !stepsText.isEmpty()) {
            String[] lines = stepsText.split("\n");
            LayoutInflater inflater = LayoutInflater.from(this);

            int checkedIndex = 0;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                View stepView = inflater.inflate(R.layout.item_diagnosis_step, layoutStepsContent, false);
                CheckBox cbStep = stepView.findViewById(R.id.cbStep);
                TextView tvStepText = stepView.findViewById(R.id.tvStepText);

                tvStepText.setText(line);

                // 恢复勾选状态（使用 checkedIndex 而非行索引 i）
                final int stepIndex = checkedIndex;
                if (currentDiagnosisResult != null
                        && currentDiagnosisResult.getStepCheckedStates() != null
                        && stepIndex < currentDiagnosisResult.getStepCheckedStates().size()) {
                    cbStep.setChecked(currentDiagnosisResult.getStepCheckedStates().get(stepIndex));
                }

                cbStep.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (currentDiagnosisResult != null
                            && currentDiagnosisResult.getStepCheckedStates() != null
                            && stepIndex < currentDiagnosisResult.getStepCheckedStates().size()) {
                        currentDiagnosisResult.getStepCheckedStates().set(stepIndex, isChecked);
                    }
                });

                layoutStepsContent.addView(stepView);
                checkedIndex++;
            }
        }

        // 默认展开
        layoutStepsContent.setVisibility(View.VISIBLE);
        ivStepsExpand.setImageResource(android.R.drawable.arrow_up_float);
    }

    // ==================== Loading ====================

    private void showLoading(boolean show) {
        layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        btnDiagnose.setEnabled(!show);
        btnRediagnose.setEnabled(!show);
        btnGenerateImages.setEnabled(!show);  // 诊断中禁用图片生成
    }

    // ==================== 图片生成 ====================

    /**
     * 处理 AI 图片生成请求
     * 根据故障描述和设备照片调用图片生成 API，生成维修流程图
     */
    private void handleImageGeneration() {
        String faultDescription = etFaultDescription.getText() != null
                ? etFaultDescription.getText().toString().trim() : "";

        // 输入校验：至少需要故障描述或照片之一
        if (faultDescription.isEmpty() && photoBase64List.isEmpty()) {
            Toast.makeText(this, R.string.repair_error_empty_image_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDiagnosing || isGenerating) {
            return;
        }

        isGenerating = true;
        showImageGenLoading(true);
        btnDiagnose.setEnabled(false);
        btnRediagnose.setEnabled(false);

        // 构造生成提示词（引导模型生成维修流程图风格图片）
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请生成一张专业的设备维修流程图。");
        if (!faultDescription.isEmpty()) {
            promptBuilder.append("故障描述：").append(faultDescription).append("。");
        }
        promptBuilder.append("流程图应包含检修步骤、注意事项和工具建议，"
                + "风格为清晰的工程图表，带箭头和步骤编号，中文标注，适合维修工人参考使用。");
        String prompt = promptBuilder.toString();

        imageGenService.generateImages(prompt, new ArrayList<>(photoBase64List),
                new ImageGenerationService.ImageGenerationCallback() {
                    @Override
                    public void onSuccess(List<String> imageUrls) {
                        runOnUiThread(() -> {
                            isGenerating = false;
                            showImageGenLoading(false);
                            btnDiagnose.setEnabled(true);
                            btnRediagnose.setEnabled(true);
                            generatedImageUrls.clear();
                            generatedImageUrls.addAll(imageUrls);
                            renderGeneratedImages();
                        });
                    }

                    @Override
                    public void onError(String msg) {
                        runOnUiThread(() -> {
                            isGenerating = false;
                            showImageGenLoading(false);
                            btnDiagnose.setEnabled(!isDiagnosing);
                            btnRediagnose.setEnabled(!isDiagnosing);
                            Toast.makeText(RepairActivity.this,
                                    getString(R.string.repair_error_image_generation) + "：" + msg,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    /**
     * 渲染生成的维修流程图
     * 动态创建 ImageView 并通过 Glide 加载图片
     */
    private void renderGeneratedImages() {
        layoutImageGenResult.removeAllViews();

        if (generatedImageUrls.isEmpty()) {
            layoutImageGenResult.setVisibility(View.GONE);
            return;
        }

        layoutImageGenResult.setVisibility(View.VISIBLE);

        for (int i = 0; i < generatedImageUrls.size(); i++) {
            String imageUrl = generatedImageUrls.get(i);

            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dpToPx(12);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            imageView.setContentDescription(getString(R.string.repair_generated_images_title) + " " + (i + 1));

            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(imageView);

            layoutImageGenResult.addView(imageView);
        }
    }

    /**
     * 控制图片生成加载状态
     */
    private void showImageGenLoading(boolean show) {
        layoutImageGenLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        btnGenerateImages.setEnabled(!show);
    }

    /**
     * 隐藏图片生成加载状态
     */
    private void hideImageGenLoading() {
        layoutImageGenLoading.setVisibility(View.GONE);
    }

    /**
     * dp 转 px（用于代码中动态设置 Margin）
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    // ==================== 保存诊断记录 ====================

    /**
     * 保存诊断记录到数据库
     */
    private void saveDiagnosisRecord(DiagnosisResult result) {
        executor.execute(() -> {
            try {
                DiagnosisRecordEntity record = new DiagnosisRecordEntity();
                record.setId("diag_" + workOrderId + "_" + System.currentTimeMillis());
                record.setWorkOrderId(workOrderId);
                record.setRound(currentRound);
                record.setFaultDescription(etFaultDescription.getText() != null
                        ? etFaultDescription.getText().toString() : "");
                record.setPhotoPaths(String.join(",", photoFilePathList));

                // 将 DiagnosisResult 转为 JSON 保存
                org.json.JSONObject resultJson = new org.json.JSONObject();
                resultJson.put("faultCauses", result.getFaultCauses());
                resultJson.put("inspectionSteps", result.getInspectionSteps());
                resultJson.put("safetyTips", result.getSafetyTips());
                resultJson.put("requiredTools", result.getRequiredTools());
                resultJson.put("requiredParts", result.getRequiredParts());
                resultJson.put("estimatedTime", result.getEstimatedTime());
                record.setDiagnosisResult(resultJson.toString());

                record.setRawAiResponse(result.getRawResponse() != null ? result.getRawResponse() : "");
                record.setAiSource("CLOUD_AI");
                record.setCreatedAt(System.currentTimeMillis());

                diagnosisRecordDao.insert(record);
                diagnosisHistory.add(record);

                Log.d(TAG, "诊断记录已保存: round=" + currentRound);
            } catch (Exception e) {
                Log.e(TAG, "saveDiagnosisRecord error: " + e.getMessage());
            }
        });
    }

    // ==================== 完成维修 ====================

    private void confirmCompleteRepair() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.repair_complete_confirm_title)
                .setMessage(R.string.repair_complete_confirm_message)
                .setPositiveButton(R.string.confirm, (dialog, which) -> completeRepair())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void completeRepair() {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                workOrderDao.updateStatus(workOrderId, WorkOrderStatus.VERIFYING.name(), now);

                if (currentWorkOrder != null) {
                    currentWorkOrder.setStatus(WorkOrderStatus.VERIFYING.name());
                    currentWorkOrder.setUpdatedAt(now);
                }

                runOnUiThread(() -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(Config.EXTRA_WORKORDER_ID, workOrderId);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "completeRepair error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.repair_error_complete, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ==================== 权限 ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePhotoIntent();
            } else {
                Toast.makeText(this, R.string.repair_error_camera_permission, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(this, R.string.repair_error_audio_permission, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== 照片适配器 ====================

    /**
     * 照片缩略图 RecyclerView 适配器
     */
    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

        @androidx.annotation.NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo_thumbnail, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull PhotoViewHolder holder, int position) {
            String base64 = photoBase64List.get(position);
            try {
                byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4; // 缩略图，4倍压缩
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

                // 释放旧 bitmap
                Bitmap oldBitmap = (Bitmap) holder.ivPhoto.getTag();
                if (oldBitmap != null && !oldBitmap.isRecycled()) {
                    oldBitmap.recycle();
                }

                holder.ivPhoto.setImageBitmap(bitmap);
                holder.ivPhoto.setTag(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "PhotoAdapter decode error: " + e.getMessage());
                holder.ivPhoto.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            holder.btnDelete.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition < photoBase64List.size()) {
                    // 释放被删除照片的 bitmap
                    Bitmap removedBitmap = (Bitmap) holder.ivPhoto.getTag();
                    if (removedBitmap != null && !removedBitmap.isRecycled()) {
                        removedBitmap.recycle();
                    }
                    photoBase64List.remove(adapterPosition);
                    if (adapterPosition < photoFilePathList.size()) {
                        photoFilePathList.remove(adapterPosition);
                    }
                    notifyDataSetChanged();
                    updatePhotoRecyclerView();
                }
            });
        }

        @Override
        public int getItemCount() {
            return photoBase64List.size();
        }

        @Override
        public void onViewRecycled(@androidx.annotation.NonNull PhotoViewHolder holder) {
            super.onViewRecycled(holder);
            Bitmap bitmap = (Bitmap) holder.ivPhoto.getTag();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            ImageButton btnDelete;

            PhotoViewHolder(View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.ivPhoto);
                btnDelete = itemView.findViewById(R.id.btnDeletePhoto);
            }
        }
    }
}
