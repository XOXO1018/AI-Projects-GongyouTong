package com.gongyoutong.app.repair;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 错误检测器
 * 根据帧分析结果和当前步骤检测各类操作错误，包括安全违规、步骤错位、工具错误等
 */
public class ErrorDetector {

    private static final String TAG = "ErrorDetector";

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /** 工具错误 — 使用了错误的工具 */
        TOOL_ERROR("工具错误", "high"),
        /** 部件错误 — 使用了错误的部件 */
        PART_ERROR("部件错误", "high"),
        /** 顺序错误 — 操作顺序与指导步骤不匹配 */
        SEQUENCE_ERROR("顺序错误", "medium"),
        /** 安全警告 — 违反安全规范 */
        SAFETY_VIOLATION("安全警告", "critical");

        /** 中文标签 */
        public final String label;

        /** 严重程度：critical / high / medium / low */
        public final String severity;

        ErrorType(String label, String severity) {
            this.label = label;
            this.severity = severity;
        }
    }

    /**
     * 检测所有可能的错误
     *
     * @param frameResult 帧分析结果
     * @param currentStep 当前指导步骤
     * @return 检测到的错误列表，空列表表示无错误
     */
    public List<ErrorType> detect(FrameAnalysisResult frameResult, RepairStep currentStep) {
        List<ErrorType> errors = new ArrayList<>();
        if (frameResult == null) {
            return errors;
        }

        // 安全检查（最高优先级）
        if (!frameResult.isSafetyPowerOff()) {
            errors.add(ErrorType.SAFETY_VIOLATION);
            Log.w(TAG, "检测到安全违规：未断电操作");
        }

        // 步骤对齐检查
        if (currentStep != null && frameResult.getVisionDetectedAction() != null) {
            if (!isActionMatching(frameResult.getVisionDetectedAction(), currentStep)) {
                errors.add(ErrorType.SEQUENCE_ERROR);
                Log.w(TAG, "检测到顺序错误：检测操作=" + frameResult.getVisionDetectedAction()
                        + "，当前步骤=" + currentStep.getTitle());
            }
        }

        // 工具检查
        if (currentStep != null && currentStep.getToolRequired() != null
                && !currentStep.getToolRequired().isEmpty()
                && frameResult.getVisionDetectedTool() != null) {
            if (!currentStep.getToolRequired()
                    .equalsIgnoreCase(frameResult.getVisionDetectedTool())) {
                errors.add(ErrorType.TOOL_ERROR);
                Log.w(TAG, "检测到工具错误：期望=" + currentStep.getToolRequired()
                        + "，实际=" + frameResult.getVisionDetectedTool());
            }
        }

        // 部件检查（基于 OCR 错误代码）
        if (frameResult.getOcrErrorCode() != null
                && !frameResult.getOcrErrorCode().isEmpty()) {
            errors.add(ErrorType.PART_ERROR);
            Log.w(TAG, "检测到部件错误：错误代码=" + frameResult.getOcrErrorCode());
        }

        return errors;
    }

    /**
     * 检查检测到的操作是否与当前步骤匹配
     * 使用简单关键词匹配：步骤描述中包含检测到的操作关键词即视为匹配
     *
     * @param detectedAction 视觉分析检测到的操作
     * @param step           当前步骤
     * @return 是否匹配
     */
    private boolean isActionMatching(String detectedAction, RepairStep step) {
        if (detectedAction == null || step == null) {
            return false;
        }
        String description = step.getDescription();
        if (description == null) {
            return false;
        }
        return description.contains(detectedAction);
    }
}
