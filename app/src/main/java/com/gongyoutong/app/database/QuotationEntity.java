package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 报价单数据库实体
 */
@Entity(tableName = "quotations")
public class QuotationEntity {

    @PrimaryKey
    @NonNull
    private String id;

    private String workOrderId;
    private String customerId;
    private double totalAmount;
    private String itemsJson; // JSON格式的费用明细
    private String status; // 待确认/已确认/已收款
    private long createdAt;
    private long confirmedAt;
    private String customerSignature; // 客户签名路径

    public QuotationEntity() {
        this.id = "";
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(String workOrderId) { this.workOrderId = workOrderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

    public String getCustomerSignature() { return customerSignature; }
    public void setCustomerSignature(String customerSignature) { this.customerSignature = customerSignature; }
}
