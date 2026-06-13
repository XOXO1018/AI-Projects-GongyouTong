package com.gongyoutong.app.repair;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 诊断结果数据类
 * 包含结构化的诊断建议，由 AI 返回的 JSON 解析而来
 */
public class DiagnosisResult {

    private String faultCauses;          // 可能的故障原因
    private String inspectionSteps;     // 排查步骤
    private String safetyTips;          // 安全提示
    private String requiredTools;       // 所需工具
    private String requiredParts;       // 可能配件
    private String estimatedTime;       // 维修时间预估
    private String rawResponse;         // AI 原始返回
    private boolean isExpanded;         // 展开状态（用于 UI）
    private List<Boolean> stepCheckedStates; // 步骤勾选状态（用于 UI）

    public DiagnosisResult() {
        this.isExpanded = true;
        this.stepCheckedStates = new ArrayList<>();
    }

    // ==================== Getters ====================

    public String getFaultCauses() {
        return faultCauses;
    }

    public String getInspectionSteps() {
        return inspectionSteps;
    }

    public String getSafetyTips() {
        return safetyTips;
    }

    public String getRequiredTools() {
        return requiredTools;
    }

    public String getRequiredParts() {
        return requiredParts;
    }

    public String getEstimatedTime() {
        return estimatedTime;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public List<Boolean> getStepCheckedStates() {
        return stepCheckedStates;
    }

    // ==================== Setters ====================

    public void setFaultCauses(String faultCauses) {
        this.faultCauses = faultCauses;
    }

    public void setInspectionSteps(String inspectionSteps) {
        this.inspectionSteps = inspectionSteps;
    }

    public void setSafetyTips(String safetyTips) {
        this.safetyTips = safetyTips;
    }

    public void setRequiredTools(String requiredTools) {
        this.requiredTools = requiredTools;
    }

    public void setRequiredParts(String requiredParts) {
        this.requiredParts = requiredParts;
    }

    public void setEstimatedTime(String estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public void setStepCheckedStates(List<Boolean> stepCheckedStates) {
        this.stepCheckedStates = stepCheckedStates;
    }

    /**
     * 根据排查步骤文本初始化勾选状态列表
     *
     * @param stepsText 排查步骤文本（按行分割）
     */
    public void initStepCheckedStates(String stepsText) {
        stepCheckedStates = new ArrayList<>();
        if (stepsText != null && !stepsText.isEmpty()) {
            String[] lines = stepsText.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    stepCheckedStates.add(false);
                }
            }
        }
    }
}
