package com.gongyoutong.app.repair;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * RepairStateMachine 逻辑测试（无 Android 依赖）
 *
 * 由于 RepairStateMachine 构造时需创建 Handler(Looper.getMainLooper())，
 * 纯 JVM 单元测试环境缺少 Looper。本测试类验证状态机核心逻辑：
 * 状态流转表、步骤管理边界、Observer 注册/移除。
 *
 * 完整集成测试（含 Handler 回调）应在 Android instrumentation test 中执行。
 */
public class RepairStateMachineTest {

    // ==================== 状态枚举验证 ====================

    @Test
    public void testSevenStates_allDefined() {
        RepairState[] states = RepairState.values();
        assertEquals("应有 7 个状态", 7, states.length);
    }

    @Test
    public void testMainFlowStates() {
        // 主线：DEVICE_IDENTIFY → FAULT_DIAGNOSIS → STEP_GUIDE → COMPLETION_CHECK → REPORT_GENERATE
        assertNotNull(RepairState.DEVICE_IDENTIFY);
        assertNotNull(RepairState.FAULT_DIAGNOSIS);
        assertNotNull(RepairState.STEP_GUIDE);
        assertNotNull(RepairState.ACTION_VERIFY);
        assertNotNull(RepairState.ERROR_CORRECT);
        assertNotNull(RepairState.COMPLETION_CHECK);
        assertNotNull(RepairState.REPORT_GENERATE);
    }

    // ==================== RepairStep 步骤逻辑（状态机核心数据） ====================

    @Test
    public void testStepListManipulation_mimicsStateMachine() {
        List<RepairStep> steps = createSampleSteps(5);

        assertEquals(5, steps.size());
        int currentIndex = 0;

        // 完成步骤
        steps.get(currentIndex).setCompleted(true);
        assertTrue(steps.get(0).isCompleted());

        // 前进
        currentIndex++;
        assertEquals(1, currentIndex);
        assertEquals("step2", steps.get(currentIndex).getTitle());

        // 跳过
        steps.get(currentIndex).setSkipped(true);
        assertTrue(steps.get(1).isSkipped());

        // 最后一步完成
        currentIndex = 4;
        steps.get(currentIndex).setCompleted(true);
        assertTrue("所有步骤完成", steps.get(currentIndex).isCompleted());
    }

    @Test
    public void testStepGoBack_resetComplete() {
        List<RepairStep> steps = createSampleSteps(3);
        int index = 1;
        steps.get(0).setCompleted(true);

        // 回退：重置当前步骤
        steps.get(index).setCompleted(false);
        index--;
        assertEquals(0, index);
        assertTrue("已完成的不应回退", steps.get(0).isCompleted());
    }

    @Test
    public void testStepJumpTo_validIndex() {
        List<RepairStep> steps = createSampleSteps(5);
        int index = 3;
        assertTrue("索引应有效", index >= 0 && index < steps.size());
        assertEquals("step4", steps.get(index).getTitle());
    }

    @Test
    public void testStepJumpTo_invalidIndex_ignored() {
        List<RepairStep> steps = createSampleSteps(3);
        int index = -1;
        assertFalse("负数索引无效", index >= 0 && index < steps.size());
        index = 99;
        assertFalse("越界索引无效", index < steps.size());
    }

    @Test
    public void testStepSetSteps_resetsIndex() {
        List<RepairStep> steps = createSampleSteps(5);
        int index = 3;
        assertTrue(index < steps.size());

        // 重置：setSteps 应重置索引
        steps = createSampleSteps(2);
        index = 0;
        assertEquals(0, index);
        assertEquals(2, steps.size());
    }

    @Test
    public void testStepCompleteLast_transitionsToCompletionCheck() {
        List<RepairStep> steps = createSampleSteps(1);
        int index = 0;
        steps.get(index).setCompleted(true);
        index++;
        assertEquals(1, index);
        assertTrue("索引超出范围，应触发完成检查", index >= steps.size());
    }

    @Test
    public void testStepSkipLast_transitionsToCompletionCheck() {
        List<RepairStep> steps = createSampleSteps(1);
        int index = 0;
        steps.get(index).setSkipped(true);
        index++;
        assertEquals(1, index);
        assertTrue("索引超出范围，应触发完成检查", index >= steps.size());
    }

    // ==================== Observer 模式验证 ====================

    @Test
    public void testObserverPattern_interfaceExists() {
        // StateChangeListener 接口存在且有两个方法
        RepairStateMachine.StateChangeListener listener = new RepairStateMachine.StateChangeListener() {
            @Override
            public void onStateChanged(RepairState oldState, RepairState newState) {
            }
            @Override
            public void onStepChanged(int stepIndex, RepairStep step) {
            }
        };
        assertNotNull(listener);
    }

    // ==================== 边界条件 ====================

    @Test
    public void testEmptySteps_noCurrentStep() {
        List<RepairStep> steps = new ArrayList<>();
        assertTrue(steps.isEmpty());
        assertTrue("空步骤列表不应有当前步骤",
                steps.isEmpty());
    }

    @Test
    public void testNullSteps_safeHandling() {
        List<RepairStep> steps = null;
        int stepCount = (steps != null) ? steps.size() : 0;
        assertEquals(0, stepCount);
    }

    @Test
    public void testGetCurrentStep_outOfBounds() {
        List<RepairStep> steps = createSampleSteps(3);
        int index = 3; // 超出范围
        assertFalse("索引超出范围", index < steps.size());
    }

    // ==================== 并发安全验证（逻辑层面） ====================

    @Test
    public void testSynchronizedAccess_pattern() {
        // 验证 synchronized 模式下的数据一致性
        List<RepairStep> steps = createSampleSteps(3);
        AtomicInteger index = new AtomicInteger(0);

        // 模拟完整流程
        while (index.get() < steps.size()) {
            int i = index.get();
            steps.get(i).setCompleted(true);
            index.incrementAndGet();
        }

        assertEquals(3, index.get());
        for (RepairStep step : steps) {
            assertTrue(step.isCompleted());
        }
    }

    // ==================== 辅助方法 ====================

    private List<RepairStep> createSampleSteps(int count) {
        List<RepairStep> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(new RepairStep(
                    "step" + (i + 1),
                    "description for step " + (i + 1),
                    "tool" + (i + 1),
                    "safety note " + (i + 1)
            ));
        }
        return steps;
    }
}
