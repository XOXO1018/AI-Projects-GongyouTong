package com.gongyoutong.app.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 报价单模型
 */
public class Quotation {
    private String id;
    private String workOrderId;
    private String customerId;
    private double totalAmount;
    private List<QuotationItem> items;
    private String status;
    private long createdAt;
    private long confirmedAt;

    public Quotation() {
        this.items = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(String workOrderId) { this.workOrderId = workOrderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public List<QuotationItem> getItems() { return items; }
    public void setItems(List<QuotationItem> items) { this.items = items; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }

    /**
     * 报价单项
     */
    public static class QuotationItem {
        private String name;
        private int quantity;
        private double unitPrice;
        private double subtotal;
        private String category; // 配件/人工/其他

        public QuotationItem() {}

        public QuotationItem(String name, int quantity, double unitPrice, String category) {
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.subtotal = quantity * unitPrice;
            this.category = category;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) {
            this.quantity = quantity;
            this.subtotal = this.quantity * this.unitPrice;
        }

        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) {
            this.unitPrice = unitPrice;
            this.subtotal = this.quantity * this.unitPrice;
        }

        public double getSubtotal() { return subtotal; }
        public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}
