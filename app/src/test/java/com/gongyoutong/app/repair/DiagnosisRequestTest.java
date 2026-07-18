package com.gongyoutong.app.repair;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * DiagnosisRequest 单元测试
 * 覆盖：构造函数默认值、addPhoto、addHistoryMessage、getter/setter
 */
public class DiagnosisRequestTest {

    private DiagnosisRequest request;

    @Before
    public void setUp() {
        request = new DiagnosisRequest();
    }

    // ==================== 构造函数默认值 ====================

    @Test
    public void testDefaultPhotoBase64ListIsEmpty() {
        assertNotNull("photoBase64List 不应为 null", request.getPhotoBase64List());
        assertTrue("默认 photoBase64List 应为空列表", request.getPhotoBase64List().isEmpty());
    }

    @Test
    public void testDefaultHistoryMessagesIsEmpty() {
        assertNotNull("historyMessages 不应为 null", request.getHistoryMessages());
        assertTrue("默认 historyMessages 应为空列表", request.getHistoryMessages().isEmpty());
    }

    @Test
    public void testDefaultFieldValuesAreNull() {
        assertNull("默认 faultDescription 应为 null", request.getFaultDescription());
        assertNull("默认 workType 应为 null", request.getWorkType());
        assertNull("默认 workOrderTitle 应为 null", request.getWorkOrderTitle());
    }

    // ==================== Getter / Setter ====================

    @Test
    public void testSetAndGetFaultDescription() {
        request.setFaultDescription("空调不制冷");
        assertEquals("空调不制冷", request.getFaultDescription());
    }

    @Test
    public void testSetAndGetWorkType() {
        request.setWorkType("维修服务");
        assertEquals("维修服务", request.getWorkType());
    }

    @Test
    public void testSetAndGetWorkOrderTitle() {
        request.setWorkOrderTitle("空调故障维修");
        assertEquals("空调故障维修", request.getWorkOrderTitle());
    }

    @Test
    public void testSetPhotoBase64List() {
        List<String> photos = new ArrayList<>();
        photos.add("base64data1");
        photos.add("base64data2");
        request.setPhotoBase64List(photos);
        assertEquals(2, request.getPhotoBase64List().size());
    }

    @Test
    public void testSetHistoryMessages() {
        List<DiagnosisRequest.DiagnosisHistoryMessage> messages = new ArrayList<>();
        messages.add(new DiagnosisRequest.DiagnosisHistoryMessage("user", "测试"));
        request.setHistoryMessages(messages);
        assertEquals(1, request.getHistoryMessages().size());
    }

    // ==================== addPhoto ====================

    @Test
    public void testAddPhoto() {
        request.addPhoto("base64data1");
        assertEquals(1, request.getPhotoBase64List().size());
        assertEquals("base64data1", request.getPhotoBase64List().get(0));
    }

    @Test
    public void testAddPhotoMultiple() {
        request.addPhoto("data1");
        request.addPhoto("data2");
        request.addPhoto("data3");
        assertEquals(3, request.getPhotoBase64List().size());
    }

    @Test
    public void testAddPhotoAfterSetNull() {
        request.setPhotoBase64List(null);
        request.addPhoto("data1");
        assertNotNull("addPhoto 应自动创建列表", request.getPhotoBase64List());
        assertEquals(1, request.getPhotoBase64List().size());
    }

    // ==================== addHistoryMessage ====================

    @Test
    public void testAddHistoryMessage() {
        request.addHistoryMessage("user", "空调不制冷");
        assertEquals(1, request.getHistoryMessages().size());
        assertEquals("user", request.getHistoryMessages().get(0).getRole());
        assertEquals("空调不制冷", request.getHistoryMessages().get(0).getContent());
    }

    @Test
    public void testAddHistoryMessageAssistant() {
        request.addHistoryMessage("assistant", "建议检查压缩机");
        assertEquals(1, request.getHistoryMessages().size());
        assertEquals("assistant", request.getHistoryMessages().get(0).getRole());
    }

    @Test
    public void testAddHistoryMessageAfterSetNull() {
        request.setHistoryMessages(null);
        request.addHistoryMessage("user", "测试");
        assertNotNull("addHistoryMessage 应自动创建列表", request.getHistoryMessages());
        assertEquals(1, request.getHistoryMessages().size());
    }

    // ==================== DiagnosisHistoryMessage ====================

    @Test
    public void testHistoryMessageConstructor() {
        DiagnosisRequest.DiagnosisHistoryMessage msg =
                new DiagnosisRequest.DiagnosisHistoryMessage("user", "content");
        assertEquals("user", msg.getRole());
        assertEquals("content", msg.getContent());
    }

    @Test
    public void testHistoryMessageDefaultConstructor() {
        DiagnosisRequest.DiagnosisHistoryMessage msg =
                new DiagnosisRequest.DiagnosisHistoryMessage();
        assertNull(msg.getRole());
        assertNull(msg.getContent());
    }

    @Test
    public void testHistoryMessageSetters() {
        DiagnosisRequest.DiagnosisHistoryMessage msg =
                new DiagnosisRequest.DiagnosisHistoryMessage();
        msg.setRole("assistant");
        msg.setContent("诊断结果");
        assertEquals("assistant", msg.getRole());
        assertEquals("诊断结果", msg.getContent());
    }
}
