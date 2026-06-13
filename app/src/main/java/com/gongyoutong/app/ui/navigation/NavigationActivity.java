package com.gongyoutong.app.ui.navigation;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.gongyoutong.app.R;

public class NavigationActivity extends AppCompatActivity {

    private TextView tvNavTitle, tvNavSubtitle;
    private TextView tvTurnInstruction, tvNextTurn, tvRemaining;
    private TextView tvProgress, tvEta, tvRemainingTime, tvSpeed;
    private LinearLayout btnReport, btnReportIssue;
    private MaterialButton btnArrived;
    private FloatingActionButton fabRecenterNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_navigation);

        initViews();
        setupToolbar();
        setupBackHandler();
        setupClickListeners();
        loadNavigationData();
    }

    private void initViews() {
        // Toolbar views
        tvNavTitle = findViewById(R.id.tvNavTitle);
        tvNavSubtitle = findViewById(R.id.tvNavSubtitle);

        // Turn instruction views
        tvTurnInstruction = findViewById(R.id.tvTurnInstruction);
        tvNextTurn = findViewById(R.id.tvNextTurn);
        tvRemaining = findViewById(R.id.tvRemaining);

        // Progress views
        tvProgress = findViewById(R.id.tvProgress);

        // Info grid views
        tvEta = findViewById(R.id.tvEta);
        tvRemainingTime = findViewById(R.id.tvRemainingTime);
        tvSpeed = findViewById(R.id.tvSpeed);

        // Action buttons
        btnReport = findViewById(R.id.btnReport);
        btnReportIssue = findViewById(R.id.btnReportIssue);
        btnArrived = findViewById(R.id.btnArrived);

        // FAB
        fabRecenterNav = findViewById(R.id.fabRecenterNav);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(NavigationActivity.this)
                    .setTitle(R.string.exit_navigation)
                    .setMessage(R.string.exit_navigation_message)
                    .setPositiveButton(R.string.confirm, (d, w) -> finish())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            }
        });
    }

    private void setupClickListeners() {
        // Report location button
        btnReport.setOnClickListener(v -> {
            // Toast.makeText(this, getString(R.string.location_reported), Toast.LENGTH_SHORT).show();
        });

        // Report issue button
        btnReportIssue.setOnClickListener(v -> {
            showIssueDialog();
        });

        // Arrived button
        btnArrived.setOnClickListener(v -> {
            showArrivalDialog();
        });

        // Re-center FAB
        fabRecenterNav.setOnClickListener(v -> {
            Toast.makeText(this, "正在重新定位...", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 接收 Intent 数据
        String address = getIntent().getStringExtra("address");
        String title = getIntent().getStringExtra("title");
        if (address != null && !address.isEmpty()) {
            tvNavTitle.setText(address);
        }
        if (title != null && !title.isEmpty()) {
            tvNavSubtitle.setText(title);
        }
    }

    private void loadNavigationData() {
        // Get address from intent
        String address = getIntent().getStringExtra("address");
        if (address != null && !address.isEmpty()) {
            tvNavTitle.setText(address);
        }

        // Simulated navigation data
        updateNavigationInfo(
            "向前直行 200 米",
            "然后右转进入解放南路",
            "3.2 km",
            "1.2",
            "3.2",
            "10:08",
            "8 分钟",
            "24 km/h"
        );
    }

    private void updateNavigationInfo(
            String turnInstruction,
            String nextTurn,
            String remaining,
            String traveled,
            String total,
            String eta,
            String remainingTime,
            String speed
    ) {
        tvTurnInstruction.setText(turnInstruction);
        tvNextTurn.setText(nextTurn);
        tvRemaining.setText(remaining);
        tvProgress.setText("已行驶 " + traveled + " km，还剩 " + total + " km");
        tvEta.setText(eta);
        tvRemainingTime.setText(remainingTime);
        tvSpeed.setText(speed);
    }

    private void showArrivalDialog() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(getString(R.string.arrival_tip))
                .setMessage(getString(R.string.arrival_message))
                .setPositiveButton(getString(R.string.end_work), (d, w) -> {
                    // Toast.makeText(this, getString(R.string.schedule_completed), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(getString(R.string.continue_navigation), null)
                .show();
    }

    private void showIssueDialog() {
        String[] options = {
            "路况问题",
            "导航偏离",
            "定位不准",
            "目的地无法到达",
            "其他问题"
        };

        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("上报问题")
                .setItems(options, (d, which) -> {
                    // Toast.makeText(this, "已提交: " + options[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

}
