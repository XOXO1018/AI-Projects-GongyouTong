# 工友通 Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现工友通设计文档中的核心功能模块，包括工作台重构、工单增强、日程日历、知识案例库、客户管理、收入分析

**Architecture:** 基于现有 Android 项目结构，采用 Activity + Room Database + vivo AI 服务架构，新增 Repository 层统一数据访问，逐步实现设计文档中的五大页面

**Tech Stack:** Java, Room Database, Material Design 3, Baidu Maps SDK, vivo Blue Heart AI, CameraX, OkHttp

---

## Phase 1A: 工作台页面重构

### Task 1: 创建工作台数据模型和 Repository

**Files:**
- Create: `app/src/main/java/com/gongyoutong/app/data/WorkspaceRepository.java`
- Modify: `app/src/main/java/com/gongyoutong/app/data/Schedule.java` (add priority field)

- [ ] **Step 1: 添加日程优先级字段**

```java
// 在 Schedule.java 中添加
private int priority; // 0=普通, 1=重要, 2=紧急

public int getPriority() { return priority; }
public void setPriority(int priority) { this.priority = priority; }
```

- [ ] **Step 2: 创建 WorkspaceRepository**

```java
package com.gongyoutong.app.data;

import android.content.Context;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.database.WorkOrderEntity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkspaceRepository {
    private static volatile WorkspaceRepository INSTANCE;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private WorkspaceRepository(Context context) {
        db = AppDatabase.getInstance(context);
    }

    public static WorkspaceRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WorkspaceRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WorkspaceRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public interface WorkspaceCallback {
        void onResult(WorkspaceData data);
    }

    public void getWorkspaceData(WorkspaceCallback callback) {
        executor.execute(() -> {
            WorkspaceData data = new WorkspaceData();
            data.todaySchedules = getTodaySchedules();
            data.pendingOrders = getPendingOrders();
            data.todayIncome = calculateTodayIncome();
            data.urgentTasks = getUrgentTasks();
            callback.onResult(data);
        });
    }

    private List<Schedule> getTodaySchedules() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        long startOfDay = today.getTimeInMillis();

        today.add(Calendar.DAY_OF_MONTH, 1);
        long endOfDay = today.getTimeInMillis();

        List<ScheduleEntity> entities = db.scheduleDao().getSchedulesBetween(startOfDay, endOfDay);
        List<Schedule> result = new ArrayList<>();
        for (ScheduleEntity e : entities) {
            Schedule s = new Schedule();
            s.setId(e.getId());
            s.setTitle(e.getTitle());
            s.setAddress(e.getAddress());
            s.setTime(new java.util.Date(e.getTime()));
            s.setStatus(e.getStatus());
            result.add(s);
        }
        return result;
    }

    private int getPendingOrders() {
        return db.workOrderDao().getCountByStatus("待接单");
    }

    private double calculateTodayIncome() {
        return db.workOrderDao().getTodayIncome(System.currentTimeMillis());
    }

    private List<Schedule> getUrgentTasks() {
        List<ScheduleEntity> entities = db.scheduleDao().getUrgentSchedules();
        List<Schedule> result = new ArrayList<>();
        for (ScheduleEntity e : entities) {
            Schedule s = new Schedule();
            s.setId(e.getId());
            s.setTitle(e.getTitle());
            s.setAddress(e.getAddress());
            s.setTime(new java.util.Date(e.getTime()));
            s.setStatus(e.getStatus());
            result.add(s);
        }
        return result;
    }

    public static class WorkspaceData {
        public List<Schedule> todaySchedules = new ArrayList<>();
        public int pendingOrders = 0;
        public double todayIncome = 0;
        public List<Schedule> urgentTasks = new ArrayList<>();
    }
}
```

- [ ] **Step 3: 添加必要的 DAO 方法**

