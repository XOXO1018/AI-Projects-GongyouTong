package com.gongyoutong.app.repair;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * FrameAnalysisResult / BoundingBox 单元测试
 * 覆盖：默认值、getter/setter、BoundingBox 坐标、空值安全
 */
public class FrameAnalysisResultTest {

    // ==================== FrameAnalysisResult 默认值 ====================

    @Test
    public void testDefaultConstructor_allDefaults() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        assertEquals("默认 description 应为空字符串", "", result.getDescription());
        assertNotNull("默认 regions 不应为 null", result.getRegions());
        assertTrue("默认 regions 应为空列表", result.getRegions().isEmpty());
        assertEquals("默认 confidence 应为 0", 0.0f, result.getConfidence(), 0.001f);
        assertFalse("默认 safetyPowerOff 应为 false", result.isSafetyPowerOff());
        assertEquals("默认 visionDetectedAction 应为空", "", result.getVisionDetectedAction());
        assertEquals("默认 visionDetectedTool 应为空", "", result.getVisionDetectedTool());
        assertEquals("默认 ocrErrorCode 应为空", "", result.getOcrErrorCode());
    }

    @Test
    public void testParameterizedConstructor() {
        List<FrameAnalysisResult.BoundingBox> boxes = new ArrayList<>();
        boxes.add(new FrameAnalysisResult.BoundingBox(0.1f, 0.2f, 0.3f, 0.4f, "part", 0.9f));

        FrameAnalysisResult result = new FrameAnalysisResult("场景描述", boxes, 0.85f);
        assertEquals("场景描述", result.getDescription());
        assertEquals(1, result.getRegions().size());
        assertEquals(0.85f, result.getConfidence(), 0.001f);
    }

    // ==================== FrameAnalysisResult Setters ====================

    @Test
    public void testSetDescription_valid() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setDescription("主板损坏");
        assertEquals("主板损坏", result.getDescription());
    }

    @Test
    public void testSetDescription_null_usesEmpty() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setDescription("test");
        result.setDescription(null);
        assertEquals("设置 null 应使用空字符串", "", result.getDescription());
    }

    @Test
    public void testSetConfidence_valid() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setConfidence(0.95f);
        assertEquals(0.95f, result.getConfidence(), 0.001f);
    }

    @Test
    public void testSetConfidence_boundaryValues() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setConfidence(0.0f);
        assertEquals(0.0f, result.getConfidence(), 0.001f);
        result.setConfidence(1.0f);
        assertEquals(1.0f, result.getConfidence(), 0.001f);
    }

    @Test
    public void testSetSafetyPowerOff() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setSafetyPowerOff(true);
        assertTrue(result.isSafetyPowerOff());
        result.setSafetyPowerOff(false);
        assertFalse(result.isSafetyPowerOff());
    }

    @Test
    public void testSetRegions_null_usesEmptyList() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setRegions(null);
        assertNotNull("null regions 应转换为空列表", result.getRegions());
        assertTrue(result.getRegions().isEmpty());
    }

    @Test
    public void testSetVisionDetectedAction_null_usesEmpty() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setVisionDetectedAction(null);
        assertEquals("", result.getVisionDetectedAction());
    }

    @Test
    public void testSetVisionDetectedTool_null_usesEmpty() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setVisionDetectedTool(null);
        assertEquals("", result.getVisionDetectedTool());
    }

    @Test
    public void testSetOcrErrorCode_null_usesEmpty() {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setOcrErrorCode(null);
        assertEquals("", result.getOcrErrorCode());
    }

    // ==================== BoundingBox 测试 ====================

    @Test
    public void testBoundingBox_defaultConstructor() {
        FrameAnalysisResult.BoundingBox box = new FrameAnalysisResult.BoundingBox();
        assertEquals(0f, box.x, 0.001f);
        assertEquals(0f, box.y, 0.001f);
        assertEquals(0f, box.width, 0.001f);
        assertEquals(0f, box.height, 0.001f);
        assertEquals("", box.label);
        assertEquals(0.0f, box.confidence, 0.001f);
    }

    @Test
    public void testBoundingBox_parameterizedConstructor() {
        FrameAnalysisResult.BoundingBox box = new FrameAnalysisResult.BoundingBox(
                0.1f, 0.2f, 0.3f, 0.4f, "screw", 0.95f);
        assertEquals(0.1f, box.x, 0.001f);
        assertEquals(0.2f, box.y, 0.001f);
        assertEquals(0.3f, box.width, 0.001f);
        assertEquals(0.4f, box.height, 0.001f);
        assertEquals("screw", box.label);
        assertEquals(0.95f, box.confidence, 0.001f);
    }

    @Test
    public void testBoundingBox_parameterizedConstructor_nullLabel() {
        FrameAnalysisResult.BoundingBox box = new FrameAnalysisResult.BoundingBox(
                0.1f, 0.2f, 0.3f, 0.4f, null, 0.8f);
        assertEquals("null label 应转为空字符串", "", box.label);
    }

    @Test
    public void testBoundingBox_coordinates_normalizedRange() {
        // 归一化坐标应在 0~1 范围内
        FrameAnalysisResult.BoundingBox box = new FrameAnalysisResult.BoundingBox(
                0.0f, 0.0f, 1.0f, 1.0f, "full", 1.0f);
        assertEquals(0.0f, box.x, 0.001f);
        assertEquals(0.0f, box.y, 0.001f);
        assertEquals(1.0f, box.width, 0.001f);
        assertEquals(1.0f, box.height, 0.001f);
    }

    @Test
    public void testBoundingBox_pixelCoordinateConversion() {
        // 模拟 ArOverlayView 的归一化 → 像素转换
        FrameAnalysisResult.BoundingBox box = new FrameAnalysisResult.BoundingBox(
                0.25f, 0.3f, 0.5f, 0.4f, "target", 0.9f);

        int viewW = 1080;
        int viewH = 1920;

        float left = box.x * viewW;
        float top = box.y * viewH;
        float right = (box.x + box.width) * viewW;
        float bottom = (box.y + box.height) * viewH;

        assertEquals("left = 0.25*1080", 270f, left, 0.5f);
        assertEquals("top = 0.3*1920", 576f, top, 0.5f);
        assertEquals("right = 0.75*1080", 810f, right, 0.5f);
        assertEquals("bottom = 0.7*1920", 1344f, bottom, 0.5f);
    }
}
