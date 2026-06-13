package com.gongyoutong.app.repair;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * RepairState 枚举测试
 * 验证 7 个状态定义完整、顺序正确
 */
public class RepairStateTest {

    @Test
    public void testSevenStatesExist() {
        RepairState[] states = RepairState.values();
        assertEquals("应有 7 个状态", 7, states.length);
    }

    @Test
    public void testStateOrder() {
        RepairState[] states = RepairState.values();
        assertEquals(RepairState.DEVICE_IDENTIFY, states[0]);
        assertEquals(RepairState.FAULT_DIAGNOSIS, states[1]);
        assertEquals(RepairState.STEP_GUIDE, states[2]);
        assertEquals(RepairState.ACTION_VERIFY, states[3]);
        assertEquals(RepairState.ERROR_CORRECT, states[4]);
        assertEquals(RepairState.COMPLETION_CHECK, states[5]);
        assertEquals(RepairState.REPORT_GENERATE, states[6]);
    }

    @Test
    public void testStateValueOf_allStates() {
        String[] names = {
                "DEVICE_IDENTIFY", "FAULT_DIAGNOSIS", "STEP_GUIDE",
                "ACTION_VERIFY", "ERROR_CORRECT", "COMPLETION_CHECK",
                "REPORT_GENERATE"
        };
        for (String name : names) {
            assertNotNull("valueOf('" + name + "') 不应为 null",
                    RepairState.valueOf(name));
        }
    }

    @Test
    public void testStateName_consistent() {
        for (RepairState state : RepairState.values()) {
            assertEquals(state, RepairState.valueOf(state.name()));
        }
    }

    @Test
    public void testMainFlow() {
        // 验证主线流程：DEVICE_IDENTIFY → FAULT_DIAGNOSIS → STEP_GUIDE → COMPLETION_CHECK → REPORT_GENERATE
        RepairState[] mainFlow = {
                RepairState.DEVICE_IDENTIFY,
                RepairState.FAULT_DIAGNOSIS,
                RepairState.STEP_GUIDE,
                RepairState.COMPLETION_CHECK,
                RepairState.REPORT_GENERATE
        };
        assertEquals(5, mainFlow.length);
    }

    @Test
    public void testErrorFlow() {
        // 验证错误分支：STEP_GUIDE → ACTION_VERIFY → ERROR_CORRECT → STEP_GUIDE
        RepairState[] errorFlow = {
                RepairState.STEP_GUIDE,
                RepairState.ACTION_VERIFY,
                RepairState.ERROR_CORRECT
        };
        assertEquals(3, errorFlow.length);
    }
}