```java
// ScheduleDao.java 添加
@Query("SELECT * FROM schedules WHERE time BETWEEN :start AND :end ORDER BY time ASC")
List<ScheduleEntity> getSchedulesBetween(long start, long end);

@Query("SELECT * FROM schedules WHERE status = '进行中' OR (time < :now AND status = '待出发')")
List<ScheduleEntity> getUrgentSchedules();

// WorkOrderDao.java 添加
@Query("SELECT COUNT(*) FROM work_orders WHERE status = :status")
int getCountByStatus(String status);

@Query("SELECT COALESCE(SUM(amount), 0) FROM work_orders WHERE completedAt > :todayStart AND status = '已完成'")
double getTodayIncome(long todayStart);
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/data/WorkspaceRepository.java
git add app/src/main/java/com/gongyoutong/app/data/Schedule.java
git add app/src/main/java/com/gongyoutong/app/database/ScheduleDao.java
git add app/src/main/java/com/gongyoutong/app/database/WorkOrderDao.java
git commit -m "feat: add workspace data repository and models"
```

---

### Task 2: 重构 MainActivity 为工作台

**Files:**
- Modify: `app/src/main/java/com/gongyoutong/app/ui/main/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: 更新布局添加工作台元素**

```xml
<!-- 在 activity_main.xml 的 LinearLayout 中，AI 头像区域之后添加 -->

<!-- 今日概览卡片 -->
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginTop="16dp"
    app:cardCornerRadius="18dp"
    app:cardElevation="2dp"
    app:cardBackgroundColor="@color/card_background">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="18dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="今日概览"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold"/>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="14dp">

            <!-- 待办工单 -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center">

                <TextView
                    android:id="@+id/tvPendingOrders"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="0"
                    android:textColor="@color/primary"
                    android:textSize="28sp"
                    android:textStyle="bold"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="待接单"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginTop="4dp"/>
            </LinearLayout>

            <!-- 今日收入 -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center">

                <TextView
                    android:id="@+id/tvTodayIncome"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="¥0"
                    android:textColor="@color/success"
                    android:textSize="28sp"
                    android:textStyle="bold"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="今日收入"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginTop="4dp"/>
            </LinearLayout>

            <!-- 今日日程 -->
            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:gravity="center">

                <TextView
                    android:id="@+id/tvTodaySchedules"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="0"
                    android:textColor="@color/accent_blue"
                    android:textSize="28sp"
                    android:textStyle="bold"/>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="今日日程"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginTop="4dp"/>
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>

<!-- 快捷操作区 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginTop="20dp"
    android:orientation="horizontal"
    android:weightSum="4">

    <LinearLayout
        android:id="@+id/btnQuickOrder"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="8dp"
        android:background="?attr/selectableItemBackground"
        android:clickable="true"
        android:focusable="true">

        <ImageView
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@drawable/ic_workorder"
            android:padding="8dp"
            android:background="@drawable/bg_quick_action"
            app:tint="@color/primary"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="接单中心"
            android:textColor="@color/text_primary"
            android:textSize="11sp"
            android:layout_marginTop="6dp"/>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/btnQuickDiagnose"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="8dp"
        android:background="?attr/selectableItemBackground"
        android:clickable="true"
        android:focusable="true">

        <ImageView
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@drawable/ic_repair"
            android:padding="8dp"
            android:background="@drawable/bg_quick_action"
            app:tint="@color/accent_green"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="AI诊断"
            android:textColor="@color/text_primary"
            android:textSize="11sp"
            android:layout_marginTop="6dp"/>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/btnQuickKnowledge"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="8dp"
        android:background="?attr/selectableItemBackground"
        android:clickable="true"
        android:focusable="true">

        <ImageView
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@drawable/ic_knowledge"
            android:padding="8dp"
            android:background="@drawable/bg_quick_action"
            app:tint="@color/accent_blue"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="知识库"
            android:textColor="@color/text_primary"
            android:textSize="11sp"
            android:layout_marginTop="6dp"/>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/btnQuickCustomer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="8dp"
        android:background="?attr/selectableItemBackground"
        android:clickable="true"
        android:focusable="true">

        <ImageView
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@drawable/ic_profile"
            android:padding="8dp"
            android:background="@drawable/bg_quick_action"
            app:tint="@color/accent_purple"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="客户管理"
            android:textColor="@color/text_primary"
            android:textSize="11sp"
            android:layout_marginTop="6dp"/>
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: 创建快捷操作背景 drawable**

