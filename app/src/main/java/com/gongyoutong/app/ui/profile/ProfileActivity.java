package com.gongyoutong.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.gongyoutong.app.R;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleDao;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.ui.main.MainActivity;
import com.gongyoutong.app.ui.schedule.ScheduleActivity;
import com.gongyoutong.app.ui.settings.SettingsActivity;
import com.gongyoutong.app.ui.workorder.WorkOrderActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvWorkerName, tvWorkerId;
    private TextView tvPendingCount, tvInProgressCount, tvCompletedCount;
    private TextView tvMonthlyCount, tvMonthlyIncome;
    private TextView tvNotificationBadge, tvRating;
    private ImageButton btnEditProfile;
    private LinearLayout layoutNotifications, layoutRating, layoutSupport;
    private LinearLayout layoutAbout, layoutMapSettings;

    private AppDatabase database;
    private ScheduleDao scheduleDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_profile);

        database = AppDatabase.getInstance(this);
        scheduleDao = database.scheduleDao();
        prefs = getSharedPreferences("gyt_prefs", MODE_PRIVATE);

        initViews();
        setupToolbar();
        setupBottomNav();
        setupClickListeners();
        setupBackHandler();
        loadWorkerInfo();
        loadStatistics();
    }

    private void initViews() {
        tvWorkerName = findViewById(R.id.tvWorkerName);
        tvWorkerId = findViewById(R.id.tvWorkerId);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvInProgressCount = findViewById(R.id.tvInProgressCount);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);
        tvMonthlyCount = findViewById(R.id.tvMonthlyCount);
        tvMonthlyIncome = findViewById(R.id.tvMonthlyIncome);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        tvRating = findViewById(R.id.tvRating);
        layoutNotifications = findViewById(R.id.layoutNotifications);
        layoutRating = findViewById(R.id.layoutRating);
        layoutSupport = findViewById(R.id.layoutSupport);
        layoutAbout = findViewById(R.id.layoutAbout);
        layoutMapSettings = findViewById(R.id.layoutMapSettings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v ->
            getOnBackPressedDispatcher().onBackPressed()
        );
    }

    // 【修复】底部导航改为用 REORDER_TO_FRONT 唤醒已有 Activity，不再 finish 破坏 Back 栈
    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_profile);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_workorder) {
                Intent intent = new Intent(this, WorkOrderActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_schedule) {
                Intent intent = new Intent(this, ScheduleActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_knowledge) {
                Intent intent = new Intent(this, com.gongyoutong.app.ui.knowledge.KnowledgeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    private void setupClickListeners() {
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        layoutNotifications.setOnClickListener(v -> showNotificationsDialog());

        layoutRating.setOnClickListener(v -> showRatingDialog());

        layoutSupport.setOnClickListener(v -> showSupportDialog());

        layoutMapSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        layoutAbout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("关于工友通")
                .setMessage("工友通 v1.0\n\n专为蓝领师傅打造的工作管理平台。\n\n集日程管理、AI智能助手、导航等功能于一体，让服务更高效！\n\n© 2025 工友通")
                .setPositiveButton(R.string.ok, null)
                .show();
        });
    }

    /**
     * 编辑资料对话框
     */
    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑资料");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText etName = new EditText(this);
        etName.setHint("姓名");
        etName.setText(tvWorkerName.getText());
        layout.addView(etName);

        EditText etWorkerId = new EditText(this);
        etWorkerId.setHint("工号");
        etWorkerId.setText(prefs.getString("worker_id", ""));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        etWorkerId.setLayoutParams(lp);
        layout.addView(etWorkerId);

        builder.setView(layout);
        builder.setPositiveButton("保存", (d, w) -> {
            String name = etName.getText().toString().trim();
            String workerId = etWorkerId.getText().toString().trim();
            if (!name.isEmpty()) {
                prefs.edit().putString("worker_name", name).apply();
                tvWorkerName.setText(name);
            }
            if (!workerId.isEmpty()) {
                prefs.edit().putString("worker_id", workerId).apply();
                tvWorkerId.setText("工号：" + workerId);
            }
            Toast.makeText(this, "资料已保存", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 消息通知 - 显示已完成/待办日程通知
     */
    private void showNotificationsDialog() {
        executor.execute(() -> {
            List<ScheduleEntity> all = scheduleDao.getAll();
            StringBuilder sb = new StringBuilder();
            int pending = 0, inProgress = 0;
            long now = System.currentTimeMillis();
            long oneHour = 3600 * 1000;

            for (ScheduleEntity s : all) {
                if ("待出发".equals(s.getStatus()) && s.getTime() > now
                        && s.getTime() - now < oneHour) {
                    pending++;
                }
                if ("进行中".equals(s.getStatus())) {
                    inProgress++;
                }
            }

            if (pending > 0) {
                sb.append("一小时内即将出发的日程 ").append(pending).append(" 项\n");
            }
            if (inProgress > 0) {
                sb.append("正在进行中的工作 ").append(inProgress).append(" 项\n");
            }
            if (sb.length() == 0) {
                sb.append("暂无新通知\n\n所有工作已安排妥当！");
            }

            String message = sb.toString().trim();
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                    .setTitle("消息通知")
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            });
        });
    }

    /**
     * 服务评价 - 显示完成统计
     */
    private void showRatingDialog() {
        executor.execute(() -> {
            List<ScheduleEntity> all = scheduleDao.getAll();
            int completed = 0;
            int total = all.size();
            long now = System.currentTimeMillis();
            long monthMs = 30L * 24 * 3600 * 1000;
            int monthlyCompleted = 0;
            int onTime = 0;

            for (ScheduleEntity s : all) {
                if ("已完成".equals(s.getStatus())) {
                    completed++;
                    if (s.getUpdatedAt() > now - monthMs) {
                        monthlyCompleted++;
                    }
                    if (s.getTime() > 0 && s.getUpdatedAt() <= s.getTime() + 3600 * 1000) {
                        onTime++;
                    }
                }
            }

            double onTimeRate = completed > 0 ? Math.round((double) onTime / completed * 100) : 0;
            StringBuilder sb = new StringBuilder();
            sb.append("累计完成：").append(completed).append(" 单\n");
            sb.append("本月完成：").append(monthlyCompleted).append(" 单\n");
            sb.append("准时率：").append((int) onTimeRate).append("%\n\n");
            sb.append("— 客户评价 —\n");
            if (completed >= 10) {
                sb.append("服务评价良好，继续保持！");
            } else if (completed > 0) {
                sb.append("服务好，评价自然好！");
            } else {
                sb.append("暂无客户评价");
            }

            String message = sb.toString();
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                    .setTitle("服务评价")
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            });
        });
    }

    /**
     * 客服中心 - 支持拨打电话
     */
    private void showSupportDialog() {
        new AlertDialog.Builder(this)
            .setTitle("客服中心")
            .setMessage("工作时间：周一至周六 8:00-20:00\n\n客服热线：400-888-8888\n\n如有问题请在工作时间内联系客服，我们将竭诚为您服务。")
            .setPositiveButton("拨打", (d, w) -> {
                Intent callIntent = new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:400-888-8888"));
                startActivity(callIntent);
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void loadWorkerInfo() {
        String name = prefs.getString("worker_name", null);
        if (name != null) {
            tvWorkerName.setText(name);
        }

        String workerId = prefs.getString("worker_id", null);
        if (workerId != null) {
            tvWorkerId.setText("工号：" + workerId);
        }
    }

    private void loadStatistics() {
        executor.execute(() -> {
            List<ScheduleEntity> all = scheduleDao.getAll();

            int pending = 0;
            int inProgress = 0;
            int completed = 0;

            Calendar now = Calendar.getInstance();
            int currentMonth = now.get(Calendar.MONTH);
            int currentYear = now.get(Calendar.YEAR);
            int monthlyCompleted = 0;

            for (ScheduleEntity entity : all) {
                String status = entity.getStatus();
                if ("待出发".equals(status)) {
                    pending++;
                } else if ("进行中".equals(status)) {
                    inProgress++;
                } else if ("已完成".equals(status)) {
                    completed++;
                    Calendar scheduleCal = Calendar.getInstance();
                    scheduleCal.setTimeInMillis(entity.getTime());
                    if (scheduleCal.get(Calendar.MONTH) == currentMonth
                        && scheduleCal.get(Calendar.YEAR) == currentYear) {
                        monthlyCompleted++;
                    }
                }
            }

            final int finalPending = pending;
            final int finalInProgress = inProgress;
            final int finalCompleted = completed;
            final int finalMonthly = monthlyCompleted;

            runOnUiThread(() -> {
                tvPendingCount.setText(String.valueOf(finalPending));
                tvInProgressCount.setText(String.valueOf(finalInProgress));
                tvCompletedCount.setText(String.valueOf(finalCompleted));
                tvMonthlyCount.setText(finalMonthly + " 单");
                // 【修复】月收入 = 单数 × 每单单价（后续可通过配置修改单价）
                tvMonthlyIncome.setText(String.format(Locale.CHINA, "¥%.2f", finalMonthly * 100.0));
                tvRating.setText("暂无评价");
                tvNotificationBadge.setText("0");
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 同步底部导航状态
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_profile);
        loadWorkerInfo();
        loadStatistics();
    }

    @Override
    protected void onDestroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}
