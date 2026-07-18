package com.gongyoutong.app.data;

import java.util.Date;

/**
 * 客户模型
 */
public class Customer {
    private String id;
    private String name;
    private String phone;
    private String address;
    private String tag;
    private int serviceCount;
    private double totalSpent;
    private Date lastServiceTime;
    private String notes;
    private Date createdAt;

    public Customer() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public int getServiceCount() { return serviceCount; }
    public void setServiceCount(int serviceCount) { this.serviceCount = serviceCount; }

    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }

    public Date getLastServiceTime() { return lastServiceTime; }
    public void setLastServiceTime(Date lastServiceTime) { this.lastServiceTime = lastServiceTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
