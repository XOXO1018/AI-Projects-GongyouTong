package com.gongyoutong.app.repair;

/**
 * 维修步骤数据类
 * AI 规划的单个维修步骤
 */
public class RepairStep {

    private String title;          // 步骤标题
    private String description;    // 步骤详细描述
    private String toolRequired;   // 所需工具
    private String safetyNote;     // 安全注意事项
    private boolean completed;     // 是否已完成
    private boolean skipped;       // 是否已跳过

    /** 默认构造函数 */
    public RepairStep() {
        this.title = "";
        this.description = "";
        this.toolRequired = "";
        this.safetyNote = "";
        this.completed = false;
        this.skipped = false;
    }

    /**
     * 全参构造函数
     */
    public RepairStep(String title, String description, String toolRequired, String safetyNote) {
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.toolRequired = toolRequired != null ? toolRequired : "";
        this.safetyNote = safetyNote != null ? safetyNote : "";
        this.completed = false;
        this.skipped = false;
    }

    // ==================== Getters ====================

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getToolRequired() {
        return toolRequired;
    }

    public String getSafetyNote() {
        return safetyNote;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isSkipped() {
        return skipped;
    }

    // ==================== Setters ====================

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public void setToolRequired(String toolRequired) {
        this.toolRequired = toolRequired != null ? toolRequired : "";
    }

    public void setSafetyNote(String safetyNote) {
        this.safetyNote = safetyNote != null ? safetyNote : "";
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }
}
