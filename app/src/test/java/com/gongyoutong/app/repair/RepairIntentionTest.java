package com.gongyoutong.app.repair;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * RepairIntention 单元测试
 * 覆盖：默认值、getter/setter、所有 Type 枚举值、null 安全
 */
public class RepairIntentionTest {

    // ==================== 默认构造函数 ====================

    @Test
    public void testDefaultConstructor() {
        RepairIntention intention = new RepairIntention();
        assertEquals("默认类型应为 OTHER", RepairIntention.Type.OTHER, intention.getType());
        assertEquals("默认置信度应为 0", 0.0f, intention.getConfidence(), 0.001f);
        assertEquals("默认 rawText 应为空", "", intention.getRawText());
    }

    // ==================== 参数化构造函数 ====================

    @Test
    public void testParameterizedConstructor() {
        RepairIntention intention = new RepairIntention(
                RepairIntention.Type.ASK_GUIDANCE, 0.95f, "怎么修");
        assertEquals(RepairIntention.Type.ASK_GUIDANCE, intention.getType());
        assertEquals(0.95f, intention.getConfidence(), 0.001f);
        assertEquals("怎么修", intention.getRawText());
    }

    @Test
    public void testParameterizedConstructor_nullType_defaultsToOther() {
        RepairIntention intention = new RepairIntention(null, 0.8f, "text");
        assertEquals(RepairIntention.Type.OTHER, intention.getType());
    }

    @Test
    public void testParameterizedConstructor_nullRawText_defaultsToEmpty() {
        RepairIntention intention = new RepairIntention(
                RepairIntention.Type.REPORT_PROGRESS, 0.7f, null);
        assertEquals("", intention.getRawText());
    }

    // ==================== Setters ====================

    @Test
    public void testSetType() {
        RepairIntention intention = new RepairIntention();
        intention.setType(RepairIntention.Type.ASK_TOOL);
        assertEquals(RepairIntention.Type.ASK_TOOL, intention.getType());
    }

    @Test
    public void testSetType_null_defaultsToOther() {
        RepairIntention intention = new RepairIntention();
        intention.setType(RepairIntention.Type.CONFIRM_COMPLETE);
        intention.setType(null);
        assertEquals("null type 应转为 OTHER", RepairIntention.Type.OTHER, intention.getType());
    }

    @Test
    public void testSetConfidence() {
        RepairIntention intention = new RepairIntention();
        intention.setConfidence(0.88f);
        assertEquals(0.88f, intention.getConfidence(), 0.001f);
    }

    @Test
    public void testSetRawText_null_usesEmpty() {
        RepairIntention intention = new RepairIntention();
        intention.setRawText(null);
        assertEquals("", intention.getRawText());
    }

    // ==================== Type 枚举 ====================

    @Test
    public void testTypeEnum_allValuesPresent() {
        RepairIntention.Type[] types = RepairIntention.Type.values();
        assertEquals("应有 6 种意图类型", 6, types.length);
    }

    @Test
    public void testTypeEnum_valueOf() {
        assertEquals(RepairIntention.Type.ASK_GUIDANCE,
                RepairIntention.Type.valueOf("ASK_GUIDANCE"));
        assertEquals(RepairIntention.Type.REPORT_PROGRESS,
                RepairIntention.Type.valueOf("REPORT_PROGRESS"));
        assertEquals(RepairIntention.Type.ASK_TOOL,
                RepairIntention.Type.valueOf("ASK_TOOL"));
        assertEquals(RepairIntention.Type.REPORT_ISSUE,
                RepairIntention.Type.valueOf("REPORT_ISSUE"));
        assertEquals(RepairIntention.Type.CONFIRM_COMPLETE,
                RepairIntention.Type.valueOf("CONFIRM_COMPLETE"));
        assertEquals(RepairIntention.Type.OTHER,
                RepairIntention.Type.valueOf("OTHER"));
    }

    @Test
    public void testTypeEnum_caseSensitive() {
        try {
            RepairIntention.Type.valueOf("ask_guidance");
            fail("valueOf 应区分大小写，应抛出异常");
        } catch (IllegalArgumentException e) {
            // 预期：大小写不匹配
        }
    }

    // ==================== 边界条件 ====================

    @Test
    public void testConfidence_boundary() {
        RepairIntention intention = new RepairIntention();
        intention.setConfidence(0.0f);
        assertEquals(0.0f, intention.getConfidence(), 0.001f);
        intention.setConfidence(1.0f);
        assertEquals(1.0f, intention.getConfidence(), 0.001f);
    }

    @Test
    public void testAllTypes_roundtrip() {
        for (RepairIntention.Type type : RepairIntention.Type.values()) {
            RepairIntention intention = new RepairIntention(type, 0.5f, "test");
            assertEquals(type, intention.getType());
            assertEquals(0.5f, intention.getConfidence(), 0.001f);
        }
    }
}
