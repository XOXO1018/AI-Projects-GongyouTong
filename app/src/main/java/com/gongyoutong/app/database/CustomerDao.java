package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 客户数据访问对象
 */
@Dao
public interface CustomerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CustomerEntity customer);

    @Update
    void update(CustomerEntity customer);

    @Delete
    void delete(CustomerEntity customer);

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    CustomerEntity getById(String id);

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    CustomerEntity getByPhone(String phone);

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    List<CustomerEntity> getAll();
}
