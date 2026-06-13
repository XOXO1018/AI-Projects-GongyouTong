package com.gongyoutong.app.repair;

/**
 * 维修意图数据类
 * AI 识别的用户意图
 */
public class RepairIntention {

    /** 意图类型枚举 */
    public enum Type {
        /** 请求指导 */
        ASK_GUIDANCE,
        /** 报告进度 */
        REPORT_PROGRESS,
        /** 询问工具 */
        ASK_TOOL,
        /** 报告问题 */
        REPORT_ISSUE,
        /** 确认完成 */
        CONFIRM_COMPLETE,
        /** 其他/未知 */
        OTHER
    }

    private Type type;           // 意图类型
    private float confidence;    // 置信度 (0.0 ~ 1.0)
    private String rawText;      // 原始识别文本

    /** 默认构造函数 */
    public RepairIntention() {
        this.type = Type.OTHER;
        this.confidence = 0.0f;
        this.rawText = "";
    }

    /**
     * 全参构造函数
     */
    public RepairIntention(Type type, float confidence, String rawText) {
        this.type = type != null ? type : Type.OTHER;
        this.confidence = confidence;
        this.rawText = rawText != null ? rawText : "";
    }

    // ==================== Getters ====================

    public Type getType() {
        return type;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getRawText() {
        return rawText;
    }

    // ==================== Setters ====================

    public void setType(Type type) {
        this.type = type != null ? type : Type.OTHER;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText != null ? rawText : "";
    }
}
