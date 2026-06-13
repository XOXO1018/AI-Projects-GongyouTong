package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 维修记录数据访问对象
 */
@Dao
public interface RepairRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RepairRecordEntity record);

    @Update
    void update(RepairRecordEntity record);

    @Delete
    void delete(RepairRecordEntity record);

    @Query("SELECT * FROM repair_records WHERE id = :id LIMIT 1")
    RepairRecordEntity getById(String id);

    @Query("SELECT * FROM repair_records WHERE workOrderId = :workOrderId ORDER BY createdAt DESC")
    List<RepairRecordEntity> getByWorkOrderId(String workOrderId);
}