```xml
<!-- res/drawable/bg_quick_action.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/primary_container"/>
    <corners android:radius="12dp"/>
</shape>
```

- [ ] **Step 3: 更新 MainActivity 初始化工作台数据**

```java
// 在 MainActivity.java 中添加
private WorkspaceRepository workspaceRepository;
private TextView tvPendingOrders, tvTodayIncome, tvTodaySchedules;

// 在 onCreate 中初始化
workspaceRepository = WorkspaceRepository.getInstance(this);
tvPendingOrders = findViewById(R.id.tvPendingOrders);
tvTodayIncome = findViewById(R.id.tvTodayIncome);
tvTodaySchedules = findViewById(R.id.tvTodaySchedules);

// 添加加载工作台数据方法
private void loadWorkspaceData() {
    workspaceRepository.getWorkspaceData(data -> runOnUiThread(() -> {
        tvPendingOrders.setText(String.valueOf(data.pendingOrders));
        tvTodayIncome.setText(String.format("¥%.0f", data.todayIncome));
        tvTodaySchedules.setText(String.valueOf(data.todaySchedules.size()));
    }));
}

// 在 onResume 中调用
@Override
protected void onResume() {
    super.onResume();
    loadWorkspaceData();
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/ui/main/MainActivity.java
git add app/src/main/res/layout/activity_main.xml
git add app/src/main/res/drawable/bg_quick_action.xml
git commit -m "feat: redesign MainActivity as workspace hub"
```

---

## Phase 1B: 工单增强 - 报价和报告

### Task 3: 创建报价单数据模型

**Files:**
- Create: `app/src/main/java/com/gongyoutong/app/data/Quotation.java`
- Create: `app/src/main/java/com/gongyoutong/app/database/QuotationEntity.java`
- Create: `app/src/main/java/com/gongyoutong/app/database/QuotationDao.java`
- Modify: `app/src/main/java/com/gongyoutong/app/database/AppDatabase.java`

- [ ] **Step 1: 创建报价单实体**

```java
package com.gongyoutong.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "quotations")
public class QuotationEntity {
    @PrimaryKey
    private String id;
    private String workOrderId;
    private String customerId;
    private double totalAmount;
    private String itemsJson; // JSON格式的费用明细
    private String status; // 待确认/已确认/已收款
    private long createdAt;
    private long confirmedAt;
    private String customerSignature; // 客户签名路径

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(String workOrderId) { this.workOrderId = workOrderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getCustomerSignature() { return customerSignature; }
    public void setCustomerSignature(String customerSignature) { this.customerSignature = customerSignature; }
}
```

- [ ] **Step 2: 创建报价单 DAO**

```java
package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface QuotationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuotationEntity quotation);

    @Update
    void update(QuotationEntity quotation);

    @Query("SELECT * FROM quotations WHERE workOrderId = :workOrderId")
    QuotationEntity getByWorkOrderId(String workOrderId);

    @Query("SELECT * FROM quotations WHERE status = :status")
    List<QuotationEntity> getByStatus(String status);

    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM quotations WHERE status = '已收款' AND confirmedAt > :startTime")
    double getTotalIncome(long startTime);
}
```

- [ ] **Step 3: 创建报价单模型**

```java
package com.gongyoutong.app.data;

import java.util.List;

public class Quotation {
    private String id;
    private String workOrderId;
    private String customerId;
    private double totalAmount;
    private List<QuotationItem> items;
    private String status;
    private long createdAt;
    private long confirmedAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(String workOrderId) { this.workOrderId = workOrderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public List<QuotationItem> getItems() { return items; }
    public void setItems(List<QuotationItem> items) { this.items = items; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

    public static class QuotationItem {
        private String name;
        private int quantity;
        private double unitPrice;
        private double subtotal;
        private String category; // 配件/人工/其他

        public QuotationItem(String name, int quantity, double unitPrice, String category) {
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.subtotal = quantity * unitPrice;
            this.category = category;
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
        public double getSubtotal() { return subtotal; }
        public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}
```

