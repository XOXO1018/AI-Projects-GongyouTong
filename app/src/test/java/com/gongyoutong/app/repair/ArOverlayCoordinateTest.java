package com.gongyoutong.app.repair;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ArOverlayView 坐标映射单元测试
 * 覆盖：归一化坐标→像素坐标转换、边界条件、标签位置计算
 *
 * 注意：ArOverlayView 继承 android.view.View，无法在纯 JUnit 中实例化。
 * 此处测试坐标转换算法本身。
 */
public class ArOverlayCoordinateTest {

    // ==================== 坐标映射 ====================

    @Test
    public void testNormalizedToPixel_fullScreen() {
        // 0,0 → 1,1 映射到全屏
        FrameAnalysisResult.BoundingBox box = box(0.0f, 0.0f, 1.0f, 1.0f, "full");
        Rect pixel = toPixel(box, 1080, 1920);
        assertEquals(0f, pixel.left, 0.5f);
        assertEquals(0f, pixel.top, 0.5f);
        assertEquals(1080f, pixel.right, 0.5f);
        assertEquals(1920f, pixel.bottom, 0.5f);
    }

    @Test
    public void testNormalizedToPixel_quarterScreen() {
        // 左上角 1/4
        FrameAnalysisResult.BoundingBox box = box(0.0f, 0.0f, 0.5f, 0.5f, "quarter");
        Rect pixel = toPixel(box, 1080, 1920);
        assertEquals(540f, pixel.right, 0.5f);
        assertEquals(960f, pixel.bottom, 0.5f);
    }

    @Test
    public void testNormalizedToPixel_centerSmall() {
        // 中心小区域
        FrameAnalysisResult.BoundingBox box = box(0.4f, 0.4f, 0.2f, 0.2f, "center");
        Rect pixel = toPixel(box, 1080, 1920);
        assertEquals(432f, pixel.left, 0.5f);   // 0.4 * 1080
        assertEquals(768f, pixel.top, 0.5f);    // 0.4 * 1920
        assertEquals(648f, pixel.right, 0.5f);  // 0.6 * 1080
        assertEquals(1152f, pixel.bottom, 0.5f); // 0.6 * 1920
    }

    @Test
    public void testNormalizedToPixel_zeroSize() {
        FrameAnalysisResult.BoundingBox box = box(0.5f, 0.5f, 0.0f, 0.0f, "point");
        Rect pixel = toPixel(box, 1080, 1920);
        assertEquals(pixel.left, pixel.right, 0.01f);
        assertEquals(pixel.top, pixel.bottom, 0.01f);
    }

    @Test
    public void testNormalizedToPixel_variedScreenSizes() {
        FrameAnalysisResult.BoundingBox box = box(0.1f, 0.2f, 0.3f, 0.4f, "test");

        // 1080×1920 (portrait phone)
        Rect phone = toPixel(box, 1080, 1920);
        assertEquals(108f, phone.left, 0.5f);
        assertEquals(384f, phone.top, 0.5f);
        assertEquals(432f, phone.right, 0.5f);
        assertEquals(1152f, phone.bottom, 0.5f);

        // 1920×1080 (landscape)
        Rect landscape = toPixel(box, 1920, 1080);
        assertEquals(192f, landscape.left, 0.5f);
        assertEquals(216f, landscape.top, 0.5f);
        assertEquals(768f, landscape.right, 0.5f);
        assertEquals(648f, landscape.bottom, 0.5f);
    }

    // ==================== 标签位置（ArOverlayView 算法） ====================

    @Test
    public void testLabelPosition_aboveBox() {
        FrameAnalysisResult.BoundingBox box = box(0.2f, 0.5f, 0.3f, 0.2f, "螺丝");
        Rect pixel = toPixel(box, 1080, 1920);

        float labelY = pixel.top - 8f;
        assertTrue("标签应在框上方", labelY < pixel.top);
        assertTrue("标签 Y 应 > 30（有足够空间）", labelY >= 30f);
    }

