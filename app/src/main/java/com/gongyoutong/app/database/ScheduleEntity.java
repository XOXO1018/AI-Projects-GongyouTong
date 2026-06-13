package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 日程数据库实体
 */
@Entity(tableName = "schedules")
public class ScheduleEntity {
    
    @PrimaryKey
    @NonNull
    private String id;
    
    private String title;
    private String address;
    private String workType;
    private long time;  // 时间戳
    private String status;  // 待出发、进行中、已完成、已取消
    private long createdAt;
    private long updatedAt;
    
    // 导航相关
    private double latitude;
    private double longitude;
    
    // AI 提醒
    private String aiReminder;

    // 关联工单
    private String workOrderNo;

    // 客户信息（从工单同步或手动添加）
    private String contactName;
    private String contactPhone;

    // 设备信息（从工单同步或手动添加）
    private String deviceBrand;
    private String deviceModel;
    private String deviceAge;

    // 故障描述（从工单同步）
    private String description;

    // Constructor
    public ScheduleEntity() {
        this.id = "";
    }
    
    // Getters and Setters
    @NonNull
    public String getId() {
        return id;
    }
    
    public void setId(@NonNull String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getWorkType() {
        return workType;
    }
    
    public void setWorkType(String workType) {
        this.workType = workType;
    }
    
    public long getTime() {
        return time;
    }
    
    public void setTime(long time) {
        this.time = time;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
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
    
    public String getAiReminder() {
        return aiReminder;
    }
    
    public void setAiReminder(String aiReminder) {
        this.aiReminder = aiReminder;
    }

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getDeviceBrand() {
        return deviceBrand;
    }

    public void setDeviceBrand(String deviceBrand) {
        this.deviceBrand = deviceBrand;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getDeviceAge() {
        return deviceAge;
    }

    public void setDeviceAge(String deviceAge) {
        this.deviceAge = deviceAge;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
