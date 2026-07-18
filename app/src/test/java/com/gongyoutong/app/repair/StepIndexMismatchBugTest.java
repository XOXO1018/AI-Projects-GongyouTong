package com.gongyoutong.app.repair;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * DiagnosisResult 步骤索引回归测试
 *
 * 历史 Bug（已修复）：
 * RepairActivity.setupStepsCard() 曾使用行索引 i 访问 stepCheckedStates，
 * 但 initStepCheckedStates() 跳过空行后生成的列表索引与行索引不一致，
 * 导致 IndexOutOfBoundsException。
 *
 * 修复方案：新增独立计数器 checkedIndex，与 stepCheckedStates 正确对齐。
 *
 * 此测试验证修复后使用 checkedIndex 访问 stepCheckedStates 不会越界。
 */
public class StepIndexMismatchBugTest {

    private DiagnosisResult result;

    @Before
    public void setUp() {
        result = new DiagnosisResult();
    }

    /**
     * 验证修复：步骤文本包含空行时，使用 checkedIndex 访问 stepCheckedStates 不会越界
     * 此测试模拟修复后的 setupStepsCard 逻辑
     */
    @Test
    public void testStepIndexWithEmptyLines_fixedApproach() {
        String stepsText = "步骤1\n\n步骤2";
        result.initStepCheckedStates(stepsText);

        assertEquals("stepCheckedStates 应有2个元素", 2, result.getStepCheckedStates().size());

        // 模拟修复后的 setupStepsCard 逻辑：使用 checkedIndex 而非行索引 i
        String[] lines = stepsText.split("\n");
        int checkedIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // 使用 checkedIndex 访问，应不会越界
            assertTrue("checkedIndex=" + checkedIndex + " 应在 stepCheckedStates 范围内",
                    checkedIndex < result.getStepCheckedStates().size());
            assertNotNull(result.getStepCheckedStates().get(checkedIndex));

            checkedIndex++;
        }

        assertEquals("checkedIndex 最终值应等于 stepCheckedStates 大小",
                checkedIndex, result.getStepCheckedStates().size());
    }

    /**
     * 正确的实现方式：使用独立计数器
     */
    @Test
    public void testStepIndexCorrectApproach_withCounter() {
        String stepsText = "步骤1\n\n步骤2\n步骤3";
        result.initStepCheckedStates(stepsText);

        String[] lines = stepsText.split("\n");
        int checkedIndex = 0; // 独立计数器
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // 使用 checkedIndex 而非 i
            if (checkedIndex < result.getStepCheckedStates().size()) {
                boolean checked = result.getStepCheckedStates().get(checkedIndex);
                assertFalse("索引 " + checkedIndex + " 应为 false", checked);
            }
            checkedIndex++;
        }

        assertEquals("遍历的非空行数应等于 stepCheckedStates 大小",
                checkedIndex, result.getStepCheckedStates().size());
    }

    /**
     * 无空行时两种索引方式结果一致
     */
    @Test
    public void testStepIndexNoEmptyLines_worksCorrectly() {
        String stepsText = "步骤1\n步骤2\n步骤3";
        result.initStepCheckedStates(stepsText);

        assertEquals(3, result.getStepCheckedStates().size());

        // 无空行时，行索引 i 与 stepCheckedStates 索引一致
        String[] lines = stepsText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            assertTrue("索引 i=" + i + " 应在范围内", i < result.getStepCheckedStates().size());
        }
    }

    /**
     * 多个连续空行的极端情况：使用 checkedIndex 不会越界
     */
    @Test
    public void testStepIndexMultipleConsecutiveEmptyLines_fixedApproach() {
        String stepsText = "\n\n步骤1\n\n\n步骤2\n\n";
        result.initStepCheckedStates(stepsText);

        assertEquals("只有2个非空行", 2, result.getStepCheckedStates().size());

        // 模拟修复后的逻辑：使用 checkedIndex
        String[] lines = stepsText.split("\n");
        int checkedIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // 使用 checkedIndex 访问，不会越界
            assertTrue("checkedIndex=" + checkedIndex + " 应在范围内",
                    checkedIndex < result.getStepCheckedStates().size());

            checkedIndex++;
        }

        assertEquals("checkedIndex 应等于 stepCheckedStates 大小",
                checkedIndex, result.getStepCheckedStates().size());
    }
}