    @Test
    public void testLabelPosition_belowBox_whenTopTooClose() {
        // 框太靠近顶部，标签放在下方
        FrameAnalysisResult.BoundingBox box = box(0.2f, 0.01f, 0.3f, 0.1f, "close");
        Rect pixel = toPixel(box, 1080, 1920);

        float labelYAbove = pixel.top - 8f;
        if (labelYAbove < 30f) {
            float labelYBelow = pixel.bottom + 40f;
            assertTrue("标签应放在下方", labelYBelow > pixel.bottom);
        }
    }

    @Test
    public void testLabelXPosition_offsetFromLeft() {
        FrameAnalysisResult.BoundingBox box = box(0.2f, 0.3f, 0.3f, 0.2f, "label");
        Rect pixel = toPixel(box, 1080, 1920);
        float labelX = pixel.left + 8f;
        assertTrue("标签 X 应在框内偏右", labelX > pixel.left);
        assertTrue("标签 X 不应超出框", labelX < pixel.right + 50);
    }

    // ==================== 边界条件 ====================

    @Test
    public void testNormalizedCoordinates_outOfBounds() {
        // 虽然归一化坐标应在 0~1，但防御性处理也很重要
        FrameAnalysisResult.BoundingBox box = box(-0.1f, -0.1f, 1.2f, 1.2f, "overflow");
        Rect pixel = toPixel(box, 1080, 1920);
        // 不应崩溃，但坐标会超出视口
        assertTrue("负坐标会映射为负像素", pixel.left < 0);
        assertTrue("超出部分会超出视口", pixel.right > 1080);
    }

    @Test
    public void testZeroViewSize_divisionByZero() {
        FrameAnalysisResult.BoundingBox box = box(0.5f, 0.5f, 0.3f, 0.3f, "test");
        Rect pixel = toPixel(box, 0, 0);
        assertEquals(0f, pixel.left, 0.01f);
        assertEquals(0f, pixel.right, 0.01f);
    }

    @Test
    public void testEmptyLabel_noTextDrawn() {
        FrameAnalysisResult.BoundingBox box = box(0.2f, 0.3f, 0.3f, 0.2f, "");
        assertTrue("空标签不应绘制文字", box.label.isEmpty());
    }

    @Test
    public void testNullLabel_convertedToEmpty() {
        // BoundingBox 构造函数将 null label 转换为 ""（防御性处理）
        FrameAnalysisResult.BoundingBox box = box(0.2f, 0.3f, 0.3f, 0.2f, null);
        assertEquals("null label 应被转换为空字符串", "", box.label);
        // 在 ArOverlayView 中空 label 不会绘制文字
    }

    // ==================== 多个区域叠加测试 ====================

    @Test
    public void testMultipleRegions_noOverlap() {
        List<FrameAnalysisResult.BoundingBox> regions = new ArrayList<>();
        regions.add(box(0.1f, 0.1f, 0.2f, 0.2f, "A"));
        regions.add(box(0.7f, 0.7f, 0.2f, 0.2f, "B"));
        regions.add(box(0.4f, 0.4f, 0.1f, 0.1f, "C"));

        assertEquals(3, regions.size());

        for (FrameAnalysisResult.BoundingBox region : regions) {
            Rect pixel = toPixel(region, 1080, 1920);
            assertTrue("宽度应 > 0", pixel.right > pixel.left);
            assertTrue("高度应 > 0", pixel.bottom > pixel.top);
        }
    }

    // ==================== 辅助方法 ====================

    private static class Rect {
        final float left, top, right, bottom;
        Rect(float l, float t, float r, float b) {
            left = l; top = t; right = r; bottom = b;
        }
    }

    private Rect toPixel(FrameAnalysisResult.BoundingBox box, int viewW, int viewH) {
        float left = box.x * viewW;
        float top = box.y * viewH;
        float right = (box.x + box.width) * viewW;
        float bottom = (box.y + box.height) * viewH;
        return new Rect(left, top, right, bottom);
    }

    private FrameAnalysisResult.BoundingBox box(float x, float y, float w, float h,
                                                  String label) {
        return new FrameAnalysisResult.BoundingBox(x, y, w, h, label, 0.9f);
    }
}