- [ ] **Step 4: 更新 AppDatabase**

```java
// AppDatabase.java 修改
@Database(entities = {
    ScheduleEntity.class,
    KnowledgeEntity.class,
    KnowledgeVectorEntity.class,
    WorkOrderEntity.class,
    CustomerEntity.class,
    RepairRecordEntity.class,
    DiagnosisRecordEntity.class,
    QuotationEntity.class  // 新增
}, version = Config.DATABASE_VERSION + 1, exportSchema = false)

// 添加抽象方法
public abstract QuotationDao quotationDao();
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/data/Quotation.java
git add app/src/main/java/com/gongyoutong/app/database/QuotationEntity.java
git add app/src/main/java/com/gongyoutong/app/database/QuotationDao.java
git add app/src/main/java/com/gongyoutong/app/database/AppDatabase.java
git commit -m "feat: add quotation data model and DAO"
```

---

### Task 4: 创建 AI 报价生成服务

**Files:**
- Create: `app/src/main/java/com/gongyoutong/app/ai/QuotationAiService.java`

- [ ] **Step 1: 创建报价 AI 服务**

```java
package com.gongyoutong.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.Config;
import com.gongyoutong.app.data.Quotation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QuotationAiService {
    private static final String TAG = "QuotationAiService";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static volatile QuotationAiService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private QuotationAiService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static QuotationAiService getInstance() {
        if (sInstance == null) {
            synchronized (QuotationAiService.class) {
                if (sInstance == null) {
                    sInstance = new QuotationAiService();
                }
            }
        }
        return sInstance;
    }

    public interface QuotationCallback {
        void onSuccess(Quotation quotation);
        void onError(String msg);
    }

    public void generateQuotation(String faultDescription, String deviceType,
                                   List<String> partsUsed, QuotationCallback callback) {
        executor.execute(() -> {
            try {
                String prompt = buildQuotationPrompt(faultDescription, deviceType, partsUsed);

                JSONObject requestJson = new JSONObject();
                requestJson.put("model", Config.VIVO_MODEL);

                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是维修报价专家。根据维修内容生成合理的报价单。必须返回JSON格式。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                requestJson.put("messages", messages);
                requestJson.put("stream", false);
                requestJson.put("temperature", 0.3);
                requestJson.put("max_tokens", 1000);

                RequestBody body = RequestBody.create(requestJson.toString(), JSON_TYPE);
                Request request = new Request.Builder()
                        .url(Config.VIVO_API_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer " + Config.VIVO_APP_KEY)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("AI 服务暂时不可用"));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Quotation quotation = parseQuotationResponse(responseBody, faultDescription);

                    if (quotation != null) {
                        mainHandler.post(() -> callback.onSuccess(quotation));
                    } else {
                        mainHandler.post(() -> callback.onError("报价生成失败，请重试"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "generateQuotation error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("网络错误，请检查网络后重试"));
            }
        });
    }

    private String buildQuotationPrompt(String faultDescription, String deviceType,
                                         List<String> partsUsed) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下维修信息生成报价单：\n\n");
        prompt.append("设备类型：").append(deviceType != null ? deviceType : "未知").append("\n");
        prompt.append("故障描述：").append(faultDescription).append("\n");
        if (partsUsed != null && !partsUsed.isEmpty()) {
            prompt.append("使用的配件：").append(String.join("、", partsUsed)).append("\n");
        }
        prompt.append("\n请返回JSON格式：\n");
        prompt.append("{\"items\": [{\"name\": \"项目名称\", \"quantity\": 1, \"unitPrice\": 价格, \"category\": \"配件/人工/其他\"}], \"totalAmount\": 总价}\n");
        prompt.append("配件价格参考市场价，人工费根据工时合理定价。");
        return prompt.toString();
    }

    private Quotation parseQuotationResponse(String responseBody, String faultDescription) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.optJSONObject("message");
                if (message != null) {
                    String content = message.optString("content", "");
                    return parseQuotationJson(content);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseQuotationResponse error: " + e.getMessage());
        }
        return null;
    }

    private Quotation parseQuotationJson(String jsonStr) {
        try {
            String clean = jsonStr.replaceAll("```json", "").replaceAll("```", "").trim();
            int start = clean.indexOf("{");
            int end = clean.lastIndexOf("}");
            if (start < 0 || end < 0) return null;

            JSONObject json = new JSONObject(clean.substring(start, end + 1));
            Quotation quotation = new Quotation();
            quotation.setId(String.valueOf(System.currentTimeMillis()));
            quotation.setTotalAmount(json.optDouble("totalAmount", 0));
            quotation.setStatus("待确认");
            quotation.setCreatedAt(System.currentTimeMillis());

            List<Quotation.QuotationItem> items = new ArrayList<>();
            JSONArray itemsArray = json.optJSONArray("items");
            if (itemsArray != null) {
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject item = itemsArray.getJSONObject(i);
                    items.add(new Quotation.QuotationItem(
                            item.optString("name", ""),
                            item.optInt("quantity", 1),
                            item.optDouble("unitPrice", 0),
                            item.optString("category", "其他")
                    ));
                }
            }
            quotation.setItems(items);
            return quotation;
        } catch (Exception e) {
            Log.e(TAG, "parseQuotationJson error: " + e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/ai/QuotationAiService.java
git commit -m "feat: add AI quotation generation service"
```

