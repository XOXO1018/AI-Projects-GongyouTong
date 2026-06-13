package com.gongyoutong.app.ui.workorder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.gongyoutong.app.Config;
import com.gongyoutong.app.R;
import com.gongyoutong.app.data.WorkOrder;
import com.gongyoutong.app.data.WorkOrderAdapter;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.WorkOrderDao;
import com.gongyoutong.app.database.WorkOrderEntity;
import com.gongyoutong.app.ui.main.MainActivity;
import com.gongyoutong.app.ui.profile.ProfileActivity;
import com.gongyoutong.app.ui.schedule.ScheduleActivity;
import com.gongyoutong.app.ui.knowledge.KnowledgeActivity;
import com.gongyoutong.app.workorder.MockDispatcher;
import com.gongyoutong.app.workorder.WorkOrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工单列表页
 * 显示工单列表，支持按状态筛选
 */
public class WorkOrderActivity extends AppCompatActivity {

    private static final String TAG = "WorkOrderActivity";

    private RecyclerView rvWorkOrders;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyTitle;
    private TextView tvEmptyHint;
    private TabLayout tabFilter;
    private MaterialToolbar toolbar;

    private WorkOrderAdapter adapter;
    private final List<WorkOrder> allWorkOrders = new ArrayList<>();
    private final List<WorkOrder> filteredWorkOrders = new ArrayList<>();

    private AppDatabase database;
    private WorkOrderDao workOrderDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int currentFilter = 0; // 0=全部, 1=待接单, 2=进行中, 3=已完成, 4=异常

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_workorder);

        database = AppDatabase.getInstance(this);
        workOrderDao = database.workOrderDao();

        initViews();
        setupToolbar();
        setupTabFilter();
        setupBottomNav();
        setupRecyclerView();
        loadWorkOrders();
    }

    private void initViews() {
        rvWorkOrders = findViewById(R.id.rvWorkOrders);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        tabFilter = findViewById(R.id.tabFilter);
        toolbar = findViewById(R.id.toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupTabFilter() {
        tabFilter.addTab(tabFilter.newTab().setText(R.string.workorder_tab_all));
        tabFilter.addTab(tabFilter.newTab().setText(R.string.workorder_tab_pending));
        tabFilter.addTab(tabFilter.newTab().setText(R.string.workorder_tab_active));
        tabFilter.addTab(tabFilter.newTab().setText(R.string.workorder_tab_completed));
        tabFilter.addTab(tabFilter.newTab().setText(R.string.workorder_tab_exception));

        tabFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentFilter = tab.getPosition();
                applyFilter();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_workorder);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_workorder) {
                return true;
            } else if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_schedule) {
                Intent intent = new Intent(this, ScheduleActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_knowledge) {
                Intent intent = new Intent(this, KnowledgeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new WorkOrderAdapter(workOrder -> {
            String orderNo = workOrder.getOrderNo();
            Log.d(TAG, "onItemClick: orderNo=" + orderNo);
            Intent intent = new Intent(this, WorkOrderDetailActivity.class);
            intent.putExtra(Config.EXTRA_WORKORDER_ID, orderNo);
            startActivity(intent);
        });
        rvWorkOrders.setLayoutManager(new LinearLayoutManager(this));
        rvWorkOrders.setAdapter(adapter);
    }

    private void loadWorkOrders() {
        executor.execute(() -> {
            // 如果没有待接单工单，生成模拟数据
            try {
                if (!MockDispatcher.hasPendingOrders(workOrderDao)) {
                    Log.d(TAG, "loadWorkOrders: generating mock data");
                    for (int i = 0; i < 5; i++) {
                        WorkOrderEntity mockOrder = MockDispatcher.generateMockWorkOrder();
                        MockDispatcher.dispatchToDatabase(mockOrder, workOrderDao);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadWorkOrders: mock data error", e);
            }

            List<WorkOrderEntity> entities;
            try {
                entities = workOrderDao.getAll();
            } catch (Exception e) {
                Log.e(TAG, "loadWorkOrders: query error", e);
                entities = new ArrayList<>();
            }

            Log.d(TAG, "loadWorkOrders: loaded " + entities.size() + " orders");
            allWorkOrders.clear();
            for (WorkOrderEntity entity : entities) {
                allWorkOrders.add(WorkOrder.fromEntity(entity));
            }

            runOnUiThread(() -> {
                applyFilter();
                updateEmptyState();
            });
        });
    }

    private void applyFilter() {
        filteredWorkOrders.clear();

        for (WorkOrder wo : allWorkOrders) {
            WorkOrderStatus status = WorkOrderStatus.fromString(wo.getStatus());
            boolean match = false;

            switch (currentFilter) {
                case 0: // 全部
                    match = true;
                    break;
                case 1: // 待接单
                    match = status == WorkOrderStatus.PENDING;
                    break;
                case 2: // 进行中
                    match = status != WorkOrderStatus.COMPLETED
                            && status != WorkOrderStatus.EXCEPTION
                            && status != WorkOrderStatus.PENDING;
                    break;
                case 3: // 已完成
                    match = status == WorkOrderStatus.COMPLETED;
                    break;
                case 4: // 异常
                    match = status == WorkOrderStatus.EXCEPTION;
                    break;
            }

            if (match) {
                filteredWorkOrders.add(wo);
            }
        }

        adapter.setList(new ArrayList<>(filteredWorkOrders));
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredWorkOrders.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvWorkOrders.setVisibility(View.GONE);

            String[][] emptyTexts = {
                    {"暂无工单", "新工单派发后会显示在这里"},
                    {"暂无待接单工单", "新工单派发后会显示在这里"},
                    {"暂无进行中工单", "接单后工单会显示在这里"},
                    {"暂无已完成工单", "完成服务后工单会显示在这里"},
                    {"暂无异常工单", "遇到异常情况的工单会显示在这里"}
            };
            int index = Math.min(currentFilter, emptyTexts.length - 1);
            tvEmptyTitle.setText(emptyTexts[index][0]);
            tvEmptyHint.setText(emptyTexts[index][1]);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvWorkOrders.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_workorder);

        // 刷新数据
        executor.execute(() -> {
            List<WorkOrderEntity> entities = workOrderDao.getAll();
            allWorkOrders.clear();
            for (WorkOrderEntity entity : entities) {
                allWorkOrders.add(WorkOrder.fromEntity(entity));
            }
            runOnUiThread(() -> {
                applyFilter();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
