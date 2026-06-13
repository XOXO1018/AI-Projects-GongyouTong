package com.gongyoutong.app.repair;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * DiagnosisResult 单元测试
 * 覆盖：构造函数默认值、getter/setter、initStepCheckedStates 逻辑
 */
public class DiagnosisResultTest {

    private DiagnosisResult result;

    @Before
    public void setUp() {
        result = new DiagnosisResult();
    }

    // ==================== 构造函数默认值 ====================

    @Test
    public void testDefaultExpandedIsTrue() {
        assertTrue("默认展开状态应为 true", result.isExpanded());
    }

    @Test
    public void testDefaultStepCheckedStatesIsEmptyList() {
        assertNotNull("stepCheckedStates 不应为 null", result.getStepCheckedStates());
        assertTrue("默认 stepCheckedStates 应为空列表", result.getStepCheckedStates().isEmpty());
    }

    @Test
    public void testDefaultFieldValuesAreNull() {
        assertNull("默认 faultCauses 应为 null", result.getFaultCauses());
        assertNull("默认 inspectionSteps 应为 null", result.getInspectionSteps());
        assertNull("默认 safetyTips 应为 null", result.getSafetyTips());
        assertNull("默认 requiredTools 应为 null", result.getRequiredTools());
        assertNull("默认 requiredParts 应为 null", result.getRequiredParts());
        assertNull("默认 estimatedTime 应为 null", result.getEstimatedTime());
        assertNull("默认 rawResponse 应为 null", result.getRawResponse());
    }

    // ==================== Getter / Setter ====================

    @Test
    public void testSetAndGetFaultCauses() {
        result.setFaultCauses("压缩机故障 80%");
        assertEquals("压缩机故障 80%", result.getFaultCauses());
    }

    @Test
    public void testSetAndGetInspectionSteps() {
        result.setInspectionSteps("1. 检查电源\n2. 检查压缩机");
        assertEquals("1. 检查电源\n2. 检查压缩机", result.getInspectionSteps());
    }

    @Test
    public void testSetAndGetSafetyTips() {
        result.setSafetyTips("断电操作");
        assertEquals("断电操作", result.getSafetyTips());
    }

    @Test
    public void testSetAndGetRequiredTools() {
        result.setRequiredTools("万用表, 螺丝刀");
        assertEquals("万用表, 螺丝刀", result.getRequiredTools());
    }

    @Test
    public void testSetAndGetRequiredParts() {
        result.setRequiredParts("压缩机, 电容");
        assertEquals("压缩机, 电容", result.getRequiredParts());
    }

    @Test
    public void testSetAndGetEstimatedTime() {
        result.setEstimatedTime("约30-45分钟");
        assertEquals("约30-45分钟", result.getEstimatedTime());
    }

    @Test
    public void testSetAndGetRawResponse() {
        result.setRawResponse("{\"faultCauses\":\"...\"}");
        assertEquals("{\"faultCauses\":\"...\"}", result.getRawResponse());
    }

    @Test
    public void testSetAndGetExpanded() {
        result.setExpanded(false);
        assertFalse(result.isExpanded());
        result.setExpanded(true);
        assertTrue(result.isExpanded());
    }

    @Test
    public void testSetAndGetStepCheckedStates() {
        List<Boolean> states = new ArrayList<>();
        states.add(true);
        states.add(false);
        result.setStepCheckedStates(states);
        assertEquals(2, result.getStepCheckedStates().size());
        assertTrue(result.getStepCheckedStates().get(0));
        assertFalse(result.getStepCheckedStates().get(1));
    }

    // ==================== initStepCheckedStates ====================

    @Test
    public void testInitStepCheckedStates_normalInput() {
        String steps = "1. 检查电源\n2. 检查压缩机\n3. 测量电压";
        result.initStepCheckedStates(steps);
        assertEquals("应生成3个勾选状态", 3, result.getStepCheckedStates().size());
        for (int i = 0; i < result.getStepCheckedStates().size(); i++) {
            assertFalse("所有勾选状态默认应为 false", result.getStepCheckedStates().get(i));
        }
    }

    @Test
    public void testInitStepCheckedStates_withEmptyLines() {
        // 步骤文本中包含空行
        String steps = "1. 检查电源\n\n2. 检查压缩机\n\n3. 测量电压";
        result.initStepCheckedStates(steps);
        assertEquals("空行不应产生勾选状态，应有3个状态", 3, result.getStepCheckedStates().size());
    }

    @Test
    public void testInitStepCheckedStates_nullInput() {
        result.initStepCheckedStates(null);
        assertNotNull(result.getStepCheckedStates());
        assertTrue("null 输入应产生空列表", result.getStepCheckedStates().isEmpty());
    }

    @Test
    public void testInitStepCheckedStates_emptyInput() {
        result.initStepCheckedStates("");
        assertNotNull(result.getStepCheckedStates());
        assertTrue("空字符串应产生空列表", result.getStepCheckedStates().isEmpty());
    }

    @Test
    public void testInitStepCheckedStates_singleLine() {
        result.initStepCheckedStates("检查电源连接");
        assertEquals(1, result.getStepCheckedStates().size());
    }

    @Test
    public void testInitStepCheckedStates_onlyEmptyLines() {
        result.initStepCheckedStates("\n\n\n");
        assertEquals("只有空行时应产生空列表", 0, result.getStepCheckedStates().size());
    }

    @Test
    public void testInitStepCheckedStates_replacesExistingStates() {
        // 先设置一些状态
        List<Boolean> oldStates = new ArrayList<>();
        oldStates.add(true);
        result.setStepCheckedStates(oldStates);
        assertEquals(1, result.getStepCheckedStates().size());

        // 重新初始化应覆盖旧状态
        result.initStepCheckedStates("步骤1\n步骤2");
        assertEquals(2, result.getStepCheckedStates().size());
        assertFalse(result.getStepCheckedStates().get(0));
    }
}
