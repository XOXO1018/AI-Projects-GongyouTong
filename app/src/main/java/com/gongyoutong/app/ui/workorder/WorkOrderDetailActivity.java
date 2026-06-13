package com.gongyoutong.app.ui.workorder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.gongyoutong.app.Config;
import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.WorkOrderAiService;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.CustomerDao;
import com.gongyoutong.app.database.CustomerEntity;
import com.gongyoutong.app.database.RepairRecordDao;
import com.gongyoutong.app.database.RepairRecordEntity;
import com.gongyoutong.app.database.ScheduleDao;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.database.WorkOrderDao;
import com.gongyoutong.app.database.WorkOrderEntity;
import com.gongyoutong.app.ui.navigation.BaiduNavigationActivity;
import com.gongyoutong.app.workorder.WorkOrderStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工单详情页
 * 页面结构：顶部（工单状态+客户信息+地址导航+联系客户）→ 中间（故障描述+设备信息+维修记录+AI预判+工具推荐+风险提示）→ 底部操作按钮
 */
public class WorkOrderDetailActivity extends AppCompatActivity {

    private static final String TAG = "WorkOrderDetail";
    public static final String EXTRA_WORKORDER_ID = Config.EXTRA_WORKORDER_ID;
    private static final int REQUEST_CODE_REPAIR = 2001;

    // ==================== Views ====================

    // 顶部卡片
    private TextView tvOrderNo;
    private TextView tvStatus;
    private TextView tvTitle;
    private TextView tvWorkType;

    // 时间线
    private View timelineDot1, timelineDot2, timelineDot3, timelineDot4;
    private View timelineLine1, timelineLine2, timelineLine3;

    // 客户信息
    private TextView tvContactAvatar;
    private TextView tvContactName;
    private TextView tvContactPhone;
    private MaterialButton btnContact;
    private TextView tvAddress;
    private TextView tvDistance;
    private TextView tvAppointmentTime;
    private MaterialButton btnNavigate;

    // 故障描述
    private TextView tvDescription;

    // 设备信息
    private MaterialCardView cardDeviceInfo;
    private TextView tvDeviceBrand;
    private TextView tvDeviceModel;
    private TextView tvDeviceAge;

    // 历史维修记录
    private MaterialCardView cardRepairHistory;
    private LinearLayout layoutRepairRecords;
    private TextView tvRepairCount;
    private TextView tvNoRepairHistory;

    // AI 故障预判
    private MaterialCardView layoutAiPrediction;
    private TextView tvAiPrediction;
    private ProgressBar pbAiPrediction;

    // AI 工具推荐
    private MaterialCardView layoutAiTools;
    private TextView tvAiTools;
    private ProgressBar pbAiTools;

    // 风险提示
    private MaterialCardView cardRiskWarning;
    private TextView tvRiskLevel;
    private TextView tvRiskContent;

    // 底部操作
    private MaterialButton btnAction;
    private MaterialButton btnReportException;

    // ==================== Data ====================

    private AppDatabase database;
    private WorkOrderDao workOrderDao;
    private CustomerDao customerDao;
    private RepairRecordDao repairRecordDao;
    private ScheduleDao scheduleDao;
    private WorkOrderAiService aiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private WorkOrderEntity currentWorkOrder;
    private CustomerEntity currentCustomer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            setContentView(R.layout.activity_workorder_detail);

            database = AppDatabase.getInstance(this);
            workOrderDao = database.workOrderDao();
            customerDao = database.customerDao();
            repairRecordDao = database.repairRecordDao();
            scheduleDao = database.scheduleDao();
            aiService = WorkOrderAiService.getInstance();

            initViews();
            setupToolbar();

