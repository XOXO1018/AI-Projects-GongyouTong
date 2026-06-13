package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 知识向量实体
 * 存储知识条目的向量化表示，用于语义检索
 */
@Entity(tableName = "knowledge_vectors")
public class KnowledgeVectorEntity {

    @NonNull
    @PrimaryKey
    private String id;              // 向量条目唯一 ID

    private String title;           // 知识标题
    private String content;         // 知识内容（原始文本）
    private String category;        // 分类（如 device_model, fault_type, solution）
    private String embeddingJson;   // 向量 JSON 字符串（embedding 序列化）
    private long createdAt;         // 创建时间戳（毫秒）

    /** 默认构造函数（Room 要求） */
    public KnowledgeVectorEntity() {
        this.id = "";
        this.title = "";
        this.content = "";
        this.category = "";
        this.embeddingJson = null;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 全参构造函数（Room 使用无参构造 + setter，此构造供业务代码使用）
     *
     * @param id           条目唯一 ID
     * @param title        知识标题
     * @param content      知识内容
     * @param category     分类
     * @param embeddingJson 向量 JSON（可为 null）
     */
    @Ignore
    public KnowledgeVectorEntity(@NonNull String id, String title, String content,
                                  String category, String embeddingJson) {
        this.id = id;
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
        this.category = category != null ? category : "";
        this.embeddingJson = embeddingJson;
        this.createdAt = System.currentTimeMillis();
    }

    // ==================== Getters ====================

    @NonNull
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getCategory() {
        return category;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ==================== Setters ====================

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "";
    }

    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
