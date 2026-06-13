package com.gongyoutong.app.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 诊断记录实体
 * 存储 AI 辅助诊断的结果，支持多轮诊断
 */
@Entity(tableName = "diagnosis_records")
public class DiagnosisRecordEntity {

    @NonNull
    @PrimaryKey
    private String id;              // 格式: diag_{workOrderId}_{timestamp}


    private String workOrderId;     // 关联工单ID
    private int round;              // 诊断轮次（1=首次，2=补充后）
    private String faultDescription; // 故障描述
    private String photoPaths;      // 照片本地路径（逗号分隔）
    private String diagnosisResult; // AI 诊断结果 JSON
    private String rawAiResponse;   // AI 原始返回
    private String aiSource;        // CLOUD_AI / LOCAL_FALLBACK
    private long createdAt;         // 创建时间戳

    public DiagnosisRecordEntity() {
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public String getWorkOrderId() {
        return workOrderId;
    }

    public int getRound() {
        return round;
    }

    public String getFaultDescription() {
        return faultDescription;
    }

    public String getPhotoPaths() {
        return photoPaths;
    }

    public String getDiagnosisResult() {
        return diagnosisResult;
    }

    public String getRawAiResponse() {
        return rawAiResponse;
    }

    public String getAiSource() {
        return aiSource;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ==================== Setters ====================

    public void setId(String id) {
        this.id = id;
    }

    public void setWorkOrderId(String workOrderId) {
        this.workOrderId = workOrderId;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public void setFaultDescription(String faultDescription) {
        this.faultDescription = faultDescription;
    }

    public void setPhotoPaths(String photoPaths) {
        this.photoPaths = photoPaths;
    }

    public void setDiagnosisResult(String diagnosisResult) {
        this.diagnosisResult = diagnosisResult;
    }

    public void setRawAiResponse(String rawAiResponse) {
        this.rawAiResponse = rawAiResponse;
    }

    public void setAiSource(String aiSource) {
        this.aiSource = aiSource;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
