package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 报价单数据访问对象
 */
@Dao
public interface QuotationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(QuotationEntity quotation);

    @Update
    void update(QuotationEntity quotation);

    @Query("SELECT * FROM quotations WHERE workOrderId = :workOrderId LIMIT 1")
    QuotationEntity getByWorkOrderId(String workOrderId);

    @Query("SELECT * FROM quotations WHERE status = :status")
    List<QuotationEntity> getByStatus(String status);

    @Query("SELECT * FROM quotations ORDER BY createdAt DESC")
    List<QuotationEntity> getAll();

    @Query("SELECT COALESCE(SUM(totalAmount), 0) FROM quotations WHERE status = '已收款' AND confirmedAt > :startTime")
    double getTotalIncome(long startTime);

    @Query("DELETE FROM quotations WHERE id = :id")
    void deleteById(String id);
}