---

## Phase 1C: 客户管理模块

### Task 5: 创建客户管理数据模型

**Files:**
- Modify: `app/src/main/java/com/gongyoutong/app/database/CustomerEntity.java`
- Create: `app/src/main/java/com/gongyoutong/app/data/Customer.java`
- Create: `app/src/main/java/com/gongyoutong/app/data/CustomerAdapter.java`

- [ ] **Step 1: 更新 CustomerEntity**

```java
// CustomerEntity.java 添加字段
private String tag; // 高价值/普通/易爽约/企业客户
private int serviceCount;
private double totalSpent;
private long lastServiceTime;
private String notes;

// 添加对应的 getters 和 setters
public String getTag() { return tag; }
public void setTag(String tag) { this.tag = tag; }
public int getServiceCount() { return serviceCount; }
public void setServiceCount(int serviceCount) { this.serviceCount = serviceCount; }
public double getTotalSpent() { return totalSpent; }
public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
public long getLastServiceTime() { return lastServiceTime; }
public void setLastServiceTime(long lastServiceTime) { this.lastServiceTime = lastServiceTime; }
public String getNotes() { return notes; }
public void setNotes(String notes) { this.notes = notes; }
```

- [ ] **Step 2: 创建 Customer 模型**

```java
package com.gongyoutong.app.data;

import java.util.Date;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String address;
    private String tag;
    private int serviceCount;
    private double totalSpent;
    private Date lastServiceTime;
    private String notes;
    private Date createdAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public int getServiceCount() { return serviceCount; }
    public void setServiceCount(int serviceCount) { this.serviceCount = serviceCount; }
    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
    public Date getLastServiceTime() { return lastServiceTime; }
    public void setLastServiceTime(Date lastServiceTime) { this.lastServiceTime = lastServiceTime; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: 创建 CustomerAdapter**

```java
package com.gongyoutong.app.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.gongyoutong.app.R;
import java.util.ArrayList;
import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder> {
    private List<Customer> customers = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Customer customer);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setList(List<Customer> list) {
        this.customers = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Customer customer = customers.get(position);
        holder.tvName.setText(customer.getName());
        holder.tvPhone.setText(customer.getPhone());
        holder.tvAddress.setText(customer.getAddress());
        holder.tvServiceCount.setText(customer.getServiceCount() + "次服务");
        holder.tvTotalSpent.setText(String.format("¥%.0f", customer.getTotalSpent()));

        if (customer.getTag() != null && !customer.getTag().isEmpty()) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText(customer.getTag());
        } else {
            holder.tvTag.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(customer);
        });
    }

    @Override
    public int getItemCount() {
        return customers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPhone, tvAddress, tvServiceCount, tvTotalSpent, tvTag;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCustomerName);
            tvPhone = itemView.findViewById(R.id.tvCustomerPhone);
            tvAddress = itemView.findViewById(R.id.tvCustomerAddress);
            tvServiceCount = itemView.findViewById(R.id.tvServiceCount);
            tvTotalSpent = itemView.findViewById(R.id.tvTotalSpent);
            tvTag = itemView.findViewById(R.id.tvCustomerTag);
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/database/CustomerEntity.java
git add app/src/main/java/com/gongyoutong/app/data/Customer.java
git add app/src/main/java/com/gongyoutong/app/data/CustomerAdapter.java
git commit -m "feat: add customer management data models"
```

---

### Task 6: 创建客户管理 Activity

**Files:**
- Create: `app/src/main/java/com/gongyoutong/app/ui/customer/CustomerActivity.java`
- Create: `app/src/main/res/layout/activity_customer.xml`
- Create: `app/src/main/res/layout/item_customer.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建客户列表布局**

