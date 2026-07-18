package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 知识库数据库实体
 */
@Entity(tableName = "knowledge")
public class KnowledgeEntity {

    @PrimaryKey
    @NonNull
    private String id;

    /** 标题（AI 自动提取或用户输入的前几十字） */
    private String title;

    /** 原始内容（保留完整录入内容） */
    private String rawContent;

    /** AI 提取的摘要/总结 */
    private String aiSummary;

    /**
     * 录入来源类型
     * "voice"    - 语音输入
     * "photo"    - 拍照/图片识别
     * "document" - 文档导入
     * "text"     - 手动文字输入
     */
    private String sourceType;

    /** 录入人姓名（从 SharedPreferences 读取，默认"工友通师傅"） */
    private String creatorName;

    /** 创建时间戳 */
    private long createdAt;

    /** 更新时间戳 */
    private long updatedAt;

    /** 思维导图 JSON（AI 生成的层次化知识结构，可为 null） */
    private String mindMapJson;

    public KnowledgeEntity() {
        this.id = "";
    }

    // ==================== Getters & Setters ====================

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getMindMapJson() { return mindMapJson; }
    public void setMindMapJson(String mindMapJson) { this.mindMapJson = mindMapJson; }
}
