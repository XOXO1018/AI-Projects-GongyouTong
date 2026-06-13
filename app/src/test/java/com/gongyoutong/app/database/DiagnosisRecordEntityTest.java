package com.gongyoutong.app.database;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DiagnosisRecordEntity 单元测试
 * 覆盖：构造函数、getter/setter、ID 格式
 */
public class DiagnosisRecordEntityTest {

    private DiagnosisRecordEntity entity;

    @Before
    public void setUp() {
        entity = new DiagnosisRecordEntity();
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull("默认构造函数应创建对象", entity);
    }

    @Test
    public void testSetAndGetId() {
        entity.setId("diag_WO001_1700000000000");
        assertEquals("diag_WO001_1700000000000", entity.getId());
    }

    @Test
    public void testSetAndGetWorkOrderId() {
        entity.setWorkOrderId("WO001");
        assertEquals("WO001", entity.getWorkOrderId());
    }

    @Test
    public void testSetAndGetRound() {
        entity.setRound(1);
        assertEquals(1, entity.getRound());
        entity.setRound(2);
        assertEquals(2, entity.getRound());
    }

    @Test
    public void testSetAndGetFaultDescription() {
        entity.setFaultDescription("空调不制冷");
        assertEquals("空调不制冷", entity.getFaultDescription());
    }

    @Test
    public void testSetAndGetPhotoPaths() {
        entity.setPhotoPaths("/path1.jpg,/path2.jpg");
        assertEquals("/path1.jpg,/path2.jpg", entity.getPhotoPaths());
    }

    @Test
    public void testSetAndGetDiagnosisResult() {
        entity.setDiagnosisResult("{\"faultCauses\":\"压缩机故障\"}");
        assertEquals("{\"faultCauses\":\"压缩机故障\"}", entity.getDiagnosisResult());
    }

    @Test
    public void testSetAndGetRawAiResponse() {
        entity.setRawAiResponse("AI原始返回文本");
        assertEquals("AI原始返回文本", entity.getRawAiResponse());
    }

    @Test
    public void testSetAndGetAiSource() {
        entity.setAiSource("CLOUD_AI");
        assertEquals("CLOUD_AI", entity.getAiSource());
    }

    @Test
    public void testSetAndGetCreatedAt() {
        long timestamp = System.currentTimeMillis();
        entity.setCreatedAt(timestamp);
        assertEquals(timestamp, entity.getCreatedAt());
    }

    @Test
    public void testIdFormat() {
        // 验证 RepairActivity 中使用的 ID 格式
        String workOrderId = "WO20250101001";
        long timestamp = 1700000000000L;
        String id = "diag_" + workOrderId + "_" + timestamp;
        entity.setId(id);
        assertTrue("ID 应以 diag_ 开头", id.startsWith("diag_"));
        assertTrue("ID 应包含工单号", id.contains(workOrderId));
        assertEquals(id, entity.getId());
    }

    @Test
    public void testPhotoPathsCommaSeparated() {
        // 验证逗号分隔的照片路径格式
        String paths = "/data/photo1.jpg,/data/photo2.jpg,/data/photo3.jpg";
        entity.setPhotoPaths(paths);
        String[] pathArray = entity.getPhotoPaths().split(",");
        assertEquals("3个路径应分为3段", 3, pathArray.length);
    }

    @Test
    public void testAiSourceValues() {
        // 验证合法的 aiSource 值
        entity.setAiSource("CLOUD_AI");
        assertEquals("CLOUD_AI", entity.getAiSource());

        entity.setAiSource("LOCAL_FALLBACK");
        assertEquals("LOCAL_FALLBACK", entity.getAiSource());
    }
}