```xml
<!-- res/layout/activity_customer.xml -->
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:fitsSystemWindows="true">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="客户管理"
            app:titleTextColor="@color/text_primary"
            app:navigationIcon="@drawable/ic_back"
            app:navigationIconTint="@color/text_primary"/>
    </com.google.android.material.appbar.AppBarLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <!-- 搜索框 -->
        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="16dp"
            android:layout_marginTop="8dp"
            style="@style/Widget.GongyouTong.TextInputLayout">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/etSearch"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="搜索客户姓名/电话"
                android:inputType="text"/>
        </com.google.android.material.textfield.TextInputLayout>

        <!-- 客户列表 -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvCustomers"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_marginTop="8dp"
            android:paddingHorizontal="16dp"
            android:clipToPadding="false"/>
    </LinearLayout>

    <!-- 添加客户 FAB -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAddCustomer"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:src="@drawable/ic_add"
        android:contentDescription="添加客户"
        app:backgroundTint="@color/primary"
        app:tint="@color/white"/>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: 创建客户列表项布局**

```xml
<!-- res/layout/item_customer.xml -->
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardCornerRadius="12dp"
    app:cardElevation="1dp"
    app:cardBackgroundColor="@color/card_background">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/tvCustomerName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="客户姓名"
                android:textColor="@color/text_primary"
                android:textSize="16sp"
                android:textStyle="bold"/>

            <TextView
                android:id="@+id/tvCustomerTag"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="高价值"
                android:textColor="@color/primary"
                android:textSize="11sp"
                android:paddingHorizontal="8dp"
                android:paddingVertical="2dp"
                android:background="@drawable/bg_status_badge_pending"
                android:visibility="gone"/>
        </LinearLayout>

        <TextView
            android:id="@+id/tvCustomerPhone"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="138****8888"
            android:textColor="@color/text_secondary"
            android:textSize="13sp"
            android:layout_marginTop="4dp"/>

        <TextView
            android:id="@+id/tvCustomerAddress"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="地址信息"
            android:textColor="@color/text_secondary"
            android:textSize="12sp"
            android:layout_marginTop="2dp"
            android:maxLines="1"
            android:ellipsize="end"/>

        <View
            android:layout_width="match_parent"
            android:layout_height="0.5dp"
            android:background="@color/divider"
            android:layout_marginVertical="10dp"/>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tvServiceCount"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="0次服务"
                android:textColor="@color/text_secondary"
                android:textSize="12sp"/>

            <TextView
                android:id="@+id/tvTotalSpent"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="¥0"
                android:textColor="@color/success"
                android:textSize="14sp"
                android:textStyle="bold"/>
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: 创建 CustomerActivity**

