package com.gongyoutong.app.data;

import com.gongyoutong.app.database.WorkOrderEntity;

/**
 * 工单 POJO，用于 Adapter 展示
 */
public class WorkOrder {

    private String id;       // 即 orderNo
    private String orderNo;
    private String title;
    private String workType;
    private String description;
    private String address;
    private double latitude;
    private double longitude;
    private String contactName;
    private String contactPhone;
    private String status;
    private long appointmentTime;
    private String aiPrediction;
    private String aiTools;

    public WorkOrder() {
    }

    /**
     * 从 Room Entity 转换为展示 POJO
     */
    public static WorkOrder fromEntity(WorkOrderEntity entity) {
        if (entity == null) {
            return null;
        }
        WorkOrder wo = new WorkOrder();
        wo.id = entity.getOrderNo();
        wo.orderNo = entity.getOrderNo();
        wo.title = entity.getTitle();
        wo.workType = entity.getWorkType();
        wo.description = entity.getDescription();
        wo.address = entity.getAddress();
        wo.latitude = entity.getLatitude();
        wo.longitude = entity.getLongitude();
        wo.contactName = entity.getContactName();
        wo.contactPhone = entity.getContactPhone();
        wo.status = entity.getStatus();
        wo.appointmentTime = entity.getAppointmentTime();
        wo.aiPrediction = entity.getAiPrediction();
        wo.aiTools = entity.getAiTools();
        return wo;
    }

    // ==================== Getters & Setters ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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
}
