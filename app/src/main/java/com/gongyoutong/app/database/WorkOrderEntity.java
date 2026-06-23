package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 工单数据库实体
 */
@Entity(tableName = "work_orders")
public class WorkOrderEntity {

    @PrimaryKey
    @NonNull
    private String orderNo;

    private String customerId;
    private String title;
    private String workType;
    private String description;
    private String address;

    private double latitude;
    private double longitude;

    private String contactName;
    private String contactPhone;

    /** 存枚举 name()，如 PENDING、ACCEPTED */
    private String status;

    private long appointmentTime;
    private long arrivedAt;
    private long completedAt;

    /** AI 故障预判结果 */
    private String aiPrediction;
    /** AI 工具推荐结果 */
    private String aiTools;

    /** 关联日程 ID */
    private String scheduleId;

    /** 工单金额 */
    private Double amount;

    private long createdAt;
    private long updatedAt;

    public WorkOrderEntity() {
        this.orderNo = "";
    }

    // ==================== Getters & Setters ====================

    @NonNull
    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(@NonNull String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getWorkType() {
        return workType;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(long appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public long getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(long arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public String getAiPrediction() {
        return aiPrediction;
    }

    public void setAiPrediction(String aiPrediction) {
        this.aiPrediction = aiPrediction;
    }

    public String getAiTools() {
        return aiTools;
    }

    public void setAiTools(String aiTools) {
        this.aiTools = aiTools;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
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
}
