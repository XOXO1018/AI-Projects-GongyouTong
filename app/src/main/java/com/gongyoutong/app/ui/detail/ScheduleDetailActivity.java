package com.gongyoutong.app.ui.detail;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.VivoAiService;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleDao;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.database.WorkOrderDao;
import com.gongyoutong.app.database.WorkOrderEntity;
import com.gongyoutong.app.ui.navigation.BaiduNavigationActivity;
import com.gongyoutong.app.workorder.WorkOrderStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日程详情页 — 与工单详情页样式一致
 * 页面结构：顶部（状态+时间线+客户信息+地址导航）→ 中间（任务描述+设备信息+AI提醒+风险提示）→ 底部操作
 */
public class ScheduleDetailActivity extends AppCompatActivity {

    private static final String TAG = "ScheduleDetail";

    // ==================== Views ====================

    // 顶部卡片
    private TextView tvScheduleSource, tvStatus, tvTitle, tvWorkType;

    // 时间线
    private View timelineDot1, timelineDot2, timelineDot3;
    private View timelineLine1, timelineLine2;

    // 客户信息
    private MaterialCardView cardCustomer;
    private TextView tvContactAvatar, tvContactName, tvContactPhone;
    private MaterialButton btnContact, btnAddCustomer;
    private View dividerCustomer;
    private LinearLayout layoutCustomerInfo;

    // 地址导航
    private TextView tvAddress, tvAppointmentTime, tvCountdown;
    private MaterialButton btnNavigate;

    // 任务描述
    private MaterialCardView cardDescription;
    private TextView tvDescription;

    // 设备信息
    private MaterialCardView cardDeviceInfo;
    private LinearLayout layoutDeviceInfo;
    private TextView tvDeviceBrand, tvDeviceModel, tvDeviceAge;
    private MaterialButton btnAddDevice;

    // AI 智能提醒
    private MaterialCardView layoutAiReminder;
    private TextView tvAiReminder, tvAiModel;
    private ProgressBar pbAiReminder;
    private MaterialButton btnRefreshAi, btnExpandReminder;
    private TextView tvToolsLabel;
    private ChipGroup chipGroupTools;

    // 风险提示
    private MaterialCardView cardRiskWarning;
    private TextView tvRiskLevel, tvRiskContent;

    // 底部操作
    private MaterialButton btnStartTask;

