package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 客户数据库实体
 */
@Entity(tableName = "customers")
public class CustomerEntity {

    @PrimaryKey
    @NonNull
    private String id;

    private String name;
    private String phone;
    private String address;

    private double latitude;
    private double longitude;

    private int orderCount;

    /** 客户标签：高价值/普通/易爽约/企业客户 */
    private String tag;
    /** 服务次数 */
    private int serviceCount;
    /** 总消费金额 */
    private double totalSpent;
    /** 最后服务时间 */
    private long lastServiceTime;
    /** 备注 */
    private String notes;

    private long createdAt;
    private long updatedAt;

    public CustomerEntity() {
        this.id = "";
    }

    // ==================== Getters & Setters ====================

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(int orderCount) {
        this.orderCount = orderCount;
    }

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

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
