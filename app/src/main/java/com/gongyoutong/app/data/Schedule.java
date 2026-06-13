package com.gongyoutong.app.data;

import java.util.Date;
import java.util.UUID;

public class Schedule {
    private String id;
    private String title;
    private String address;
    private String workType;
    private Date time;
    private String status; // 待出发 / 进行中 / 已完成 / 已取消

    public Schedule() {
        this.id = UUID.randomUUID().toString();
    }

    public Schedule(String id, String title, String address, String workType, Date time, String status) {
        this.id = id;
        this.title = title;
        this.address = address;
        this.workType = workType;
        this.time = time;
        this.status = status;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAddress() {
        return address;
    }

    public String getWorkType() {
        return workType;
    }

    public Date getTime() {
        return time;
    }

    public String getStatus() {
        return status;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // Status helpers
    public boolean isPending() {
        return "待出发".equals(status);
    }

    public boolean isInProgress() {
        return "进行中".equals(status);
    }

    public boolean isCompleted() {
        return "已完成".equals(status);
    }

    public boolean isCancelled() {
        return "已取消".equals(status);
    }

    public void markAsInProgress() {
        this.status = "进行中";
    }

    public void markAsCompleted() {
        this.status = "已完成";
    }

    public void markAsCancelled() {
        this.status = "已取消";
    }

    @Override
    public String toString() {
        return "Schedule{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", address='" + address + '\'' +
                ", workType='" + workType + '\'' +
                ", time=" + time +
                ", status='" + status + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schedule schedule = (Schedule) o;
        return id != null && id.equals(schedule.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
