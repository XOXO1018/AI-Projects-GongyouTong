package com.gongyoutong.app.repair;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频帧分析结果
 * AI 视觉分析服务返回的结构化结果
 */
public class FrameAnalysisResult {

    private String description;                // 场景描述文本
    private List<BoundingBox> regions;         // 检测到的区域列表
    private float confidence;                  // 整体置信度 (0.0 ~ 1.0)
    private boolean safetyPowerOff;            // 是否已断电操作
    private String visionDetectedAction;       // 视觉分析检测到的操作
    private String visionDetectedTool;         // 视觉分析检测到的工具
    private String ocrErrorCode;               // OCR 识别到的错误代码

    /** 默认构造函数 */
    public FrameAnalysisResult() {
        this.description = "";
        this.regions = new ArrayList<>();
        this.confidence = 0.0f;
        this.safetyPowerOff = false;
        this.visionDetectedAction = "";
        this.visionDetectedTool = "";
        this.ocrErrorCode = "";
    }

    /**
     * 全参构造函数
     */
    public FrameAnalysisResult(String description, List<BoundingBox> regions, float confidence) {
        this.description = description != null ? description : "";
        this.regions = regions != null ? regions : new ArrayList<BoundingBox>();
        this.confidence = confidence;
        this.safetyPowerOff = false;
        this.visionDetectedAction = "";
        this.visionDetectedTool = "";
        this.ocrErrorCode = "";
    }

    // ==================== Getters ====================

    public String getDescription() {
        return description;
    }

    public List<BoundingBox> getRegions() {
        return regions;
    }

    public float getConfidence() {
        return confidence;
    }

    public boolean isSafetyPowerOff() {
        return safetyPowerOff;
    }

    public String getVisionDetectedAction() {
        return visionDetectedAction;
    }

    public String getVisionDetectedTool() {
        return visionDetectedTool;
    }

    public String getOcrErrorCode() {
        return ocrErrorCode;
    }

    // ==================== Setters ====================

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public void setRegions(List<BoundingBox> regions) {
        this.regions = regions != null ? regions : new ArrayList<BoundingBox>();
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public void setSafetyPowerOff(boolean safetyPowerOff) {
        this.safetyPowerOff = safetyPowerOff;
    }

    public void setVisionDetectedAction(String visionDetectedAction) {
        this.visionDetectedAction = visionDetectedAction != null ? visionDetectedAction : "";
    }

    public void setVisionDetectedTool(String visionDetectedTool) {
        this.visionDetectedTool = visionDetectedTool != null ? visionDetectedTool : "";
    }

    public void setOcrErrorCode(String ocrErrorCode) {
        this.ocrErrorCode = ocrErrorCode != null ? ocrErrorCode : "";
    }

    /**
     * 边界框数据类（归一化坐标 0~1）
     */
    public static class BoundingBox {
        /** 左上角 X 坐标（归一化，0~1） */
        public float x;
        /** 左上角 Y 坐标（归一化，0~1） */
        public float y;
        /** 宽度（归一化，0~1） */
        public float width;
        /** 高度（归一化，0~1） */
        public float height;
        /** 区域标签 */
        public String label;
        /** 该区域的置信度 */
        public float confidence;

        /** 默认构造函数 */
        public BoundingBox() {
            this.x = 0f;
            this.y = 0f;
            this.width = 0f;
            this.height = 0f;
            this.label = "";
            this.confidence = 0.0f;
        }

        /**
         * 全参构造函数
         */
        public BoundingBox(float x, float y, float width, float height,
                           String label, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label != null ? label : "";
            this.confidence = confidence;
        }
    }
}
