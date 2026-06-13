package com.gongyoutong.app.ui.knowledge;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.gongyoutong.app.Config;
import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.OnlineKnowledgeService;
import com.gongyoutong.app.ai.OnlineKnowledgeService.OnlineKnowledgeItem;
import com.gongyoutong.app.ai.VivoAiService;
import com.gongyoutong.app.ai.VivoAsrService;
import com.gongyoutong.app.data.KnowledgeAdapter;
import com.gongyoutong.app.data.OnlineKnowledgeAdapter;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.KnowledgeDao;
import com.gongyoutong.app.database.KnowledgeEntity;
import com.gongyoutong.app.ui.main.MainActivity;
import com.gongyoutong.app.ui.profile.ProfileActivity;
import com.gongyoutong.app.ui.schedule.ScheduleActivity;
import com.gongyoutong.app.ui.workorder.WorkOrderActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KnowledgeActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 2001;
    private static final int REQ_CAMERA = 2002;
    private static final int TAB_LOCAL = 0;
    private static final int TAB_ONLINE = 1;

    // 本地知识
    private RecyclerView rvLocal;
    private View layoutEmpty;
    private KnowledgeAdapter localAdapter;

    // 在线知识
    private RecyclerView rvOnline;
    private OnlineKnowledgeAdapter onlineAdapter;
    private LinearProgressIndicator progressOnline;
    private TextView tvOnlineDate;
    private View layoutOnlineLoading;

    // Tab
    private TabLayout tabLayout;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabAdd;

    private AppDatabase database;
    private KnowledgeDao knowledgeDao;
    private VivoAiService aiService;
    private VivoAsrService asrService;
    private OnlineKnowledgeService onlineService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean isListening = false;
    private String asrText = "";
    private final List<OnlineKnowledgeItem> onlineItems = new ArrayList<>();

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> documentLauncher;
    private BottomSheetDialog inputDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);

        database = AppDatabase.getInstance(this);
        knowledgeDao = database.knowledgeDao();
        aiService = new VivoAiService();
        asrService = new VivoAsrService();
        onlineService = OnlineKnowledgeService.getInstance();

        initViews();
        setupToolbar();
        setupBottomNav();
        setupTabs();
        setupLocalList();
        setupOnlineList();
        registerLaunchers();

        loadLocalData();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        rvLocal = findViewById(R.id.rvKnowledge);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        rvOnline = findViewById(R.id.rvOnlineKnowledge);
        progressOnline = findViewById(R.id.progressOnline);
        tvOnlineDate = findViewById(R.id.tvOnlineDate);
        layoutOnlineLoading = findViewById(R.id.layoutOnlineLoading);
        fabAdd = findViewById(R.id.fabAdd);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_knowledge);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_knowledge) return true;
            navTo(id);
            return true;
        });
    }

    private void navTo(int id) {
        Intent i = null;
        if (id == R.id.nav_home) i = new Intent(this, MainActivity.class);
        else if (id == R.id.nav_workorder) i = new Intent(this, WorkOrderActivity.class);
        else if (id == R.id.nav_schedule) i = new Intent(this, ScheduleActivity.class);
        else if (id == R.id.nav_profile) i = new Intent(this, ProfileActivity.class);
        if (i != null) { i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(i); }
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("我的知识"));
        tabLayout.addTab(tabLayout.newTab().setText("联网获取"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { onTabChanged(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void onTabChanged(int position) {
        if (position == TAB_LOCAL) {
            rvLocal.setVisibility(View.VISIBLE);
            rvOnline.setVisibility(View.GONE);
            layoutOnlineLoading.setVisibility(View.GONE);
            fabAdd.setVisibility(View.VISIBLE);
            loadLocalData();
        } else {
            rvLocal.setVisibility(View.GONE);
            rvOnline.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            fabAdd.setVisibility(View.GONE);
            if (onlineItems.isEmpty()) loadDailyRecommendations();
        }
    }

    private void setupLocalList() {
        localAdapter = new KnowledgeAdapter(entity -> {
            Intent intent = new Intent(this, KnowledgeDetailActivity.class);
            intent.putExtra(KnowledgeDetailActivity.EXTRA_KNOWLEDGE_ID, entity.getId());
            startActivity(intent);
        });
        rvLocal.setLayoutManager(new LinearLayoutManager(this));
        rvLocal.setAdapter(localAdapter);

        fabAdd.setOnClickListener(v -> showInputDialog());
    }

    private void setupOnlineList() {
        onlineAdapter = new OnlineKnowledgeAdapter(new OnlineKnowledgeAdapter.OnItemActionListener() {
            @Override
            public void onViewContent(OnlineKnowledgeItem item) {
                // 点击卡片→跳转 WebView 阅读
                Intent intent = new Intent(KnowledgeActivity.this, KnowledgeWebViewActivity.class);
                intent.putExtra(KnowledgeWebViewActivity.EXTRA_TITLE, item.getTitle());
                intent.putExtra(KnowledgeWebViewActivity.EXTRA_HTML, item.getHtmlContent());
                startActivity(intent);
            }

            @Override
            public void onAddToLocal(OnlineKnowledgeItem item, int position) {
                addToLocalKnowledge(item, position);
            }
        });
        rvOnline.setLayoutManager(new LinearLayoutManager(this));
        rvOnline.setAdapter(onlineAdapter);
    }

    private void registerLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePhotoUri(result.getData().getData());
                    }
                });
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Bitmap bitmap = null;
                        if (result.getData() != null && result.getData().getExtras() != null) {
                            bitmap = (Bitmap) result.getData().getExtras().get("data");
                        }
                        showManualInputDialog("photo", bitmap != null ? "（图片内容，请手动补充描述）" : "");
                    }
                });
        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleDocumentUri(result.getData().getData());
                    }
                });
    }

    // ==================== 本地知识 ====================

    private void loadLocalData() {
        executor.execute(() -> {
            List<KnowledgeEntity> list = knowledgeDao.getAll();
            runOnUiThread(() -> {
                localAdapter.setList(list);
                if (tabLayout.getSelectedTabPosition() == TAB_LOCAL) {
                    layoutEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    rvLocal.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        });
    }

    // ==================== 联网获取 ====================

    private void loadDailyRecommendations() {
        showOnlineLoading(true);
        tvOnlineDate.setText("今日推荐 · " + DateFormat.getDateInstance().format(new Date()));

        int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        onlineService.getDailyRecommendations(dayOfYear, new OnlineKnowledgeService.RecommendCallback() {
            @Override
            public void onSuccess(List<OnlineKnowledgeItem> items) {
                showOnlineLoading(false);
                onlineItems.clear();
                onlineItems.addAll(items);
                onlineAdapter.setList(items);
            }

            @Override
            public void onError(String msg) {
                showOnlineLoading(false);
                Toast.makeText(KnowledgeActivity.this, "获取推荐失败: " + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOnlineLoading(boolean show) {
        layoutOnlineLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) progressOnline.setVisibility(View.VISIBLE);
    }

    /**
     * 添加到本地知识库：AI 生成总结 + 思维导图后存入
     */
    private void addToLocalKnowledge(OnlineKnowledgeItem item, int position) {
        Toast.makeText(this, "AI 正在总结并生成思维导图...", Toast.LENGTH_SHORT).show();

        String rawContent = buildRawContent(item);
        onlineService.generateSummaryAndMindMap(item.getTitle(), rawContent,
                new OnlineKnowledgeService.SaveCallback() {
            @Override
            public void onSuccess(String title, String summary, String mindMapJson) {
                saveToDatabase(item, title, summary, mindMapJson, position);
            }

            @Override
            public void onError(String msg) {
                // 降级：直接保存原文
                saveToDatabase(item, item.getTitle(), item.getSummary(), "", position);
            }
        });
    }

    private String buildRawContent(OnlineKnowledgeItem item) {
        return "标题：" + item.getTitle() + "\n"
                + "分类：" + item.getCategory() + "\n"
                + "摘要：" + item.getSummary() + "\n"
                + "要点：" + item.getKeyPoints() + "\n"
                + "工具：" + item.getToolsRequired() + "\n"
                + "安全：" + item.getSafetyNote();
    }

    private void saveToDatabase(OnlineKnowledgeItem item, String title, String summary,
                                String mindMapJson, int position) {
        SharedPreferences prefs = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE);
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTitle(title != null && !title.isEmpty() ? title : item.getTitle());
        entity.setRawContent(buildRawContent(item));
        entity.setAiSummary(summary != null ? summary : "");
        entity.setMindMapJson(mindMapJson != null ? mindMapJson : "");
        entity.setSourceType("online");
        entity.setCreatorName(prefs.getString("worker_name", "工友通师傅"));
        long now = System.currentTimeMillis();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        executor.execute(() -> {
            knowledgeDao.insert(entity);
            runOnUiThread(() -> {
                onlineAdapter.markAdded(position);
                Toast.makeText(this, "✓ 已添加到我的知识", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ==================== 录入知识 ====================

    private void showInputDialog() {
        if (inputDialog != null && inputDialog.isShowing()) inputDialog.dismiss();
        inputDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_knowledge_input, null);
        inputDialog.setContentView(view);

        view.findViewById(R.id.layoutVoice).setOnClickListener(v -> {
            inputDialog.dismiss(); startVoiceInput();
        });
        view.findViewById(R.id.layoutPhoto).setOnClickListener(v -> {
            inputDialog.dismiss(); showPhotoChoice();
        });
        view.findViewById(R.id.layoutDocument).setOnClickListener(v -> {
            inputDialog.dismiss(); openDocumentPicker();
        });
        view.findViewById(R.id.layoutText).setOnClickListener(v -> {
            inputDialog.dismiss(); showManualInputDialog("text", "");
        });
        inputDialog.show();
    }

    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        doStartVoice();
    }

    private void doStartVoice() {
        isListening = true;
        asrText = "";
        Toast.makeText(this, "正在录音，请说出要记录的知识...", Toast.LENGTH_LONG).show();

        asrService.startRecognition(new VivoAsrService.AsrCallback() {
            @Override public void onResult(String text, boolean isFinal) {
                runOnUiThread(() -> {
                    asrText = text;
                    if (isFinal) {
                        isListening = false; asrService.stopRecognition();
                        if (!asrText.trim().isEmpty()) saveLocalKnowledge("voice", asrText.trim());
                        else Toast.makeText(KnowledgeActivity.this, "未识别到内容", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String errorMessage) {
                runOnUiThread(() -> { isListening = false;
                    Toast.makeText(KnowledgeActivity.this, "语音识别失败: " + errorMessage, Toast.LENGTH_LONG).show(); });
            }
            @Override public void onRecordingStateChanged(boolean recording) {
                if (!recording && isListening) isListening = false;
            }
        });
    }

    private void showPhotoChoice() {
        new AlertDialog.Builder(this)
                .setTitle("选择图片来源")
                .setItems(new String[]{"拍照", "从相册选择"}, (d, which) -> {
                    if (which == 0) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                        } else launchCamera();
                    } else launchGallery();
                }).show();
    }

    private void launchCamera() { cameraLauncher.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)); }
    private void launchGallery() {
        galleryLauncher.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
    }
    private void handlePhotoUri(Uri uri) { showManualInputDialog("photo", ""); }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        documentLauncher.launch(Intent.createChooser(intent, "选择文档"));
    }

    private void handleDocumentUri(Uri uri) {
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String line; while ((line = r.readLine()) != null) sb.append(line).append("\n");
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "文档读取失败", Toast.LENGTH_LONG).show());
                return;
            }
            String content = sb.toString().trim();
            if (content.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "文档内容为空", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> saveLocalKnowledge("document", content));
        });
    }

    private void showManualInputDialog(String sourceType, String prefill) {
        EditText et = new EditText(this);
        et.setHint("请输入要记录的知识内容...");
        et.setMinLines(5);
        et.setGravity(android.view.Gravity.TOP);
        if (!TextUtils.isEmpty(prefill)) et.setText(prefill);
        et.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle("录入知识")
                .setView(et)
                .setPositiveButton("保存并让 AI 提炼", (d, w) -> {
                    String content = et.getText().toString().trim();
                    if (TextUtils.isEmpty(content)) {
                        Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveLocalKnowledge(sourceType, content);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 保存本地录入的知识 —— AI 提取标题、总结并生成思维导图
     */
    private void saveLocalKnowledge(String sourceType, String rawContent) {
        Toast.makeText(this, "正在保存，AI 提炼中...", Toast.LENGTH_SHORT).show();

        SharedPreferences prefs = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE);
        String creator = prefs.getString("worker_name", "工友通师傅");

        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setRawContent(rawContent);
        entity.setSourceType(sourceType);
        entity.setCreatorName(creator);
        long now = System.currentTimeMillis();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setTitle(rawContent.length() > 20 ? rawContent.substring(0, 20) + "..." : rawContent);
        entity.setAiSummary("");
        entity.setMindMapJson("");

        executor.execute(() -> { knowledgeDao.insert(entity);
            runOnUiThread(() -> { localAdapter.addItem(entity); loadLocalData(); }); });

        // 异步 AI 总结 + 思维导图
        aiService.extractKnowledgeWithMindMap(rawContent, new VivoAiService.KnowledgeWithMindMapCallback() {
            @Override
            public void onSuccess(String title, String summary, String mindMapJson) {
                entity.setTitle(title);
                entity.setAiSummary(summary);
                entity.setMindMapJson(mindMapJson != null ? mindMapJson : "");
                entity.setUpdatedAt(System.currentTimeMillis());
                executor.execute(() -> { knowledgeDao.update(entity);
                    runOnUiThread(() -> loadLocalData()); });
            }
            @Override public void onError(String msg) { /* AI 失败不影响保存 */ }
        });
    }

    // ==================== 权限 ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_RECORD_AUDIO) {
            if (ok) doStartVoice(); else Toast.makeText(this, "需要录音权限", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_CAMERA) {
            if (ok) launchCamera(); else Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.bottomNav).setTag(R.id.nav_knowledge);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_knowledge);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (asrService != null) asrService.destroy();
        if (executor != null) executor.shutdown();
    }
}