```java
package com.gongyoutong.app.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.gongyoutong.app.R;
import com.gongyoutong.app.data.Customer;
import com.gongyoutong.app.data.CustomerAdapter;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.CustomerDao;
import com.gongyoutong.app.database.CustomerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomerActivity extends AppCompatActivity {
    private RecyclerView rvCustomers;
    private CustomerAdapter adapter;
    private CustomerDao customerDao;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText etSearch;
    private List<Customer> allCustomers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        customerDao = AppDatabase.getInstance(this).customerDao();

        initViews();
        setupToolbar();
        setupSearch();
        loadCustomers();
    }

    private void initViews() {
        rvCustomers = findViewById(R.id.rvCustomers);
        etSearch = findViewById(R.id.etSearch);
        FloatingActionButton fabAdd = findViewById(R.id.fabAddCustomer);

        rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomerAdapter();
        adapter.setOnItemClickListener(customer -> {
            // 打开客户详情
            Toast.makeText(this, "查看客户: " + customer.getName(), Toast.LENGTH_SHORT).show();
        });
        rvCustomers.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddCustomerDialog());
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterCustomers(s.toString());
            }
        });
    }

    private void loadCustomers() {
        executor.execute(() -> {
            List<CustomerEntity> entities = customerDao.getAll();
            allCustomers.clear();
            for (CustomerEntity e : entities) {
                Customer c = new Customer();
                c.setId(e.getId());
                c.setName(e.getName());
                c.setPhone(e.getPhone());
                c.setAddress(e.getAddress());
                c.setTag(e.getTag());
                c.setServiceCount(e.getServiceCount());
                c.setTotalSpent(e.getTotalSpent());
                allCustomers.add(c);
            }
            runOnUiThread(() -> adapter.setList(new ArrayList<>(allCustomers)));
        });
    }

    private void filterCustomers(String query) {
        List<Customer> filtered = new ArrayList<>();
        for (Customer c : allCustomers) {
            if (c.getName().contains(query) || c.getPhone().contains(query)) {
                filtered.add(c);
            }
        }
        adapter.setList(filtered);
    }

    private void showAddCustomerDialog() {
        // 实现添加客户对话框
        Toast.makeText(this, "添加客户功能开发中", Toast.LENGTH_SHORT).show();
    }
}
```

- [ ] **Step 4: 更新 AndroidManifest**

```xml
<!-- AndroidManifest.xml 添加 -->
<activity
    android:name=".ui.customer.CustomerActivity"
    android:exported="false"
    android:parentActivityName=".ui.profile.ProfileActivity"/>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/ui/customer/CustomerActivity.java
git add app/src/main/res/layout/activity_customer.xml
git add app/src/main/res/layout/item_customer.xml
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add customer management activity"
```

---

## Phase 1D: 收入分析

### Task 7: 创建收入分析页面

**Files:**
- Create: `app/src/main/java/com/gongyoutong/app/ui/income/IncomeActivity.java`
- Create: `app/src/main/res/layout/activity_income.xml`
- Modify: `app/src/main/java/com/gongyoutong/app/ui/profile/ProfileActivity.java`

- [ ] **Step 1: 创建收入分析布局**

