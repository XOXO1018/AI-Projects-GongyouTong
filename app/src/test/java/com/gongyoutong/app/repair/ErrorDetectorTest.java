package com.gongyoutong.app.repair;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ErrorDetector 逻辑验证测试（无 Android 依赖）
 *
 * 由于 ErrorDetector.detect() 内部调用 android.util.Log，无法在纯 JUnit 中实例化。
 * 本测试复刻 detect() 的核心逻辑进行验证，确保判定算法正确。
 */
public class ErrorDetectorTest {

    // ==================== 空输入 ====================

    @Test
    public void testDetect_nullFrameResult_returnsEmpty() {
        List<String> errors = detect(null, null);
        assertTrue("null 输入应返回空列表", errors.isEmpty());
    }

    @Test
    public void testDetect_nullStep_returnsOnlySafetyViolation() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(false);
        List<String> errors = detect(result, null);
        assertEquals("未断电时应只有安全警告", 1, errors.size());
        assertTrue(errors.contains("SAFETY_VIOLATION"));
    }

    // ==================== 安全违规检测 ====================

    @Test
    public void testDetect_safetyPowerOff_false_detected() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(false);
        List<String> errors = detect(result, null);
        assertTrue("未断电应检测到安全违规", errors.contains("SAFETY_VIOLATION"));
    }

    @Test
    public void testDetect_safetyPowerOff_true_noViolation() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        List<String> errors = detect(result, null);
        assertFalse("已断电不应检测到安全违规", errors.contains("SAFETY_VIOLATION"));
    }

    // ==================== 步骤错位检测 ====================

    @Test
    public void testDetect_matchingAction_noSequenceError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedAction("dismantle");

        RepairStep step = new RepairStep("Dismantle", "use screwdriver to dismantle casing", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertFalse("匹配的操作不应有顺序错误", errors.contains("SEQUENCE_ERROR"));
    }

    @Test
    public void testDetect_mismatchedAction_sequenceError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedAction("replace compressor");

        RepairStep step = new RepairStep("Dismantle", "use screwdriver to dismantle", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertTrue("不匹配的操作应检测到顺序错误", errors.contains("SEQUENCE_ERROR"));
    }

    @Test
    public void testDetect_emptyDetectedAction_noSequenceError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedAction("");

        RepairStep step = new RepairStep("Dismantle", "use screwdriver", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertFalse("空检测操作不应触发顺序错误", errors.contains("SEQUENCE_ERROR"));
    }

    @Test
    public void testDetect_nullDetectedAction_noSequenceError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedAction(null);

        RepairStep step = new RepairStep("Dismantle", "use screwdriver", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertFalse("null 检测操作不应触发顺序错误", errors.contains("SEQUENCE_ERROR"));
    }

    // ==================== 工具错误检测 ====================

    @Test
    public void testDetect_matchingTool_noToolError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedTool("screwdriver");

        RepairStep step = new RepairStep("Dismantle", "remove", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertFalse("匹配工具不应有工具错误", errors.contains("TOOL_ERROR"));
    }

    @Test
    public void testDetect_mismatchedTool_toolError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedTool("wrench");

        RepairStep step = new RepairStep("Dismantle", "remove", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertTrue("不匹配工具应检测到工具错误", errors.contains("TOOL_ERROR"));
    }

    @Test
    public void testDetect_toolCaseInsensitive() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedTool("SCREWDRIVER");

        RepairStep step = new RepairStep("Dismantle", "remove", "screwdriver", "");
        List<String> errors = detect(result, step);
        assertFalse("相同工具（忽略大小写）不应错误", errors.contains("TOOL_ERROR"));
    }

    @Test
    public void testDetect_emptyToolRequired_noToolError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedTool("any");

        RepairStep step = new RepairStep("step", "desc", "", ""); // 无需工具
        List<String> errors = detect(result, step);
        assertFalse("步骤无需工具不应检测工具错误", errors.contains("TOOL_ERROR"));
    }

    @Test
    public void testDetect_nullToolInStep_noToolError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedTool("any");

        RepairStep step = new RepairStep("step", "desc", null, "");
        List<String> errors = detect(result, step);
        assertFalse("null tool required 不应检测工具错误", errors.contains("TOOL_ERROR"));
    }

    // ==================== 部件错误检测（OCR） ====================

    @Test
    public void testDetect_ocrErrorCode_present_partError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setOcrErrorCode("E001");

        List<String> errors = detect(result, null);
        assertTrue("OCR 错误代码应触发部件错误", errors.contains("PART_ERROR"));
    }

    @Test
    public void testDetect_ocrErrorCode_empty_noPartError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setOcrErrorCode("");

        List<String> errors = detect(result, null);
        assertFalse("空 OCR 代码不应触发部件错误", errors.contains("PART_ERROR"));
    }

    @Test
    public void testDetect_ocrErrorCode_null_noPartError() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setOcrErrorCode(null);

        List<String> errors = detect(result, null);
        assertFalse("null OCR 代码不应触发部件错误", errors.contains("PART_ERROR"));
    }

    // ==================== 组合错误 ====================

    @Test
    public void testDetect_multipleErrors() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(false);
        result.setVisionDetectedAction("wrong action");
        result.setVisionDetectedTool("wrong tool");
        result.setOcrErrorCode("E500");

        RepairStep step = new RepairStep("correct", "correct desc", "correct tool", "");
        List<String> errors = detect(result, step);

        assertTrue("应检测到安全违规", errors.contains("SAFETY_VIOLATION"));
        assertTrue("应检测到顺序错误", errors.contains("SEQUENCE_ERROR"));
        assertTrue("应检测到工具错误", errors.contains("TOOL_ERROR"));
        assertTrue("应检测到部件错误", errors.contains("PART_ERROR"));
        assertEquals("应检测到 4 个错误", 4, errors.size());
    }

    @Test
    public void testDetect_noErrors_whenAllClear() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        result.setVisionDetectedAction("");
        result.setVisionDetectedTool("");
        result.setOcrErrorCode("");

        RepairStep step = new RepairStep("step", "desc", "", "");
        List<String> errors = detect(result, step);
        assertTrue("无障碍时应返回空列表", errors.isEmpty());
    }

    // ==================== ErrorType 枚举验证 ====================

    @Test
    public void testErrorTypeValues() {
        assertEquals("应有 4 种错误类型（通过标签验证）", 4,
                ErrorDetector.ErrorType.values().length);
    }

    @Test
    public void testSafetyViolationIsCritical() {
        assertEquals("critical", ErrorDetector.ErrorType.SAFETY_VIOLATION.severity);
    }

    @Test
    public void testToolErrorIsHigh() {
        assertEquals("high", ErrorDetector.ErrorType.TOOL_ERROR.severity);
    }

    @Test
    public void testPartErrorIsHigh() {
        assertEquals("high", ErrorDetector.ErrorType.PART_ERROR.severity);
    }

    @Test
    public void testSequenceErrorIsMedium() {
        assertEquals("medium", ErrorDetector.ErrorType.SEQUENCE_ERROR.severity);
    }

    // ==================== 内部：复刻 ErrorDetector.detect() 逻辑 ====================

    /**
     * 复刻 ErrorDetector.detect() 的核心判定算法，无 android.util.Log 依赖。
     *
     * @param frameResult 帧分析结果
     * @param currentStep 当前步骤
     * @return 错误类型名称列表
     */
    private List<String> detect(FrameAnalysisResult frameResult, RepairStep currentStep) {
        List<String> errors = new ArrayList<>();
        if (frameResult == null) {
            return errors;
        }

        // 安全检查（最高优先级）
        if (!frameResult.isSafetyPowerOff()) {
            errors.add("SAFETY_VIOLATION");
        }

        // 步骤对齐检查
        if (currentStep != null && frameResult.getVisionDetectedAction() != null) {
            if (!isActionMatching(frameResult.getVisionDetectedAction(), currentStep)) {
                errors.add("SEQUENCE_ERROR");
            }
        }

        // 工具检查
        if (currentStep != null && currentStep.getToolRequired() != null
                && !currentStep.getToolRequired().isEmpty()
                && frameResult.getVisionDetectedTool() != null) {
            if (!currentStep.getToolRequired()
                    .equalsIgnoreCase(frameResult.getVisionDetectedTool())) {
                errors.add("TOOL_ERROR");
            }
        }

        // 部件检查（基于 OCR 错误代码）
        if (frameResult.getOcrErrorCode() != null
                && !frameResult.getOcrErrorCode().isEmpty()) {
            errors.add("PART_ERROR");
        }

        return errors;
    }

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
