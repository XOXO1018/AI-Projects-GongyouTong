package com.gongyoutong.app.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.VivoAiService;
import com.gongyoutong.app.ai.VivoAsrService;
import com.gongyoutong.app.ai.ImageGenerationService;
import com.gongyoutong.app.data.Schedule;
import com.gongyoutong.app.data.ScheduleAdapter;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleDao;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.ui.detail.ScheduleDetailActivity;
import com.gongyoutong.app.ui.profile.ProfileActivity;
import com.gongyoutong.app.ui.schedule.ScheduleActivity;
import com.gongyoutong.app.ui.workorder.WorkOrderActivity;
import com.gongyoutong.app.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "gongyoutong_prefs";
    private static final String KEY_DEMO_ADDED = "demo_data_added";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private EditText etInput;
    private ImageView btnSend, btnVoice, btnImage;
    private TextView tvVoiceStatus, tvAiReply, tvAiReplyTime;
    private RecyclerView rvSchedule;
    private TextView tvScheduleCount;
    private View loadingView;
    private LinearLayout layoutEmpty, layoutAiReply;
    private MaterialCardView chatInputCard;
    private ScheduleAdapter adapter;
    private final List<Schedule> scheduleList = new ArrayList<>();

    private AppDatabase database;
    private ScheduleDao scheduleDao;
    private VivoAiService aiService;
    private VivoAsrService asrService;
    private ImageGenerationService imageGenerationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 语音识别状态
    private boolean isListening = false;
    private String asrAccumulatedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        // 初始化数据库
        database = AppDatabase.getInstance(this);
        scheduleDao = database.scheduleDao();
        aiService = new VivoAiService();
        asrService = new VivoAsrService();
        imageGenerationService = ImageGenerationService.getInstance();

        initViews();
        setupWindowInsets();
        setupBottomNav();
        setupRecyclerView();
        setupClickListeners();
        setupBackHandler();
        loadSchedulesFromDatabase();
        updateEmptyState();
    }

    private void initViews() {
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        btnVoice = findViewById(R.id.btnVoice);
        btnImage = findViewById(R.id.btnImage);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        rvSchedule = findViewById(R.id.rvSchedule);
        tvScheduleCount = findViewById(R.id.tvScheduleCount);
        loadingView = findViewById(R.id.loadingView);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        chatInputCard = findViewById(R.id.chatInputCard);
        layoutAiReply = findViewById(R.id.layoutAiReply);
        tvAiReply = findViewById(R.id.tvAiReply);
        tvAiReplyTime = findViewById(R.id.tvAiReplyTime);

        updateSendButtonState();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(chatInputCard, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = systemBars.bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom + 8);
            return insets;
        });
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_workorder) {
                Intent intent = new Intent(MainActivity.this, WorkOrderActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_schedule) {
                Intent intent = new Intent(MainActivity.this, ScheduleActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_knowledge) {
                Intent intent = new Intent(MainActivity.this,
                        com.gongyoutong.app.ui.knowledge.KnowledgeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new ScheduleAdapter(schedule -> {
            Intent intent = new Intent(MainActivity.this, ScheduleDetailActivity.class);
            intent.putExtra("schedule_id", schedule.getId());
            startActivity(intent);
        });

        adapter.setOnItemDeleteListener((schedule, position) -> {
            showDeleteConfirmDialog(schedule, position);
        });

        rvSchedule.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvSchedule.setAdapter(adapter);
        rvSchedule.setHasFixedSize(false);
    }

    private void setupClickListeners() {
        btnSend.setOnClickListener(v -> handleSend());

        btnVoice.setOnClickListener(v -> handleVoiceInput());

        btnImage.setOnClickListener(v -> handleImageGeneration());

        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSendButtonState();
            }
        });

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                handleSend();
                return true;
            }
            return false;
        });

        chatInputCard.setOnClickListener(v -> etInput.requestFocus());
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isListening) {
                    stopListening();
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("退出应用")
                    .setMessage("确定要退出工友通吗？")
                    .setPositiveButton(R.string.confirm, (d, w) -> finish())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            }
        });
    }

    private void updateSendButtonState() {
        String text = etInput.getText().toString().trim();
        boolean enabled = !TextUtils.isEmpty(text);
        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1.0f : 0.5f);
    }

    // ==================== 语音识别 ====================

    private void handleVoiceInput() {
        if (isListening) {
            // 正在录音，点击停止
            stopListening();
            return;
        }

        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
            return;
        }

        startListening();
    }

    private void startListening() {
        isListening = true;
        asrAccumulatedText = "";

        // 更新 UI 为录音状态
        updateVoiceButtonState(true);
        tvVoiceStatus.setVisibility(View.VISIBLE);
        tvVoiceStatus.setText("正在聆听，请说话...");
        etInput.setHint("语音识别中...");
        etInput.setText("");

        // 启动语音识别
        asrService.startRecognition(new VivoAsrService.AsrCallback() {
            @Override
            public void onResult(String text, boolean isFinal) {
                runOnUiThread(() -> {
                    asrAccumulatedText = text;
                    if (!text.isEmpty()) {
                        etInput.setText(text);
                        etInput.setSelection(text.length());
                        tvVoiceStatus.setText("识别中: " + text);
                    }

                    if (isFinal) {
                        // 识别完成
                        stopListening();

                        if (!asrAccumulatedText.trim().isEmpty()) {
                            // 自动触发发送
                            handleSend();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "未识别到语音内容，请重试", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    stopListening();
                    Toast.makeText(MainActivity.this,
                            "语音识别失败: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onRecordingStateChanged(boolean recording) {
                runOnUiThread(() -> {
                    if (!recording && isListening) {
                        // 录音意外停止
                        stopListening();
                    }
                });
            }
        });
    }

    private void stopListening() {
        isListening = false;
        asrService.stopRecognition();
        updateVoiceButtonState(false);
        tvVoiceStatus.setVisibility(View.GONE);
        etInput.setHint("输入日程或向我提问，例如：明天3点去修空调");
    }

    /**
     * 更新语音按钮的视觉状态
     */
    private void updateVoiceButtonState(boolean recording) {
        if (recording) {
            // 录音中：显示脉冲动画 + 红色背景
            btnVoice.setBackgroundResource(R.drawable.bg_voice_recording);
            btnVoice.startAnimation(AnimationUtils.loadAnimation(this, R.anim.voice_pulse));
            btnVoice.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));
        } else {
            // 停止：恢复原始样式
            btnVoice.clearAnimation();
            btnVoice.setBackgroundResource(R.drawable.bg_voice_light);
            btnVoice.setImageTintList(ContextCompat.getColorStateList(this, R.color.primary));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ==================== 文本发送 ====================

    private void handleSend() {
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String originalText = text;
        etInput.setText("");

        // 先进行意图识别，再路由到不同流程
        aiService.classifyInput(text, new VivoAiService.IntentCallback() {
            @Override
            public void onResult(VivoAiService.InputIntent intent) {
                runOnUiThread(() -> {
                    if (intent == VivoAiService.InputIntent.SCHEDULE) {
                        // 日程模式：显示全屏 loading，走原有日程解析流程
                        showLoading(true);
                        handleScheduleMode(originalText);
                    } else {
                        // 问答模式：不显示全屏 loading，用流式输出
                        handleChatModeStream(originalText);
                    }
                });
            }
        });
    }

    /**
     * 日程模式：解析日程并创建
     */
    private void handleScheduleMode(String text) {
        Toast.makeText(this, "正在调用 蓝心大模型 解析日程...", Toast.LENGTH_SHORT).show();

        aiService.parseScheduleFromText(text, new VivoAiService.AiCallback() {
            @Override
            public void onSuccess(Schedule schedule, String aiReminder, VivoAiService.AiSource source) {
                executor.execute(() -> {
                    ScheduleEntity entity = convertToEntity(schedule);
                    // 不保存本地模板提醒，让详情页流式生成更高质量的 AI 提醒
                    scheduleDao.insert(entity);

                    // 从数据库重新加载
                    List<Schedule> freshList = loadSchedulesFromDb();

                    runOnUiThread(() -> {
                        showLoading(false);
                        scheduleList.clear();
                        scheduleList.addAll(freshList);
                        adapter.setList(new ArrayList<>(scheduleList));
                        updateEmptyState();

                        // 隐藏对话气泡
                        layoutAiReply.setVisibility(View.GONE);

                        // 根据 AI 来源显示不同提示
                        if (source == VivoAiService.AiSource.CLOUD_AI_SUCCESS) {
                            // Toast.makeText(MainActivity.this, "✓ 蓝心大模型 解析成功，日程已创建！", Toast.LENGTH_LONG).show();
                        } else if (source == VivoAiService.AiSource.LOCAL_FALLBACK) {
                            Toast.makeText(MainActivity.this, "⚠️ 蓝心大模型 不可用，已使用本地规则解析日程", Toast.LENGTH_LONG).show();
                        } else {
                            // Toast.makeText(MainActivity.this, "✓ 日程已创建！", Toast.LENGTH_LONG).show();
                        }

                        // 自动跳转到详情页
                        Intent intent = new Intent(MainActivity.this, ScheduleDetailActivity.class);
                        intent.putExtra("schedule_id", schedule.getId());
                        startActivity(intent);
                    });
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    showLoading(false);
                    etInput.setText(text);
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 问答模式：流式输出，不显示全屏 loading
     */
    private void handleChatModeStream(String text) {
        // 显示轻量 loading 提示
        tvVoiceStatus.setVisibility(View.VISIBLE);
        tvVoiceStatus.setText("蓝心大模型思考中...");

        // 先清空并显示回复区域
        tvAiReply.setText("");
        layoutAiReply.setVisibility(View.VISIBLE);
        
        // 隐藏之前可能显示的图片
        ImageView ivGeneratedImage = findViewById(R.id.ivGeneratedImage);
        ivGeneratedImage.setVisibility(View.GONE);

        aiService.chatWithAiStream(text, new VivoAiService.StreamChatCallback() {
            @Override
            public void onStart() {
                runOnUiThread(() -> {
                    tvVoiceStatus.setText("蓝心大模型正在回复...");
                });
            }

            @Override
            public void onDelta(String currentText, boolean isDone) {
                runOnUiThread(() -> {
                    tvAiReply.setText(currentText);
                    if (isDone) {
                        // 完成：更新时间戳，隐藏 loading 提示
                        String timeStr = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
                        tvAiReplyTime.setText(timeStr);
                        tvVoiceStatus.setVisibility(View.GONE);
                        // Toast.makeText(MainActivity.this, "✓ 回复完成", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    tvVoiceStatus.setVisibility(View.GONE);
                    layoutAiReply.setVisibility(View.GONE);
                    etInput.setText(text);
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ==================== 数据加载 ====================

    private void loadSchedulesFromDatabase() {
        executor.execute(() -> {
            // 首页加载今日待出发和进行中的日程
            List<ScheduleEntity> entities = scheduleDao.getAll();
            scheduleList.clear();

            for (ScheduleEntity entity : entities) {
                String status = entity.getStatus();
                if (("待出发".equals(status) || "进行中".equals(status)) && isToday(new Date(entity.getTime()))) {
                    scheduleList.add(convertFromEntity(entity));
                }
            }

            // 按时间从早到晚排序
            scheduleList.sort((s1, s2) -> {
                if (s1 == null || s2 == null || s1.getTime() == null || s2.getTime() == null) return 0;
                return Long.compare(s1.getTime().getTime(), s2.getTime().getTime());
            });

            // 只在首次安装且列表为空时才添加演示数据
            if (scheduleList.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                boolean demoAdded = prefs.getBoolean(KEY_DEMO_ADDED, false);
                
                if (!demoAdded) {
                    prefs.edit().putBoolean(KEY_DEMO_ADDED, true).apply();
                    runOnUiThread(() -> {
                        loadDemoData();
                    });
                }
            }

            runOnUiThread(() -> {
                adapter.setList(new ArrayList<>(scheduleList));
                updateEmptyState();
            });
        });
    }

    private List<Schedule> loadSchedulesFromDb() {
        List<ScheduleEntity> entities = scheduleDao.getAll();
        List<Schedule> result = new ArrayList<>();
        for (ScheduleEntity entity : entities) {
            String status = entity.getStatus();
            if (("待出发".equals(status) || "进行中".equals(status)) && isToday(new Date(entity.getTime()))) {
                result.add(convertFromEntity(entity));
            }
        }
        result.sort((s1, s2) -> {
            if (s1 == null || s2 == null || s1.getTime() == null || s2.getTime() == null) return 0;
            return Long.compare(s1.getTime().getTime(), s2.getTime().getTime());
        });
        return result;
    }

    private boolean isToday(Date date) {
        return DateUtils.isToday(date);
    }

    private ScheduleEntity convertToEntity(Schedule schedule) {
        ScheduleEntity entity = new ScheduleEntity();
        entity.setId(schedule.getId());
        entity.setTitle(schedule.getTitle());
        entity.setAddress(schedule.getAddress());
        entity.setWorkType(schedule.getWorkType());
        entity.setTime(schedule.getTime() != null ? schedule.getTime().getTime() : System.currentTimeMillis());
        entity.setStatus(schedule.getStatus());
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }

    private Schedule convertFromEntity(ScheduleEntity entity) {
        Schedule schedule = new Schedule();
        schedule.setId(entity.getId());
        schedule.setTitle(entity.getTitle());
        schedule.setAddress(entity.getAddress());
        schedule.setWorkType(entity.getWorkType());
        schedule.setTime(new Date(entity.getTime()));
        schedule.setStatus(entity.getStatus());
        return schedule;
    }

    private void showLoading(boolean show) {
        if (loadingView != null) {
            loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmptyState() {
        tvScheduleCount.setText(scheduleList.size() + " 项");
        
        if (scheduleList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvSchedule.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvSchedule.setVisibility(View.VISIBLE);
        }
    }

    private void showDeleteConfirmDialog(Schedule schedule, int position) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_schedule_title)
            .setMessage(getString(R.string.delete_schedule_message, schedule.getTitle()))
            .setPositiveButton(R.string.delete, (d, w) -> {
                executor.execute(() -> {
                    scheduleDao.deleteById(schedule.getId());
                    scheduleList.remove(position);
                    runOnUiThread(() -> {
                        adapter.removeItem(position);
                        updateEmptyState();
                        // Toast.makeText(this, R.string.schedule_deleted, Toast.LENGTH_SHORT).show();
                    });
                });
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void loadDemoData() {
        executor.execute(() -> {
            // 演示数据 1 - 今天
            ScheduleEntity s1 = new ScheduleEntity();
            s1.setId("demo1");
            s1.setTitle("幸福小区安装热水器");
            s1.setAddress("幸福小区 A栋301");
            s1.setWorkType("安装服务");
            s1.setTime(new Date().getTime());
            s1.setStatus("待出发");
            s1.setCreatedAt(System.currentTimeMillis());
            s1.setUpdatedAt(System.currentTimeMillis());
            scheduleDao.insert(s1);

            // 演示数据 2 - 4小时后
            ScheduleEntity s2 = new ScheduleEntity();
            s2.setId("demo2");
            s2.setTitle("阳光花园疏通下水道");
            s2.setAddress("阳光花园 5栋2单元");
            s2.setWorkType("维修服务");
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 4);
            s2.setTime(cal.getTimeInMillis());
            s2.setStatus("待出发");
            s2.setCreatedAt(System.currentTimeMillis());
            s2.setUpdatedAt(System.currentTimeMillis());
            scheduleDao.insert(s2);

            // 演示数据 3 - 今天下午
            ScheduleEntity s3 = new ScheduleEntity();
            s3.setId("demo3");
            s3.setTitle("新城区写字楼空调清洗");
            s3.setAddress("新城区 商务大厦12层");
            s3.setWorkType("清洗服务");
            Calendar cal3 = Calendar.getInstance();
            cal3.add(Calendar.HOUR, 6);
            s3.setTime(cal3.getTimeInMillis());
            s3.setStatus("待出发");
            s3.setCreatedAt(System.currentTimeMillis());
            s3.setUpdatedAt(System.currentTimeMillis());
            scheduleDao.insert(s3);

            // 刷新列表
            scheduleList.clear();
            scheduleList.addAll(loadSchedulesFromDb());
            runOnUiThread(() -> {
                adapter.setList(new ArrayList<>(scheduleList));
                updateEmptyState();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 重置底部导航状态到首页
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);
        
        // 刷新数据
        executor.execute(() -> {
            scheduleList.clear();
            scheduleList.addAll(loadSchedulesFromDb());
            runOnUiThread(() -> {
                adapter.setList(new ArrayList<>(scheduleList));
                updateEmptyState();
            });
        });
    }

    /**
     * 处理图片生成请求
     */
    private void handleImageGeneration() {
        String text = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "请输入图片描述", Toast.LENGTH_SHORT).show();
            return;
        }

        // 清空输入框
        etInput.setText("");

        // 显示轻量 loading 提示
        tvVoiceStatus.setVisibility(View.VISIBLE);
        tvVoiceStatus.setText("蓝心大模型正在生成图片...");

        // 先清空并显示回复区域
        tvAiReply.setText("正在生成图片: " + text);
        layoutAiReply.setVisibility(View.VISIBLE);

        // 调用图片生成服务
        String prompt = "根据以下描述生成图片: " + text;
        imageGenerationService.generateImages(prompt, null, new ImageGenerationService.ImageGenerationCallback() {
            @Override
            public void onSuccess(List<String> imageUrls) {
                runOnUiThread(() -> {
                    tvVoiceStatus.setVisibility(View.GONE);
                    
                    // 显示第一张图片
                    if (!imageUrls.isEmpty()) {
                        displayGeneratedImage(imageUrls.get(0), text);
                    }
                    
                    // 更新时间
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvAiReplyTime.setText(sdf.format(new Date()));
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    tvVoiceStatus.setVisibility(View.GONE);
                    tvAiReply.setText("图片生成失败: " + msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 显示生成的图片
     */
    private void displayGeneratedImage(String imageUrl, String prompt) {
        // 获取图片显示控件
        ImageView ivGeneratedImage = findViewById(R.id.ivGeneratedImage);
        
        // 显示图片区域
        ivGeneratedImage.setVisibility(View.VISIBLE);
        
        // 更新文本回复
        tvAiReply.setText("已生成图片: " + prompt);
        
        // 使用Glide加载图片
        try {
            Glide.with(this)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .into(ivGeneratedImage);
        } catch (Exception e) {
            tvAiReply.setText("图片加载失败: " + e.getMessage());
            ivGeneratedImage.setVisibility(View.GONE);
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
}
