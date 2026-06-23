package com.gongyoutong.app.ui.income;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.gongyoutong.app.R;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.WorkOrderDao;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 收入分析页面
 */
public class IncomeActivity extends AppCompatActivity {

    private TextView tvMonthIncome, tvMonthOrders, tvAvgPrice;
    private WorkOrderDao workOrderDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_income);

        workOrderDao = AppDatabase.getInstance(this).workOrderDao();

        initViews();
        setupToolbar();
        loadIncomeData();
    }

    private void initViews() {
        tvMonthIncome = findViewById(R.id.tvMonthIncome);
        tvMonthOrders = findViewById(R.id.tvMonthOrders);
        tvAvgPrice = findViewById(R.id.tvAvgPrice);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadIncomeData() {
        executor.execute(() -> {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long monthStart = cal.getTimeInMillis();

            double monthIncome = workOrderDao.getTotalIncome(monthStart);
            int monthOrders = workOrderDao.getCompletedCount(monthStart);
            double avgPrice = monthOrders > 0 ? monthIncome / monthOrders : 0;

            runOnUiThread(() -> {
                tvMonthIncome.setText(String.format(Locale.CHINA, "¥%.0f", monthIncome));
                tvMonthOrders.setText(monthOrders + " 单");
                tvAvgPrice.setText(String.format(Locale.CHINA, "¥%.0f", avgPrice));
            });
        });
    }
}
