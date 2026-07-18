package com.gongyoutong.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.database.WorkOrderEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 工作台数据仓库
 * 提供今日概览、紧急任务等数据
 */
public class WorkspaceRepository {
    private static volatile WorkspaceRepository INSTANCE;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    /**
     * 获取工作台数据（异步）
     */
    public void getWorkspaceData(WorkspaceCallback callback) {
        executor.execute(() -> {
            WorkspaceData data = new WorkspaceData();
            data.todaySchedules = getTodaySchedules();
            data.pendingOrders = getPendingOrders();
            data.todayIncome = calculateTodayIncome();
            data.urgentTasks = getUrgentTasks();
            mainHandler.post(() -> callback.onResult(data));
        });
    }

    /**
     * 获取今日日程
     */
    private List<Schedule> getTodaySchedules() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
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
            s.setWorkType(e.getWorkType());
            s.setTime(new java.util.Date(e.getTime()));
            s.setStatus(e.getStatus());
            result.add(s);
        }
        return result;
    }

    /**
     * 获取待接单工单数
     */
    private int getPendingOrders() {
        return db.workOrderDao().countByStatus("PENDING");
    }

    /**
     * 计算今日收入
     */
    private double calculateTodayIncome() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long startOfDay = today.getTimeInMillis();
        return db.workOrderDao().getTotalIncome(startOfDay);
    }

    /**
     * 获取紧急任务（进行中或过期的待出发）
     */
    private List<Schedule> getUrgentTasks() {
        long now = System.currentTimeMillis();
        List<ScheduleEntity> entities = db.scheduleDao().getUrgentSchedules(now);
        List<Schedule> result = new ArrayList<>();
        for (ScheduleEntity e : entities) {
            Schedule s = new Schedule();
            s.setId(e.getId());
            s.setTitle(e.getTitle());
            s.setAddress(e.getAddress());
            s.setWorkType(e.getWorkType());
            s.setTime(new java.util.Date(e.getTime()));
            s.setStatus(e.getStatus());
            result.add(s);
        }
        return result;
    }

    /**
     * 工作台数据模型
     */
    public static class WorkspaceData {
        public List<Schedule> todaySchedules = new ArrayList<>();
        public int pendingOrders = 0;
        public double todayIncome = 0;
        public List<Schedule> urgentTasks = new ArrayList<>();
    }
}