```xml
<!-- res/layout/activity_income.xml -->
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:fitsSystemWindows="true">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="收入分析"
            app:titleTextColor="@color/text_primary"
            app:navigationIcon="@drawable/ic_back"
            app:navigationIconTint="@color/text_primary"/>
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- 本月收入概览 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:cardCornerRadius="16dp"
                app:cardElevation="2dp"
                app:cardBackgroundColor="@color/primary">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="本月收入"
                        android:textColor="@color/white"
                        android:textSize="14sp"
                        android:alpha="0.8"/>

                    <TextView
                        android:id="@+id/tvMonthIncome"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="¥0"
                        android:textColor="@color/white"
                        android:textSize="36sp"
                        android:textStyle="bold"
                        android:layout_marginTop="4dp"/>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginTop="16dp">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="完成单数"
                                android:textColor="@color/white"
                                android:textSize="12sp"
                                android:alpha="0.7"/>

                            <TextView
                                android:id="@+id/tvMonthOrders"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="0 单"
                                android:textColor="@color/white"
                                android:textSize="18sp"
                                android:textStyle="bold"/>
                        </LinearLayout>

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="客单价"
                                android:textColor="@color/white"
                                android:textSize="12sp"
                                android:alpha="0.7"/>

                            <TextView
                                android:id="@+id/tvAvgPrice"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="¥0"
                                android:textColor="@color/white"
                                android:textSize="18sp"
                                android:textStyle="bold"/>
                        </LinearLayout>
                    </LinearLayout>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- 收入趋势 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:cardCornerRadius="16dp"
                app:cardElevation="1dp"
                app:cardBackgroundColor="@color/card_background">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="收入趋势"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:textStyle="bold"/>

                    <!-- 简单的收入趋势图（用进度条模拟） -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:layout_marginTop="16dp">

                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal"
                            android:gravity="center_vertical"
                            android:layout_marginBottom="8dp">

                            <TextView
                                android:layout_width="40dp"
                                android:layout_height="wrap_content"
                                android:text="周一"
                                android:textColor="@color/text_secondary"
                                android:textSize="12sp"/>

                            <View
                                android:id="@+id/barMon"
                                android:layout_width="0dp"
                                android:layout_height="16dp"
                                android:layout_weight="1"
                                android:background="@drawable/bg_income_bar"/>

                            <TextView
                                android:id="@+id/tvMon"
                                android:layout_width="60dp"
                                android:layout_height="wrap_content"
                                android:gravity="end"
                                android:text="¥0"
                                android:textColor="@color/text_primary"
                                android:textSize="12sp"/>
                        </LinearLayout>

                        <!-- 其他天类似 -->
                    </LinearLayout>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- 工单类型分析 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:cardCornerRadius="16dp"
                app:cardElevation="1dp"
                app:cardBackgroundColor="@color/card_background">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="工单类型分布"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:textStyle="bold"/>

                    <TextView
                        android:id="@+id/tvTypeStats"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="加载中..."
                        android:textColor="@color/text_secondary"
                        android:textSize="14sp"
                        android:layout_marginTop="12dp"/>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: 创建收入柱状图背景**

```xml
<!-- res/drawable/bg_income_bar.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/primary_container"/>
    <corners android:radius="4dp"/>
</shape>
```

- [ ] **Step 3: 创建 IncomeActivity**

```java
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

public class IncomeActivity extends AppCompatActivity {
    private TextView tvMonthIncome, tvMonthOrders, tvAvgPrice, tvTypeStats;
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
        tvTypeStats = findViewById(R.id.tvTypeStats);
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
```

- [ ] **Step 4: 更新 ProfileActivity 添加收入分析入口**

```java
// ProfileActivity.java 添加
private LinearLayout layoutIncome;

// initViews() 中添加
layoutIncome = findViewById(R.id.layoutIncome);

// setupClickListeners() 中添加
layoutIncome.setOnClickListener(v -> {
    Intent intent = new Intent(this, IncomeActivity.class);
    startActivity(intent);
});
```

- [ ] **Step 5: 更新 AndroidManifest**

```xml
<!-- AndroidManifest.xml 添加 -->
<activity
    android:name=".ui.income.IncomeActivity"
    android:exported="false"
    android:parentActivityName=".ui.profile.ProfileActivity"/>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/gongyoutong/app/ui/income/IncomeActivity.java
git add app/src/main/res/layout/activity_income.xml
git add app/src/main/res/drawable/bg_income_bar.xml
git add app/src/main/java/com/gongyoutong/app/ui/profile/ProfileActivity.java
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add income analysis activity"
```

---

## 执行说明

由于这是一个大型项目，建议按以下顺序执行：

1. **Phase 1A** (Task 1-2): 工作台重构 - 这是用户每天打开 App 看到的第一个页面
2. **Phase 1B** (Task 3-4): 工单报价增强 - 提升工单完成后的收款效率
3. **Phase 1C** (Task 5-6): 客户管理 - 建立客户档案，提升复购率
4. **Phase 1D** (Task 7): 收入分析 - 帮助工人了解经营情况

每个 Phase 完成后都应该进行测试和编译验证。

**注意事项：**
- 数据库版本升级需要添加 Migration
- 所有 AI 服务调用需要网络权限
- 地图相关功能需要定位权限
- 建议在真机上测试百度地图功能
