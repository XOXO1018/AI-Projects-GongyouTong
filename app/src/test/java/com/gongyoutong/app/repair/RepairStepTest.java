package com.gongyoutong.app.repair;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * RepairStep 单元测试
 * 覆盖：默认值、getter/setter、全参构造函数、null 安全
 */
public class RepairStepTest {

    // ==================== 默认构造函数 ====================

    @Test
    public void testDefaultConstructor_allEmpty() {
        RepairStep step = new RepairStep();
        assertEquals("默认 title 应为空字符串", "", step.getTitle());
        assertEquals("默认 description 应为空字符串", "", step.getDescription());
        assertEquals("默认 toolRequired 应为空字符串", "", step.getToolRequired());
        assertEquals("默认 safetyNote 应为空字符串", "", step.getSafetyNote());
        assertFalse("默认 completed 应为 false", step.isCompleted());
        assertFalse("默认 skipped 应为 false", step.isSkipped());
    }

    // ==================== 全参构造函数 ====================

    @Test
    public void testParameterizedConstructor() {
        RepairStep step = new RepairStep("拆卸外壳", "使用螺丝刀拆卸", "螺丝刀", "断电操作");
        assertEquals("拆卸外壳", step.getTitle());
        assertEquals("使用螺丝刀拆卸", step.getDescription());
        assertEquals("螺丝刀", step.getToolRequired());
        assertEquals("断电操作", step.getSafetyNote());
        assertFalse("completed 应为 false", step.isCompleted());
        assertFalse("skipped 应为 false", step.isSkipped());
    }

    @Test
    public void testParameterizedConstructor_nullValues_useEmpty() {
        RepairStep step = new RepairStep(null, null, null, null);
        assertEquals("", step.getTitle());
        assertEquals("", step.getDescription());
        assertEquals("", step.getToolRequired());
        assertEquals("", step.getSafetyNote());
    }

    // ==================== Setters ====================

    @Test
    public void testSetTitle_null_usesEmpty() {
        RepairStep step = new RepairStep();
        step.setTitle("test");
        step.setTitle(null);
        assertEquals("", step.getTitle());
    }

    @Test
    public void testSetDescription_null_usesEmpty() {
        RepairStep step = new RepairStep();
        step.setDescription(null);
        assertEquals("", step.getDescription());
    }

    @Test
    public void testSetToolRequired_null_usesEmpty() {
        RepairStep step = new RepairStep();
        step.setToolRequired(null);
        assertEquals("", step.getToolRequired());
    }

    @Test
    public void testSetSafetyNote_null_usesEmpty() {
        RepairStep step = new RepairStep();
        step.setSafetyNote(null);
        assertEquals("", step.getSafetyNote());
    }

    @Test
    public void testSetCompleted() {
        RepairStep step = new RepairStep();
        step.setCompleted(true);
        assertTrue(step.isCompleted());
        step.setCompleted(false);
        assertFalse(step.isCompleted());
    }

    @Test
    public void testSetSkipped() {
        RepairStep step = new RepairStep();
        step.setSkipped(true);
        assertTrue(step.isSkipped());
        step.setSkipped(false);
        assertFalse(step.isSkipped());
    }

    // ==================== 综合测试 ====================

    @Test
    public void testCompleteStepLifecycle() {
        RepairStep step = new RepairStep("安全检查", "断开电源", "无", "必须断电");

        // 初始状态
        assertFalse(step.isCompleted());
        assertFalse(step.isSkipped());

        // 完成步骤
        step.setCompleted(true);
        assertTrue(step.isCompleted());

        // 跳过步骤
        step.setSkipped(true);
        assertTrue(step.isSkipped());
        assertTrue("completed 和 skipped 可同时为 true", step.isCompleted());
    }

    @Test
    public void testRepairStep_gettersConsistent() {
        RepairStep step = new RepairStep("A", "B", "C", "D");
        assertEquals("A", step.getTitle());
        assertEquals("B", step.getDescription());
        assertEquals("C", step.getToolRequired());
        assertEquals("D", step.getSafetyNote());

        step.setTitle("X");
        assertEquals("X", step.getTitle());
        // 其他字段不应被修改
        assertEquals("B", step.getDescription());
    }
}
