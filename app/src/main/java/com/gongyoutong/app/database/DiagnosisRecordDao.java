package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * 诊断记录 DAO
 * 提供诊断记录的增删查操作
 */
@Dao
public interface DiagnosisRecordDao {

    /**
     * 插入一条诊断记录
     *
     * @param record 诊断记录实体
     */
    @Insert
    void insert(DiagnosisRecordEntity record);

    /**
     * 根据工单ID查询所有诊断记录，按轮次升序排列
     *
     * @param workOrderId 工单ID
     * @return 诊断记录列表
     */
    @Query("SELECT * FROM diagnosis_records WHERE workOrderId = :workOrderId ORDER BY round ASC")
    List<DiagnosisRecordEntity> getByWorkOrderId(String workOrderId);

    /**
     * 根据工单ID查询最新一条诊断记录
     *
     * @param workOrderId 工单ID
     * @return 最新的诊断记录，如果没有则返回 null
     */
    @Query("SELECT * FROM diagnosis_records WHERE workOrderId = :workOrderId ORDER BY createdAt DESC LIMIT 1")
    DiagnosisRecordEntity getLatestByWorkOrderId(String workOrderId);
}