    // ==================== Data ====================

    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FMT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA));

    private AppDatabase database;
    private ScheduleDao scheduleDao;
    private WorkOrderDao workOrderDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String scheduleId;
    private ScheduleEntity currentSchedule;

    // AI 流式输出状态
    private boolean isAiExpanded = false;
    private String fullAiReminder = "";
    private boolean isAiStreaming = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            setContentView(R.layout.activity_schedule_detail);

            database = AppDatabase.getInstance(this);
            scheduleDao = database.scheduleDao();
            workOrderDao = database.workOrderDao();

            scheduleId = getIntent().getStringExtra("schedule_id");

            initViews();
            setupToolbar();
            setupClickListeners();
            loadScheduleData();

        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
            Toast.makeText(this, "页面加载失败", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        // 顶部卡片
        tvScheduleSource = findViewById(R.id.tvScheduleSource);
        tvStatus = findViewById(R.id.tvStatus);
        tvTitle = findViewById(R.id.tvTitle);
        tvWorkType = findViewById(R.id.tvWorkType);

        // 时间线
        timelineDot1 = findViewById(R.id.timelineDot1);
        timelineDot2 = findViewById(R.id.timelineDot2);
        timelineDot3 = findViewById(R.id.timelineDot3);
        timelineLine1 = findViewById(R.id.timelineLine1);
        timelineLine2 = findViewById(R.id.timelineLine2);

        // 客户信息
        cardCustomer = findViewById(R.id.cardCustomer);
        tvContactAvatar = findViewById(R.id.tvContactAvatar);
        tvContactName = findViewById(R.id.tvContactName);
        tvContactPhone = findViewById(R.id.tvContactPhone);
        btnContact = findViewById(R.id.btnContact);
        btnAddCustomer = findViewById(R.id.btnAddCustomer);
        dividerCustomer = findViewById(R.id.dividerCustomer);
        layoutCustomerInfo = findViewById(R.id.layoutCustomerInfo);

        // 地址导航
        tvAddress = findViewById(R.id.tvAddress);
        tvAppointmentTime = findViewById(R.id.tvAppointmentTime);
        tvCountdown = findViewById(R.id.tvCountdown);
        btnNavigate = findViewById(R.id.btnNavigate);

        // 任务描述
        cardDescription = findViewById(R.id.cardDescription);
        tvDescription = findViewById(R.id.tvDescription);

        // 设备信息
        cardDeviceInfo = findViewById(R.id.cardDeviceInfo);
        layoutDeviceInfo = findViewById(R.id.layoutDeviceInfo);
        tvDeviceBrand = findViewById(R.id.tvDeviceBrand);
        tvDeviceModel = findViewById(R.id.tvDeviceModel);
        tvDeviceAge = findViewById(R.id.tvDeviceAge);
        btnAddDevice = findViewById(R.id.btnAddDevice);

        // AI 智能提醒
        layoutAiReminder = findViewById(R.id.layoutAiReminder);
        tvAiReminder = findViewById(R.id.tvAiReminder);
        tvAiModel = findViewById(R.id.tvAiModel);
        pbAiReminder = findViewById(R.id.pbAiReminder);
        btnRefreshAi = findViewById(R.id.btnRefreshAi);
        btnExpandReminder = findViewById(R.id.btnExpandReminder);
        tvToolsLabel = findViewById(R.id.tvToolsLabel);
        chipGroupTools = findViewById(R.id.chipGroupTools);

        // 风险提示
        cardRiskWarning = findViewById(R.id.cardRiskWarning);
        tvRiskLevel = findViewById(R.id.tvRiskLevel);
        tvRiskContent = findViewById(R.id.tvRiskContent);

        // 底部操作
        btnStartTask = findViewById(R.id.btnStartTask);

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

    private void setupClickListeners() {
        // 导航按钮
        btnNavigate.setOnClickListener(v -> startNavigation());

        // 底部操作按钮：根据日程状态流转
        btnStartTask.setOnClickListener(v -> handleActionClick());

        // 联系客户
        btnContact.setOnClickListener(v -> {
            if (currentSchedule != null && currentSchedule.getContactPhone() != null
                    && !currentSchedule.getContactPhone().isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + currentSchedule.getContactPhone()));
                startActivity(callIntent);
            } else {
                Toast.makeText(this, "客户电话为空", Toast.LENGTH_SHORT).show();
            }
        });

        // 添加客户信息
        btnAddCustomer.setOnClickListener(v -> showCustomerInfoDialog());

        // 添加设备信息
        btnAddDevice.setOnClickListener(v -> showDeviceInfoDialog());

        // 重新生成 AI 提醒（流式）
        btnRefreshAi.setOnClickListener(v -> {
            if (currentSchedule != null && !isAiStreaming) {
                refreshAiReminderStream();
            }
        });

        // 展开/收起 AI 提醒
        btnExpandReminder.setOnClickListener(v -> {
            isAiExpanded = !isAiExpanded;
            if (isAiExpanded) {
                tvAiReminder.setMaxLines(Integer.MAX_VALUE);
                tvAiReminder.setEllipsize(null);
                btnExpandReminder.setText("收起");
            } else {
                tvAiReminder.setMaxLines(4);
                tvAiReminder.setEllipsize(android.text.TextUtils.TruncateAt.END);
                btnExpandReminder.setText("展开全文");
            }
        });
    }

    // ==================== 数据加载 ====================

    private void loadScheduleData() {
        executor.execute(() -> {
            currentSchedule = scheduleDao.getById(scheduleId);

            runOnUiThread(() -> {
                if (currentSchedule != null) {
                    bindSchedule(currentSchedule);
                } else {
                    Toast.makeText(this, "未找到日程信息", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    // ==================== 数据绑定 ====================

    private void bindSchedule(ScheduleEntity schedule) {
        // 来源标识
        if (schedule.getWorkOrderNo() != null && !schedule.getWorkOrderNo().isEmpty()) {
            tvScheduleSource.setText("工单 " + schedule.getWorkOrderNo());
        } else {
            tvScheduleSource.setText("日程 " + schedule.getId());
        }

        // 标题
        tvTitle.setText(schedule.getTitle() != null ? schedule.getTitle() : "");
        tvWorkType.setText(schedule.getWorkType() != null ? schedule.getWorkType() : "");

        // 状态
        updateStatusDisplay(schedule.getStatus());
        updateTimeline(schedule.getStatus());
        updateActionButton(schedule.getStatus());

        // 地址
        tvAddress.setText(schedule.getAddress() != null ? schedule.getAddress() : "地址待确认");

        // 预约时间
        if (schedule.getTime() > 0) {
            tvAppointmentTime.setText("预约：" + DATE_TIME_FMT.get().format(new Date(schedule.getTime())));
            tvCountdown.setText(getCountdownText(new Date(schedule.getTime())));
        } else {
            tvAppointmentTime.setText("预约：时间待定");
            tvCountdown.setText("");
        }

        // 客户信息
        bindCustomerInfo(schedule);

        // 任务描述
        bindDescription(schedule);

        // 设备信息
        bindDeviceInfo(schedule);

        // AI 提醒（流式）
        String aiReminder = schedule.getAiReminder();
        if (aiReminder != null && !aiReminder.isEmpty()) {
            fullAiReminder = aiReminder;
            tvAiReminder.setText(aiReminder);
            tvAiModel.setText("基于 蓝心大模型 生成");
            updateExpandButton();
            analyzeRisk(aiReminder);
        } else {
            generateAiReminderStream();
        }

        // 工具标签
        setupToolChips(schedule.getWorkType());
    }

    private void bindCustomerInfo(ScheduleEntity schedule) {
        String contactName = schedule.getContactName();
        String contactPhone = schedule.getContactPhone();

        boolean hasCustomer = contactName != null && !contactName.isEmpty();

        if (hasCustomer) {
            layoutCustomerInfo.setVisibility(View.VISIBLE);
            dividerCustomer.setVisibility(View.VISIBLE);
            btnAddCustomer.setVisibility(View.GONE);

            tvContactAvatar.setText(contactName.substring(0, 1));
            tvContactName.setText(contactName);

            if (contactPhone != null && contactPhone.length() >= 7) {
                tvContactPhone.setText(contactPhone.substring(0, 3) + "****" + contactPhone.substring(contactPhone.length() - 4));
            } else {
                tvContactPhone.setText(contactPhone != null ? contactPhone : "");
            }
        } else {
            layoutCustomerInfo.setVisibility(View.GONE);
            dividerCustomer.setVisibility(View.GONE);
            btnAddCustomer.setVisibility(View.VISIBLE);
        }
    }

    private void bindDescription(ScheduleEntity schedule) {
        String desc = schedule.getDescription();
        if (desc != null && !desc.isEmpty()) {
            cardDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(desc);
        } else {
            cardDescription.setVisibility(View.GONE);
        }
    }

    private void bindDeviceInfo(ScheduleEntity schedule) {
        String brand = schedule.getDeviceBrand();
        String model = schedule.getDeviceModel();
        String age = schedule.getDeviceAge();

        boolean hasDevice = (brand != null && !brand.isEmpty())
                || (model != null && !model.isEmpty())
                || (age != null && !age.isEmpty());

        if (hasDevice) {
            layoutDeviceInfo.setVisibility(View.VISIBLE);
            btnAddDevice.setVisibility(View.GONE);
            tvDeviceBrand.setText(brand != null ? brand : "—");
            tvDeviceModel.setText(model != null ? model : "—");
            tvDeviceAge.setText(age != null ? age : "—");
        } else {
            layoutDeviceInfo.setVisibility(View.GONE);
            btnAddDevice.setVisibility(View.VISIBLE);
        }
    }

    // ==================== 状态样式 ====================

    private void updateStatusDisplay(String status) {
        tvStatus.setText(status != null ? status : "待出发");

        int bgColorRes, textColorRes;
        switch (status != null ? status : "待出发") {
            case "待出发":
                bgColorRes = R.color.workorder_tag_bg;
                textColorRes = R.color.workorder_pending;
                break;
            case "进行中":
                bgColorRes = R.color.workorder_active_tag_bg;
                textColorRes = R.color.workorder_active;
                break;
            case "已完成":
                bgColorRes = R.color.workorder_completed_tag_bg;
                textColorRes = R.color.workorder_completed;
                break;
            case "已取消":
            case "已过期":
                bgColorRes = R.color.error_container;
                textColorRes = R.color.error;
                break;
            default:
                bgColorRes = R.color.workorder_tag_bg;
                textColorRes = R.color.workorder_pending;
                break;
        }

        tvStatus.getBackground().setTint(getColor(bgColorRes));
        tvStatus.setTextColor(getColor(textColorRes));
    }

    // ==================== 时间线 ====================

    private void updateTimeline(String status) {
        int completedDots;
        switch (status != null ? status : "待出发") {
            case "待出发":
            case "已过期":
                completedDots = 0;
                break;
            case "进行中":
                completedDots = 1;
                break;
            case "已完成":
                completedDots = 2;
                break;
            default:
                completedDots = 0;
                break;
        }

        int activeColor = getColor(R.color.primary);
        int inactiveColor = getColor(R.color.gray_300);
        int inactiveLineColor = getColor(R.color.gray_200);

        timelineDot1.getBackground().setTint(completedDots >= 1 ? activeColor : inactiveColor);
        timelineDot2.getBackground().setTint(completedDots >= 2 ? activeColor : inactiveColor);
        timelineDot3.getBackground().setTint(completedDots >= 3 ? activeColor : inactiveColor);

        timelineLine1.setBackgroundColor(completedDots >= 2 ? activeColor : inactiveLineColor);
        timelineLine2.setBackgroundColor(completedDots >= 3 ? activeColor : inactiveLineColor);
    }

    // ==================== 导航 ====================

    private void startNavigation() {
        if (currentSchedule != null) {
            String address = currentSchedule.getAddress();
            if (address == null || address.isEmpty()) {
                Toast.makeText(this, "日程地址为空，无法导航", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Intent intent = new Intent(ScheduleDetailActivity.this, BaiduNavigationActivity.class);
                intent.putExtra("address", address);
                intent.putExtra("title", currentSchedule.getTitle() != null ? currentSchedule.getTitle() : "");
                intent.putExtra("schedule_id", currentSchedule.getId());
                if (currentSchedule.getLatitude() != 0 && currentSchedule.getLongitude() != 0) {
                    intent.putExtra("latitude", currentSchedule.getLatitude());
                    intent.putExtra("longitude", currentSchedule.getLongitude());
                }
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "启动导航失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "无法获取日程信息", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== AI 智能提醒（流式输出）====================

    /**
     * 流式生成 AI 个性化提醒
     */
    private void generateAiReminderStream() {
        if (currentSchedule == null || isAiStreaming) return;

        isAiStreaming = true;
        pbAiReminder.setVisibility(View.VISIBLE);
        btnRefreshAi.setEnabled(false);
        tvAiReminder.setMaxLines(4);
        tvAiReminder.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvAiReminder.setText("AI 正在生成个性化提醒...");
        tvAiModel.setText("正在连接 蓝心大模型...");
        btnExpandReminder.setVisibility(View.GONE);

        VivoAiService aiService = new VivoAiService();
        String workType = currentSchedule.getWorkType() != null ? currentSchedule.getWorkType() : "";
        String title = currentSchedule.getTitle() != null ? currentSchedule.getTitle() : "";
        String address = currentSchedule.getAddress() != null ? currentSchedule.getAddress() : "";

        aiService.generateReminderStream(title, workType, address,
                new VivoAiService.StreamChatCallback() {
                    @Override
                    public void onStart() {
                        runOnUiThread(() -> tvAiModel.setText("蓝心大模型 生成中..."));
                    }

                    @Override
                    public void onDelta(String text, boolean isDone) {
                        runOnUiThread(() -> {
                            fullAiReminder = text;
                            tvAiReminder.setText(text);

                            if (isDone) {
                                isAiStreaming = false;
                                pbAiReminder.setVisibility(View.GONE);
                                btnRefreshAi.setEnabled(true);
                                tvAiModel.setText("基于 蓝心大模型 生成");
                                updateExpandButton();

                                // 保存到数据库
                                executor.execute(() -> {
                                    currentSchedule.setAiReminder(fullAiReminder);
                                    currentSchedule.setUpdatedAt(System.currentTimeMillis());
                                    scheduleDao.update(currentSchedule);
                                });

                                // 分析风险
                                analyzeRisk(fullAiReminder);
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            isAiStreaming = false;
                            pbAiReminder.setVisibility(View.GONE);
                            btnRefreshAi.setEnabled(true);
                            tvAiModel.setText("生成失败");

                            // fallback 到本地模板
                            String fallbackReminder = VivoAiService.generateAiReminderText(
                                    currentSchedule.getWorkType());
                            fullAiReminder = fallbackReminder;
                            tvAiReminder.setText(fallbackReminder);
                            tvAiModel.setText("基于本地规则生成");
                            updateExpandButton();
                        });
                    }
                });
    }

    /**
     * 重新生成 AI 提醒（流式）
     */
    private void refreshAiReminderStream() {
        fullAiReminder = "";
        generateAiReminderStream();
    }

    /**
     * 更新展开/收起按钮
     */
    private void updateExpandButton() {
        if (fullAiReminder.length() > 100 || fullAiReminder.split("\n").length > 4) {
            btnExpandReminder.setVisibility(View.VISIBLE);
            isAiExpanded = false;
            tvAiReminder.setMaxLines(4);
            tvAiReminder.setEllipsize(android.text.TextUtils.TruncateAt.END);
            btnExpandReminder.setText("展开全文");
        } else {
            btnExpandReminder.setVisibility(View.GONE);
        }
    }

    // ==================== 风险分析 ====================

    private void analyzeRisk(String prediction) {
        String text = prediction != null ? prediction.toLowerCase(Locale.CHINA) : "";
        String riskLevel, riskContent;
        int riskBgColor, riskTextColor;

        if (containsAny(text, "困难", "老化", "严重", "更换", "报废", "漏电", "起火")) {
            riskLevel = "高风险";
            riskContent = "根据AI分析，该任务存在较高风险。建议携带充足备件，注意安全防护，必要时请求协助。";
            riskBgColor = R.color.risk_high_bg;
            riskTextColor = R.color.risk_high;
        } else if (containsAny(text, "中等", "注意", "可能", "检查", "建议")) {
            riskLevel = "中风险";
            riskContent = "根据AI分析，该任务存在一定风险。建议提前确认设备状态，携带常用工具和备件。";
            riskBgColor = R.color.risk_medium_bg;
            riskTextColor = R.color.risk_medium;
        } else {
            riskLevel = "低风险";
            riskContent = "根据AI分析，该任务风险较低。常规操作即可完成，注意标准安全流程。";
            riskBgColor = R.color.risk_low_bg;
            riskTextColor = R.color.risk_low;
        }

        cardRiskWarning.setVisibility(View.VISIBLE);
        tvRiskLevel.setText(riskLevel);
        tvRiskLevel.setTextColor(getColor(riskTextColor));
        tvRiskLevel.getBackground().setTint(getColor(riskBgColor));
        tvRiskContent.setText(riskContent);
        cardRiskWarning.setStrokeColor(getColor(riskTextColor));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 工具标签 ====================

    private void setupToolChips(String workType) {
        chipGroupTools.removeAllViews();

        String[] tools = VivoAiService.getToolsForWorkType(workType);
        if (tools == null || tools.length == 0) {
            tvToolsLabel.setVisibility(View.GONE);
            return;
        }

        tvToolsLabel.setVisibility(View.VISIBLE);
        for (String tool : tools) {
            Chip chip = new Chip(this);
            chip.setText(tool);
            chip.setChipBackgroundColorResource(R.color.bg_card);
            chip.setChipStrokeColorResource(R.color.primary_light);
            chip.setChipStrokeWidth(1f);
            chip.setTextSize(13f);
            chip.setClickable(false);
            chip.setCheckable(false);
            chipGroupTools.addView(chip);
        }
    }

    // ==================== 工具方法 ====================

    private String getCountdownText(Date time) {
        if (time == null) return "";
        long diff = time.getTime() - System.currentTimeMillis();
        if (diff <= 0) return "已过期";
        long hours = diff / (60 * 60 * 1000);
        long mins = (diff % (60 * 60 * 1000)) / (60 * 1000);
        if (hours > 24) {
            long days = hours / 24;
            return days + "天后";
        } else if (hours > 0) {
            return hours + "小时" + (mins > 0 ? mins + "分钟" : "") + "后";
        } else {
            return mins + "分钟后";
        }
    }

    // ==================== 日程状态流转与工单同步 ====================

    /**
     * 根据日程状态更新底部操作按钮
     * 日程状态：待出发 → 进行中 → 已完成
     */
    private void updateActionButton(String status) {
        if (status == null) status = "待出发";

        switch (status) {
            case "待出发":
                btnStartTask.setText("出发");
                btnStartTask.setIcon(null);
                btnStartTask.setVisibility(View.VISIBLE);
                break;
            case "进行中":
                btnStartTask.setText("完成任务");
                btnStartTask.setIcon(null);
                btnStartTask.setVisibility(View.VISIBLE);
                break;
            case "已完成":
            case "已取消":
            case "已过期":
                btnStartTask.setVisibility(View.GONE);
                break;
            default:
                btnStartTask.setText("出发");
                btnStartTask.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * 处理底部操作按钮点击
     */
    private void handleActionClick() {
        if (currentSchedule == null) return;

        String status = currentSchedule.getStatus();
        if (status == null) status = "待出发";

        String nextStatus;
        switch (status) {
            case "待出发":
                nextStatus = "进行中";
                break;
            case "进行中":
                nextStatus = "已完成";
                break;
            default:
                return;
        }

        transitionScheduleStatus(nextStatus);
    }

    /**
     * 日程状态流转 + 同步工单状态
     */
    private void transitionScheduleStatus(String targetStatus) {
        if (currentSchedule == null) return;

        executor.execute(() -> {
            long now = System.currentTimeMillis();
            scheduleDao.updateStatus(currentSchedule.getId(), targetStatus, now);
            currentSchedule.setStatus(targetStatus);
            currentSchedule.setUpdatedAt(now);

            // 同步工单状态
            syncWorkOrderStatus(targetStatus);

            runOnUiThread(() -> {
                updateStatusDisplay(targetStatus);
                updateTimeline(targetStatus);
                updateActionButton(targetStatus);
                // Toast.makeText(this, "日程状态已更新为：" + targetStatus, Toast.LENGTH_SHORT).show();
            });
        });
    }

    /**
     * 日程状态变更后同步关联工单状态
     * 映射：待出发→ACCEPTED, 进行中→DEPARTED, 已完成→COMPLETED
     * 
     * 特殊处理：日程标记"已完成"时，工单直接完成（跳过中间状态），
     * 因为日程完成意味着维修业务实际已结束
     */
    private void syncWorkOrderStatus(String scheduleStatus) {
        String workOrderNo = currentSchedule.getWorkOrderNo();
        if (workOrderNo == null || workOrderNo.isEmpty()) {
            Log.d(TAG, "syncWorkOrderStatus: 无关联工单，跳过同步");
            return;
        }

        WorkOrderStatus targetWoStatus = mapScheduleToWorkOrderStatus(scheduleStatus);
        if (targetWoStatus == null) {
            Log.d(TAG, "syncWorkOrderStatus: 日程状态 " + scheduleStatus + " 无需同步工单");
            return;
        }

        try {
            WorkOrderEntity wo = workOrderDao.getById(workOrderNo);
            if (wo == null) {
                Log.w(TAG, "syncWorkOrderStatus: 关联工单不存在: " + workOrderNo);
                return;
            }

            WorkOrderStatus currentWoStatus = WorkOrderStatus.fromString(wo.getStatus());

            // 日程完成 → 工单直接完成（业务层面维修已结束）
            if (targetWoStatus == WorkOrderStatus.COMPLETED) {
                workOrderDao.updateStatus(workOrderNo, WorkOrderStatus.COMPLETED.name(), System.currentTimeMillis());
                Log.i(TAG, "syncWorkOrderStatus: 日程已完成 → 工单直接完成");
                return;
            }

            // 常规流转：检查是否可以合法流转
            if (currentWoStatus.canTransitionTo(targetWoStatus)) {
                workOrderDao.updateStatus(workOrderNo, targetWoStatus.name(), System.currentTimeMillis());
                Log.i(TAG, "syncWorkOrderStatus: 日程 " + scheduleStatus + " → 工单 " + targetWoStatus);
            } else if (isForwardDirection(currentWoStatus, targetWoStatus)) {
                // 当前工单状态已在目标之后（如工单已在REPAIRING，日程推DEPARTED），
                // 说明工单进度已超前，无需回退，只更新日志
                Log.i(TAG, "syncWorkOrderStatus: 工单已超前(" + currentWoStatus + ")，无需同步到 " + targetWoStatus);
            } else {
                Log.w(TAG, "syncWorkOrderStatus: 无法从 " + currentWoStatus + " 流转到 " + targetWoStatus);
            }
        } catch (Exception e) {
            Log.e(TAG, "syncWorkOrderStatus: 同步工单状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断目标状态是否在当前状态的后方（即工单进度已超前）
     */
    private boolean isForwardDirection(WorkOrderStatus current, WorkOrderStatus target) {
        WorkOrderStatus[] flow = {
                WorkOrderStatus.PENDING,
                WorkOrderStatus.ACCEPTED,
                WorkOrderStatus.DEPARTED,
                WorkOrderStatus.ARRIVED,
                WorkOrderStatus.REPAIRING,
                WorkOrderStatus.VERIFYING,
                WorkOrderStatus.COMPLETED
        };
        int currentIdx = -1, targetIdx = -1;
        for (int i = 0; i < flow.length; i++) {
            if (flow[i] == current) currentIdx = i;
            if (flow[i] == target) targetIdx = i;
        }
        return currentIdx > targetIdx; // 当前已超过目标位置
    }

    /**
     * 日程状态 → 工单状态 映射
     */
    private WorkOrderStatus mapScheduleToWorkOrderStatus(String scheduleStatus) {
        if (scheduleStatus == null) return null;
        switch (scheduleStatus) {
            case "待出发":
                return WorkOrderStatus.ACCEPTED;
            case "进行中":
                return WorkOrderStatus.DEPARTED;
            case "已完成":
                return WorkOrderStatus.COMPLETED;
            case "已取消":
                return null; // 日程取消不自动取消工单
            default:
                return null;
        }
    }

    /**
     * 显示添加客户信息对话框
     */
    private void showCustomerInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加客户信息");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText etName = new EditText(this);
        etName.setHint("客户姓名");
        etName.setText(currentSchedule != null ? currentSchedule.getContactName() : "");
        layout.addView(etName);

        EditText etPhone = new EditText(this);
        etPhone.setHint("联系电话");
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        etPhone.setText(currentSchedule != null ? currentSchedule.getContactPhone() : "");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        etPhone.setLayoutParams(lp);
        layout.addView(etPhone);

        builder.setView(layout);
        builder.setPositiveButton("保存", (d, w) -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            if (TextUtils.isEmpty(name) && TextUtils.isEmpty(phone)) {
                Toast.makeText(this, "请至少填写姓名或电话", Toast.LENGTH_SHORT).show();
                return;
            }
            saveCustomerInfo(name, phone);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveCustomerInfo(String name, String phone) {
        if (currentSchedule == null) return;
        executor.execute(() -> {
            currentSchedule.setContactName(name);
            currentSchedule.setContactPhone(phone);
            currentSchedule.setUpdatedAt(System.currentTimeMillis());
            scheduleDao.update(currentSchedule);
            runOnUiThread(this::refreshCustomerInfo);
        });
    }

    private void refreshCustomerInfo() {
        if (currentSchedule == null) return;
        String name = currentSchedule.getContactName();
        String phone = currentSchedule.getContactPhone();
        boolean hasCustomer = name != null && !name.isEmpty();
        if (hasCustomer) {
            layoutCustomerInfo.setVisibility(View.VISIBLE);
            dividerCustomer.setVisibility(View.VISIBLE);
            btnAddCustomer.setVisibility(View.GONE);
            tvContactAvatar.setText(name.substring(0, 1));
            tvContactName.setText(name);
            if (phone != null && phone.length() >= 7) {
                tvContactPhone.setText(phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4));
            } else {
                tvContactPhone.setText(phone != null ? phone : "");
            }
        }
    }

    /**
     * 显示添加设备信息对话框
     */
    private void showDeviceInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加设备信息");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText etBrand = new EditText(this);
        etBrand.setHint("设备品牌（如：格力）");
        etBrand.setText(currentSchedule != null ? currentSchedule.getDeviceBrand() : "");
        layout.addView(etBrand);

        EditText etModel = new EditText(this);
        etModel.setHint("设备型号（如：KFR-35GW）");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.topMargin = 16;
        etModel.setLayoutParams(lp1);
        layout.addView(etModel);

        EditText etAge = new EditText(this);
        etAge.setHint("使用年限（如：3年）");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = 16;
        etAge.setLayoutParams(lp2);
        layout.addView(etAge);

        builder.setView(layout);
        builder.setPositiveButton("保存", (d, w) -> {
            String brand = etBrand.getText().toString().trim();
            String model = etModel.getText().toString().trim();
            String age = etAge.getText().toString().trim();
            if (TextUtils.isEmpty(brand) && TextUtils.isEmpty(model) && TextUtils.isEmpty(age)) {
                Toast.makeText(this, "请至少填写一项设备信息", Toast.LENGTH_SHORT).show();
                return;
            }
            saveDeviceInfo(brand, model, age);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveDeviceInfo(String brand, String model, String age) {
        if (currentSchedule == null) return;
        executor.execute(() -> {
            currentSchedule.setDeviceBrand(brand);
            currentSchedule.setDeviceModel(model);
            currentSchedule.setDeviceAge(age);
            currentSchedule.setUpdatedAt(System.currentTimeMillis());
            scheduleDao.update(currentSchedule);
            runOnUiThread(this::refreshDeviceInfo);
        });
    }

    private void refreshDeviceInfo() {
        if (currentSchedule == null) return;
        String brand = currentSchedule.getDeviceBrand();
        String model = currentSchedule.getDeviceModel();
        String age = currentSchedule.getDeviceAge();
        boolean hasDevice = (brand != null && !brand.isEmpty())
                || (model != null && !model.isEmpty())
                || (age != null && !age.isEmpty());
        if (hasDevice) {
            layoutDeviceInfo.setVisibility(View.VISIBLE);
            btnAddDevice.setVisibility(View.GONE);
            tvDeviceBrand.setText(brand != null && !brand.isEmpty() ? brand : "—");
            tvDeviceModel.setText(model != null && !model.isEmpty() ? model : "—");
            tvDeviceAge.setText(age != null && !age.isEmpty() ? age : "—");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