            String orderNo = getIntent().getStringExtra(EXTRA_WORKORDER_ID);
            Log.d(TAG, "onCreate: orderNo=" + orderNo);
            if (orderNo != null && !orderNo.isEmpty()) {
                loadWorkOrder(orderNo);
            } else {
                Log.e(TAG, "onCreate: orderNo is null or empty, finishing");
                Toast.makeText(this, "工单不存在", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate: fatal error", e);
            Toast.makeText(this, "页面加载失败", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        // 顶部卡片
        tvOrderNo = findViewById(R.id.tvOrderNo);
        tvStatus = findViewById(R.id.tvStatus);
        tvTitle = findViewById(R.id.tvTitle);
        tvWorkType = findViewById(R.id.tvWorkType);

        // 时间线
        timelineDot1 = findViewById(R.id.timelineDot1);
        timelineDot2 = findViewById(R.id.timelineDot2);
        timelineDot3 = findViewById(R.id.timelineDot3);
        timelineDot4 = findViewById(R.id.timelineDot4);
        timelineLine1 = findViewById(R.id.timelineLine1);
        timelineLine2 = findViewById(R.id.timelineLine2);
        timelineLine3 = findViewById(R.id.timelineLine3);

        // 客户信息
        tvContactAvatar = findViewById(R.id.tvContactAvatar);
        tvContactName = findViewById(R.id.tvContactName);
        tvContactPhone = findViewById(R.id.tvContactPhone);
        btnContact = findViewById(R.id.btnContact);
        tvAddress = findViewById(R.id.tvAddress);
        tvDistance = findViewById(R.id.tvDistance);
        tvAppointmentTime = findViewById(R.id.tvAppointmentTime);
        btnNavigate = findViewById(R.id.btnNavigate);

        // 故障描述
        tvDescription = findViewById(R.id.tvDescription);

        // 设备信息
        cardDeviceInfo = findViewById(R.id.cardDeviceInfo);
        tvDeviceBrand = findViewById(R.id.tvDeviceBrand);
        tvDeviceModel = findViewById(R.id.tvDeviceModel);
        tvDeviceAge = findViewById(R.id.tvDeviceAge);

        // 历史维修记录
        cardRepairHistory = findViewById(R.id.cardRepairHistory);
        layoutRepairRecords = findViewById(R.id.layoutRepairRecords);
        tvRepairCount = findViewById(R.id.tvRepairCount);
        tvNoRepairHistory = findViewById(R.id.tvNoRepairHistory);

        // AI 故障预判
        layoutAiPrediction = findViewById(R.id.layoutAiPrediction);
        tvAiPrediction = findViewById(R.id.tvAiPrediction);
        pbAiPrediction = findViewById(R.id.pbAiPrediction);

        // AI 工具推荐
        layoutAiTools = findViewById(R.id.layoutAiTools);
        tvAiTools = findViewById(R.id.tvAiTools);
        pbAiTools = findViewById(R.id.pbAiTools);

        // 风险提示
        cardRiskWarning = findViewById(R.id.cardRiskWarning);
        tvRiskLevel = findViewById(R.id.tvRiskLevel);
        tvRiskContent = findViewById(R.id.tvRiskContent);

        // 底部操作
        btnAction = findViewById(R.id.btnAction);
        btnReportException = findViewById(R.id.btnReportException);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ==================== 数据加载 ====================

    private void loadWorkOrder(String orderNo) {
        executor.execute(() -> {
            try {
                currentWorkOrder = workOrderDao.getById(orderNo);
            } catch (Exception e) {
                Log.e(TAG, "loadWorkOrder: query error for orderNo=" + orderNo, e);
            }

            if (currentWorkOrder == null) {
                Log.e(TAG, "loadWorkOrder: work order not found, orderNo=" + orderNo);
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                try {
                    currentWorkOrder = workOrderDao.getById(orderNo);
                    Log.d(TAG, "loadWorkOrder: retry result=" + (currentWorkOrder != null));
                } catch (Exception e) {
                    Log.e(TAG, "loadWorkOrder: retry query error", e);
                }
            }

            if (currentWorkOrder == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "工单不存在: " + orderNo, Toast.LENGTH_LONG).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            () -> finish(), 1500);
                });
                return;
            }

            Log.d(TAG, "loadWorkOrder: loaded work order: " + currentWorkOrder.getTitle());

            // 加载客户信息
            if (currentWorkOrder.getCustomerId() != null && !currentWorkOrder.getCustomerId().isEmpty()) {
                try {
                    currentCustomer = customerDao.getById(currentWorkOrder.getCustomerId());
                } catch (Exception e) {
                    Log.e(TAG, "loadWorkOrder: customer query error", e);
                }
            }

            // 加载维修记录
            List<RepairRecordEntity> repairRecords = null;
            try {
                repairRecords = repairRecordDao.getByWorkOrderId(orderNo);
            } catch (Exception e) {
                Log.e(TAG, "loadWorkOrder: repair records query error", e);
            }

            List<RepairRecordEntity> finalRepairRecords = repairRecords;
            runOnUiThread(() -> {
                bindWorkOrder(currentWorkOrder);
                bindCustomerInfo(currentCustomer);
                bindRepairRecords(finalRepairRecords);
                updateTimeline(currentWorkOrder);
                updateActionButton();
                triggerAiServicesIfNeeded(currentWorkOrder);
            });
        });
    }

    // ==================== 数据绑定 ====================

    private void bindWorkOrder(WorkOrderEntity wo) {
        tvOrderNo.setText(wo.getOrderNo());

        WorkOrderStatus status = WorkOrderStatus.fromString(wo.getStatus());
        tvStatus.setText(status.getDisplayName());
        applyStatusStyle(status);

        tvTitle.setText(wo.getTitle() != null ? wo.getTitle() : "");
        tvWorkType.setText(wo.getWorkType() != null ? wo.getWorkType() : "");
        tvDescription.setText(wo.getDescription() != null ? wo.getDescription() : "暂无描述");

        // 地址
        tvAddress.setText(wo.getAddress() != null ? wo.getAddress() : "地址待确认");

        // 预约时间
        if (wo.getAppointmentTime() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            tvAppointmentTime.setText("预约：" + sdf.format(new Date(wo.getAppointmentTime())));
        } else {
            tvAppointmentTime.setText("预约：时间待定");
        }

        // 距离（如果坐标有效，计算直线距离估算）
        if (wo.getLatitude() != 0 && wo.getLongitude() != 0) {
            tvDistance.setText("距离：计算中...");
            // 暂时显示有坐标，实际距离需要定位服务
            tvDistance.setText("距离：需导航查看");
        } else {
            tvDistance.setText("");
        }
    }

    private void bindCustomerInfo(CustomerEntity customer) {
        if (customer != null) {
            // 联系人头像：取姓名首字
            String name = customer.getName();
            if (name != null && !name.isEmpty()) {
                tvContactAvatar.setText(name.substring(0, 1));
            } else {
                tvContactAvatar.setText("客");
            }
            tvContactName.setText(name != null ? name : "未知客户");
            // 电话脱敏
            String phone = customer.getPhone();
            if (phone != null && phone.length() >= 7) {
                tvContactPhone.setText(phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4));
            } else {
                tvContactPhone.setText(phone != null ? phone : "");
            }
        } else {
            // 没有客户记录，从工单联系人信息兜底
            String contactName = currentWorkOrder.getContactName();
            if (contactName != null && !contactName.isEmpty()) {
                tvContactAvatar.setText(contactName.substring(0, 1));
                tvContactName.setText(contactName);
            } else {
                tvContactAvatar.setText("客");
                tvContactName.setText("未知客户");
            }
            String phone = currentWorkOrder.getContactPhone();
            if (phone != null && phone.length() >= 7) {
                tvContactPhone.setText(phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4));
            } else {
                tvContactPhone.setText(phone != null ? phone : "");
            }
        }

        // 设备信息卡片（使用客户数据或工单类型推算）
        // 当前数据模型没有独立设备字段，根据工单类型生成模拟设备信息
        String workType = currentWorkOrder.getWorkType();
        if (workType != null && !workType.isEmpty()) {
            cardDeviceInfo.setVisibility(View.VISIBLE);
            inferDeviceInfo(workType);
        }

        // 联系客户按钮
        btnContact.setOnClickListener(v -> {
            String phone = currentWorkOrder.getContactPhone();
            if (phone != null && !phone.isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                startActivity(callIntent);
            } else {
                Toast.makeText(this, "客户电话为空", Toast.LENGTH_SHORT).show();
            }
        });

        // 导航按钮 — 使用百度地图SDK页面内导航
        btnNavigate.setOnClickListener(v -> {
            if (currentWorkOrder != null) {
                String address = currentWorkOrder.getAddress();
                if (address == null || address.isEmpty()) {
                    Toast.makeText(this, "地址为空，无法导航", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Intent navIntent = new Intent(WorkOrderDetailActivity.this, BaiduNavigationActivity.class);
                    navIntent.putExtra("address", address);
                    navIntent.putExtra("title", currentWorkOrder.getTitle() != null ? currentWorkOrder.getTitle() : "");
                    navIntent.putExtra("schedule_id", currentWorkOrder.getScheduleId() != null ? currentWorkOrder.getScheduleId() : "");
                    if (currentWorkOrder.getLatitude() != 0 && currentWorkOrder.getLongitude() != 0) {
                        navIntent.putExtra("latitude", currentWorkOrder.getLatitude());
                        navIntent.putExtra("longitude", currentWorkOrder.getLongitude());
                    }
                    startActivity(navIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "启动导航失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "无法获取工单信息", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 根据工单类型推算设备信息（模拟数据，后续可对接真实设备库）
     */
    private void inferDeviceInfo(String workType) {
        switch (workType) {
            case "维修服务":
                tvDeviceBrand.setText("格力");
                tvDeviceModel.setText("KFR-35GW");
                tvDeviceAge.setText("3年");
                break;
            case "安装服务":
                tvDeviceBrand.setText("美的");
                tvDeviceModel.setText("KFR-72LW");
                tvDeviceAge.setText("新装");
                break;
            case "养护服务":
                tvDeviceBrand.setText("海尔");
                tvDeviceModel.setText("KFR-26GW");
                tvDeviceAge.setText("5年");
                break;
            case "清洗服务":
                tvDeviceBrand.setText("志高");
                tvDeviceModel.setText("KFR-51LW");
                tvDeviceAge.setText("2年");
                break;
            default:
                tvDeviceBrand.setText("—");
                tvDeviceModel.setText("—");
                tvDeviceAge.setText("—");
                break;
        }
    }

    private void bindRepairRecords(List<RepairRecordEntity> records) {
        layoutRepairRecords.removeAllViews();

        if (records == null || records.isEmpty()) {
            cardRepairHistory.setVisibility(View.VISIBLE);
            tvNoRepairHistory.setVisibility(View.VISIBLE);
            tvRepairCount.setText("共0条");
            return;
        }

        cardRepairHistory.setVisibility(View.VISIBLE);
        tvNoRepairHistory.setVisibility(View.GONE);
        tvRepairCount.setText("共" + records.size() + "条");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (RepairRecordEntity record : records) {
            View itemView = inflater.inflate(R.layout.item_repair_record, layoutRepairRecords, false);

            TextView tvDate = itemView.findViewById(R.id.tvRepairDate);
            TextView tvDesc = itemView.findViewById(R.id.tvRepairDesc);
            TextView tvResult = itemView.findViewById(R.id.tvRepairResult);

            if (record.getCreatedAt() > 0) {
                tvDate.setText(sdf.format(new Date(record.getCreatedAt())));
            } else {
                tvDate.setText("日期未知");
            }

            tvDesc.setText(record.getDescription() != null ? record.getDescription() : "维修记录");
            tvResult.setText(record.getResult() != null ? record.getResult() : "");

            layoutRepairRecords.addView(itemView);
        }
    }

    // ==================== 时间线 ====================

    /**
     * 根据工单状态更新进度时间线
     * 阶段：接单 → 出发 → 到场 → 完成
     */
    private void updateTimeline(WorkOrderEntity wo) {
        WorkOrderStatus status = WorkOrderStatus.fromString(wo.getStatus());

        int completedDots; // 已完成的阶段数（1-4）
        switch (status) {
            case PENDING:
                completedDots = 0;
                break;
            case ACCEPTED:
                completedDots = 1;
                break;
            case DEPARTED:
                completedDots = 2;
                break;
            case ARRIVED:
            case REPAIRING:
            case VERIFYING:
                completedDots = 3;
                break;
            case COMPLETED:
                completedDots = 4;
                break;
            case EXCEPTION:
                completedDots = 0; // 异常状态时间线特殊处理
                break;
            default:
                completedDots = 0;
                break;
        }

        int activeColor = getColor(R.color.primary);
        int inactiveColor = getColor(R.color.gray_300);
        int inactiveLineColor = getColor(R.color.gray_200);

        // 圆点
        timelineDot1.getBackground().setTint(completedDots >= 1 ? activeColor : inactiveColor);
        timelineDot2.getBackground().setTint(completedDots >= 2 ? activeColor : inactiveColor);
        timelineDot3.getBackground().setTint(completedDots >= 3 ? activeColor : inactiveColor);
        timelineDot4.getBackground().setTint(completedDots >= 4 ? activeColor : inactiveColor);

        // 连线
        timelineLine1.setBackgroundColor(completedDots >= 2 ? activeColor : inactiveLineColor);
        timelineLine2.setBackgroundColor(completedDots >= 3 ? activeColor : inactiveLineColor);
        timelineLine3.setBackgroundColor(completedDots >= 4 ? activeColor : inactiveLineColor);

        // 异常状态用红色
        if (status == WorkOrderStatus.EXCEPTION) {
            int exceptionColor = getColor(R.color.workorder_exception);
            timelineDot1.getBackground().setTint(exceptionColor);
        }
    }

    // ==================== 状态样式 ====================

    private void applyStatusStyle(WorkOrderStatus status) {
        int bgColorRes;
        int textColorRes;

        switch (status) {
            case PENDING:
                bgColorRes = R.color.workorder_tag_bg;
                textColorRes = R.color.workorder_pending;
                break;
            case ACCEPTED:
            case DEPARTED:
            case ARRIVED:
            case REPAIRING:
            case VERIFYING:
                bgColorRes = R.color.workorder_active_tag_bg;
                textColorRes = R.color.workorder_active;
                break;
            case COMPLETED:
                bgColorRes = R.color.workorder_completed_tag_bg;
                textColorRes = R.color.workorder_completed;
                break;
            case EXCEPTION:
                bgColorRes = R.color.workorder_exception_tag_bg;
                textColorRes = R.color.workorder_exception;
                break;
            default:
                bgColorRes = R.color.workorder_tag_bg;
                textColorRes = R.color.workorder_pending;
                break;
        }

        tvStatus.getBackground().setTint(getColor(bgColorRes));
        tvStatus.setTextColor(getColor(textColorRes));
    }

    // ==================== 操作按钮 ====================

    private void updateActionButton() {
        if (currentWorkOrder == null) return;

        WorkOrderStatus status = WorkOrderStatus.fromString(currentWorkOrder.getStatus());

        switch (status) {
            case PENDING:
                btnAction.setText(R.string.workorder_accept);
                btnAction.setOnClickListener(v -> transitionStatus(WorkOrderStatus.ACCEPTED));
                btnAction.setVisibility(View.VISIBLE);
                break;
            case ACCEPTED:
                btnAction.setText(R.string.workorder_depart);
                btnAction.setOnClickListener(v -> transitionStatus(WorkOrderStatus.DEPARTED));
                btnAction.setVisibility(View.VISIBLE);
                break;
            case DEPARTED:
                btnAction.setText(R.string.workorder_arrive);
                btnAction.setOnClickListener(v -> transitionStatus(WorkOrderStatus.ARRIVED));
                btnAction.setVisibility(View.VISIBLE);
                break;
            case ARRIVED:
                btnAction.setText(R.string.workorder_start_repair);
                btnAction.setOnClickListener(v -> transitionStatus(WorkOrderStatus.REPAIRING));
                btnAction.setVisibility(View.VISIBLE);
                break;
            case REPAIRING:
                btnAction.setText(R.string.repair_enter_repair);
                btnAction.setOnClickListener(v -> {
                    Intent intent = new Intent(this, com.gongyoutong.app.ui.repair.RepairActivity.class);
                    intent.putExtra(Config.EXTRA_WORKORDER_ID, currentWorkOrder.getOrderNo());
                    startActivityForResult(intent, REQUEST_CODE_REPAIR);
                });
                btnAction.setVisibility(View.VISIBLE);
                break;
            case VERIFYING:
                btnAction.setText(R.string.workorder_complete);
                btnAction.setOnClickListener(v -> transitionStatus(WorkOrderStatus.COMPLETED));
                btnAction.setVisibility(View.VISIBLE);
                break;
            case EXCEPTION:
                btnAction.setText(R.string.workorder_start_repair);
                btnAction.setOnClickListener(v -> transitionStatus(WorkOrderStatus.REPAIRING));
                btnAction.setVisibility(View.VISIBLE);
                break;
            case COMPLETED:
                btnAction.setVisibility(View.GONE);
                btnReportException.setVisibility(View.GONE);
                break;
        }

        // 报告异常按钮（除已完成和异常外都显示）
        if (status != WorkOrderStatus.COMPLETED && status != WorkOrderStatus.EXCEPTION) {
            btnReportException.setVisibility(View.VISIBLE);
            btnReportException.setOnClickListener(v -> transitionStatus(WorkOrderStatus.EXCEPTION));
        } else {
            btnReportException.setVisibility(View.GONE);
        }
    }

    private void transitionStatus(WorkOrderStatus targetStatus) {
        if (currentWorkOrder == null) return;

        WorkOrderStatus currentStatus = WorkOrderStatus.fromString(currentWorkOrder.getStatus());
        if (!currentStatus.canTransitionTo(targetStatus)) {
            Toast.makeText(this, "状态流转不合法", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            long now = System.currentTimeMillis();
            workOrderDao.updateStatus(currentWorkOrder.getOrderNo(), targetStatus.name(), now);
            currentWorkOrder.setStatus(targetStatus.name());
            currentWorkOrder.setUpdatedAt(now);

            // 接单时自动创建日程
            if (targetStatus == WorkOrderStatus.ACCEPTED) {
                createScheduleFromWorkOrder(currentWorkOrder);
            }

            // 同步日程状态
            syncScheduleStatus(targetStatus);

            runOnUiThread(() -> {
                WorkOrderStatus newStatus = WorkOrderStatus.fromString(currentWorkOrder.getStatus());
                tvStatus.setText(newStatus.getDisplayName());
                applyStatusStyle(newStatus);
                updateTimeline(currentWorkOrder);
                updateActionButton();
                // Toast.makeText(this, R.string.workorder_status_updated, Toast.LENGTH_SHORT).show();
            });
        });
    }

    /**
     * 工单状态变更后同步日程状态
     * 映射：ACCEPTED→待出发, DEPARTED→进行中, ARRIVED/REPAIRING/VERIFYING→进行中,
     *       COMPLETED→已完成, EXCEPTION→进行中
     */
    private void syncScheduleStatus(WorkOrderStatus workOrderStatus) {
        String scheduleId = currentWorkOrder.getScheduleId();
        if (scheduleId == null || scheduleId.isEmpty()) {
            Log.d(TAG, "syncScheduleStatus: 无关联日程，跳过");
            return;
        }

        String scheduleStatus = mapWorkOrderToScheduleStatus(workOrderStatus);
        if (scheduleStatus == null) {
            Log.d(TAG, "syncScheduleStatus: 工单状态 " + workOrderStatus + " 无需同步日程");
            return;
        }

        try {
            scheduleDao.updateStatus(scheduleId, scheduleStatus, System.currentTimeMillis());
            Log.i(TAG, "syncScheduleStatus: 工单 " + workOrderStatus + " → 日程 " + scheduleStatus);
        } catch (Exception e) {
            Log.e(TAG, "syncScheduleStatus: 同步日程状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 工单状态 → 日程状态 映射
     */
    private String mapWorkOrderToScheduleStatus(WorkOrderStatus woStatus) {
        switch (woStatus) {
            case ACCEPTED:
                return "待出发";
            case DEPARTED:
            case ARRIVED:
            case REPAIRING:
            case VERIFYING:
            case EXCEPTION:
                return "进行中";
            case COMPLETED:
                return "已完成";
            case PENDING:
            default:
                return null; // PENDING 不会有日程，无需同步
        }
    }

    /**
     * 接单时从工单自动创建日程
     */
    private void createScheduleFromWorkOrder(WorkOrderEntity wo) {
        try {
            // 检查是否已有关联日程
            if (wo.getScheduleId() != null && !wo.getScheduleId().isEmpty()) {
                Log.d(TAG, "工单已有关联日程: " + wo.getScheduleId());
                return;
            }

            ScheduleEntity schedule = new ScheduleEntity();
            schedule.setId("sch_" + wo.getOrderNo());
            schedule.setTitle(wo.getTitle() != null ? wo.getTitle() : "工单任务");
            schedule.setAddress(wo.getAddress());
            schedule.setWorkType(wo.getWorkType());
            schedule.setTime(wo.getAppointmentTime() > 0 ? wo.getAppointmentTime() : System.currentTimeMillis());
            schedule.setStatus("待出发");
            schedule.setCreatedAt(System.currentTimeMillis());
            schedule.setUpdatedAt(System.currentTimeMillis());
            schedule.setLatitude(wo.getLatitude());
            schedule.setLongitude(wo.getLongitude());
            schedule.setWorkOrderNo(wo.getOrderNo());
            schedule.setContactName(wo.getContactName());
            schedule.setContactPhone(wo.getContactPhone());
            schedule.setDescription(wo.getDescription());

            scheduleDao.insert(schedule);

            // 回写 scheduleId 到工单
            workOrderDao.updateScheduleId(wo.getOrderNo(), schedule.getId(), System.currentTimeMillis());
            currentWorkOrder.setScheduleId(schedule.getId());

            Log.i(TAG, "接单自动创建日程成功: " + schedule.getId());
        } catch (Exception e) {
            Log.e(TAG, "创建日程失败: " + e.getMessage(), e);
        }
    }

    // ==================== AI 服务 ====================

    private void triggerAiServicesIfNeeded(WorkOrderEntity wo) {
        boolean needPrediction = wo.getAiPrediction() == null || wo.getAiPrediction().isEmpty();
        boolean needTools = wo.getAiTools() == null || wo.getAiTools().isEmpty();

        if (needPrediction) {
            layoutAiPrediction.setVisibility(View.VISIBLE);
            pbAiPrediction.setVisibility(View.VISIBLE);
            tvAiPrediction.setText(R.string.workorder_ai_analyzing);

            aiService.predictFault(wo.getTitle(), wo.getWorkType(), wo.getDescription(),
                    new WorkOrderAiService.FaultPredictionCallback() {
                        @Override
                        public void onSuccess(String result) {
                            tvAiPrediction.setText(result);
                            pbAiPrediction.setVisibility(View.GONE);
                            executor.execute(() -> workOrderDao.updateAiPrediction(
                                    wo.getOrderNo(), result, System.currentTimeMillis()));

                            // 预判完成后自动触发风险分析
                            analyzeRisk(wo, result);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            tvAiPrediction.setText("AI 预判暂不可用");
                            pbAiPrediction.setVisibility(View.GONE);
                        }
                    });
        } else {
            layoutAiPrediction.setVisibility(View.VISIBLE);
            tvAiPrediction.setText(wo.getAiPrediction());
            pbAiPrediction.setVisibility(View.GONE);

            // 用已有的预判结果分析风险
            analyzeRisk(wo, wo.getAiPrediction());
        }

        if (needTools) {
            layoutAiTools.setVisibility(View.VISIBLE);
            pbAiTools.setVisibility(View.VISIBLE);
            tvAiTools.setText(R.string.workorder_ai_tools_loading);

            String prediction = wo.getAiPrediction();
            if (prediction == null || prediction.isEmpty()) {
                prediction = wo.getWorkType() != null ? wo.getWorkType() : "";
            }

            aiService.recommendTools(wo.getWorkType(), prediction,
                    new WorkOrderAiService.ToolRecommendationCallback() {
                        @Override
                        public void onSuccess(String result) {
                            tvAiTools.setText(result);
                            pbAiTools.setVisibility(View.GONE);
                            executor.execute(() -> workOrderDao.updateAiTools(
                                    wo.getOrderNo(), result, System.currentTimeMillis()));
                        }

                        @Override
                        public void onError(String errorMessage) {
                            tvAiTools.setText("AI 工具推荐暂不可用");
                            pbAiTools.setVisibility(View.GONE);
                        }
                    });
        } else {
            layoutAiTools.setVisibility(View.VISIBLE);
            tvAiTools.setText(wo.getAiTools());
            pbAiTools.setVisibility(View.GONE);
        }
    }

    /**
     * 根据故障预判结果 + 设备信息分析风险等级
     */
    private void analyzeRisk(WorkOrderEntity wo, String prediction) {
        cardRiskWarning.setVisibility(View.VISIBLE);

        // 简单的风险判断逻辑（基于关键词）
        String predictionLower = prediction != null ? prediction.toLowerCase(Locale.CHINA) : "";
        String riskLevel;
        String riskContent;
        int riskBgColor, riskTextColor, riskTagBgColor;

        if (containsAny(predictionLower, "困难", "老化", "严重", "更换", "报废", "漏电", "起火")) {
            riskLevel = "高风险";
            riskContent = "根据AI故障分析，该维修任务存在较高风险。建议携带充足备件，注意安全防护，必要时请求协助。";
            riskBgColor = R.color.risk_high_bg;
            riskTextColor = R.color.risk_high;
            riskTagBgColor = R.color.risk_high_bg;
        } else if (containsAny(predictionLower, "中等", "注意", "可能", "检查", "建议")) {
            riskLevel = "中风险";
            riskContent = "根据AI故障分析，该维修任务存在一定风险。建议提前确认设备状态，携带常用工具和备件。";
            riskBgColor = R.color.risk_medium_bg;
            riskTextColor = R.color.risk_medium;
            riskTagBgColor = R.color.risk_medium_bg;
        } else {
            riskLevel = "低风险";
            riskContent = "根据AI故障分析，该维修任务风险较低。常规操作即可完成，注意标准安全流程。";
            riskBgColor = R.color.risk_low_bg;
            riskTextColor = R.color.risk_low;
            riskTagBgColor = R.color.risk_low_bg;
        }

        tvRiskLevel.setText(riskLevel);
        tvRiskLevel.setTextColor(getColor(riskTextColor));
        tvRiskLevel.getBackground().setTint(getColor(riskTagBgColor));
        tvRiskContent.setText(riskContent);

        // 更新卡片边框颜色
        cardRiskWarning.setStrokeColor(getColor(riskTextColor));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_REPAIR && resultCode == RESULT_OK) {
            // 维修界面返回，刷新工单状态
            if (currentWorkOrder != null) {
                loadWorkOrder(currentWorkOrder.getOrderNo());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
