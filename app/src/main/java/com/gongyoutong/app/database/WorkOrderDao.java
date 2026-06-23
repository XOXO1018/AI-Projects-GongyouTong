package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 工单数据访问对象
 */
@Dao
public interface WorkOrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WorkOrderEntity workOrder);

    @Update
    void update(WorkOrderEntity workOrder);

    @Delete
    void delete(WorkOrderEntity workOrder);

    @Query("SELECT * FROM work_orders WHERE orderNo = :orderNo LIMIT 1")
    WorkOrderEntity getById(String orderNo);

    @Query("SELECT * FROM work_orders ORDER BY appointmentTime DESC")
    List<WorkOrderEntity> getAll();

    @Query("SELECT * FROM work_orders WHERE status = :status ORDER BY appointmentTime DESC")
    List<WorkOrderEntity> getByStatus(String status);

    /** 查询待接单工单 */
    @Query("SELECT * FROM work_orders WHERE status = 'PENDING' ORDER BY appointmentTime ASC")
    List<WorkOrderEntity> getPendingOrders();

    /** 查询进行中工单（状态不在 COMPLETED 和 EXCEPTION 之中） */
    @Query("SELECT * FROM work_orders WHERE status NOT IN ('COMPLETED', 'EXCEPTION') ORDER BY appointmentTime ASC")
    List<WorkOrderEntity> getActiveOrders();

    /** 更新工单状态 */
    @Query("UPDATE work_orders SET status = :status, updatedAt = :updatedAt WHERE orderNo = :orderNo")
    void updateStatus(String orderNo, String status, long updatedAt);

    /** 按状态统计工单数 */
    @Query("SELECT COUNT(*) FROM work_orders WHERE status = :status")
    int countByStatus(String status);

    /** 更新到场时间 */
    @Query("UPDATE work_orders SET arrivedAt = :arrivedAt, status = 'ARRIVED', updatedAt = :updatedAt WHERE orderNo = :orderNo")
    void updateArrivedAt(String orderNo, long arrivedAt, long updatedAt);

    /** 更新完成时间 */
    @Query("UPDATE work_orders SET completedAt = :completedAt, status = 'COMPLETED', updatedAt = :updatedAt WHERE orderNo = :orderNo")
    void updateCompletedAt(String orderNo, long completedAt, long updatedAt);

    /** 更新 AI 故障预判 */
    @Query("UPDATE work_orders SET aiPrediction = :aiPrediction, updatedAt = :updatedAt WHERE orderNo = :orderNo")
    void updateAiPrediction(String orderNo, String aiPrediction, long updatedAt);

    /** 更新 AI 工具推荐 */
    @Query("UPDATE work_orders SET aiTools = :aiTools, updatedAt = :updatedAt WHERE orderNo = :orderNo")
    void updateAiTools(String orderNo, String aiTools, long updatedAt);

    /** 更新关联日程ID */
    @Query("UPDATE work_orders SET scheduleId = :scheduleId, updatedAt = :updatedAt WHERE orderNo = :orderNo")
    void updateScheduleId(String orderNo, String scheduleId, long updatedAt);

    /** 获取指定时间后的已完成工单数 */
    @Query("SELECT COUNT(*) FROM work_orders WHERE status = 'COMPLETED' AND completedAt > :startTime")
    int getCompletedCount(long startTime);

    /** 获取指定时间后的总收入 */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM work_orders WHERE status = 'COMPLETED' AND completedAt > :startTime")
    double getTotalIncome(long startTime);
}
