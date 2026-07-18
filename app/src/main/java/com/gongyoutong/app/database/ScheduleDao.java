package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 日程数据访问对象
 */
@Dao
public interface ScheduleDao {
    
    // 插入日程
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ScheduleEntity schedule);
    
    // 更新日程
    @Update
    void update(ScheduleEntity schedule);
    
    // 删除日程
    @Delete
    void delete(ScheduleEntity schedule);
    
    // 根据 ID 删除
    @Query("DELETE FROM schedules WHERE id = :id")
    void deleteById(String id);
    
    // 根据 ID 查询
    @Query("SELECT * FROM schedules WHERE id = :id")
    ScheduleEntity getById(String id);
    
    // 查询所有日程（按时间排序）
    @Query("SELECT * FROM schedules ORDER BY time ASC")
    List<ScheduleEntity> getAll();
    
    // 查询未完成的日程
    @Query("SELECT * FROM schedules WHERE status != '已完成' AND status != '已取消' ORDER BY time ASC")
    List<ScheduleEntity> getPending();
    
    // 查询今日日程
    @Query("SELECT * FROM schedules WHERE date(time/1000, 'unixepoch', 'localtime') = date('now', 'localtime') ORDER BY time ASC")
    List<ScheduleEntity> getTodaySchedules();
    
    // 查询未来日程
    @Query("SELECT * FROM schedules WHERE time >= :startTime AND status != '已完成' AND status != '已取消' ORDER BY time ASC")
    List<ScheduleEntity> getUpcomingSchedules(long startTime);
    
    // 查询过期的待出发日程
    @Query("UPDATE schedules SET status = '已过期' WHERE status = '待出发' AND time < :currentTime")
    void markExpiredSchedules(long currentTime);
    
    // 更新日程状态
    @Query("UPDATE schedules SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    void updateStatus(String id, String status, long updatedAt);
    
    // 获取日程数量
    @Query("SELECT COUNT(*) FROM schedules WHERE status != '已完成' AND status != '已取消'")
    int getActiveScheduleCount();

    // 查询指定时间范围内的日程
    @Query("SELECT * FROM schedules WHERE time BETWEEN :start AND :end ORDER BY time ASC")
    List<ScheduleEntity> getSchedulesBetween(long start, long end);

    // 查询紧急日程（进行中或过期待出发）
    @Query("SELECT * FROM schedules WHERE status = '进行中' OR (time < :now AND status = '待出发')")
    List<ScheduleEntity> getUrgentSchedules(long now);
}
