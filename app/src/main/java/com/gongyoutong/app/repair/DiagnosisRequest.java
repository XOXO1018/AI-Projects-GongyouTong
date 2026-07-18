package com.gongyoutong.app.repair;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 诊断请求数据类
 * 封装发送给 AI 的多模态诊断请求参数
 */
public class DiagnosisRequest {

    private String faultDescription;                         // 故障描述
    private List<String> photoBase64List;                     // 照片 Base64 列表
    private List<DiagnosisHistoryMessage> historyMessages;   // 历史上下文消息
    private String workType;                                  // 工单类型
    private String workOrderTitle;                            // 工单标题

    public DiagnosisRequest() {
        this.photoBase64List = new ArrayList<>();
        this.historyMessages = new ArrayList<>();
    }

    // ==================== Getters ====================

    public String getFaultDescription() {
        return faultDescription;
    }

    public List<String> getPhotoBase64List() {
        return photoBase64List;
    }

    public List<DiagnosisHistoryMessage> getHistoryMessages() {
        return historyMessages;
    }

    public String getWorkType() {
        return workType;
    }

    public String getWorkOrderTitle() {
        return workOrderTitle;
    }

    // ==================== Setters ====================

    public void setFaultDescription(String faultDescription) {
        this.faultDescription = faultDescription;
    }

    public void setPhotoBase64List(List<String> photoBase64List) {
        this.photoBase64List = photoBase64List;
    }

    public void setHistoryMessages(List<DiagnosisHistoryMessage> historyMessages) {
        this.historyMessages = historyMessages;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
    }

    public void setWorkOrderTitle(String workOrderTitle) {
        this.workOrderTitle = workOrderTitle;
    }

    /**
     * 添加一张照片的 Base64 数据
     *
     * @param photoBase64 照片 Base64 字符串
     */
    public void addPhoto(String photoBase64) {
        if (photoBase64List == null) {
            photoBase64List = new ArrayList<>();
        }
        photoBase64List.add(photoBase64);
    }

    /**
     * 添加历史消息
     *
     * @param role    角色（"user" 或 "assistant"）
     * @param content 消息内容
     */
    public void addHistoryMessage(String role, String content) {
        if (historyMessages == null) {
            historyMessages = new ArrayList<>();
        }
        historyMessages.add(new DiagnosisHistoryMessage(role, content));
    }

    /**
     * 历史上下文消息内部类
     */
    public static class DiagnosisHistoryMessage {

        private String role;     // "user" or "assistant"
        private String content;  // 消息内容

        public DiagnosisHistoryMessage() {
        }

        public DiagnosisHistoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
