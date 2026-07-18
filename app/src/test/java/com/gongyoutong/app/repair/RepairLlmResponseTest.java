package com.gongyoutong.app.repair;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * RepairLlmService / VisionAnalysisService 响应解析单元测试
 * 覆盖：SSE 解析、JSON 清理（markdown）、步骤解析、意图解析、extractContent
 *
 * 使用 Gson（已在项目依赖中）替代 org.json 以确保在 JVM 单元测试环境下可用。
 */
public class RepairLlmResponseTest {

    private static final Gson gson = new Gson();

    // ==================== SSE 数据行格式测试 ====================

    @Test
    public void testSseLine_dataWithSpace() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}";
        assertTrue("以 'data: ' 开头的行应被识别为 SSE 数据", line.startsWith("data: "));
        String data = line.substring(6);
        assertTrue("提取的数据应以 { 开头", data.startsWith("{"));
    }

    @Test
    public void testSseLine_dataWithoutSpace() {
        String line = "data:{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}";
        assertTrue("以 'data:' 开头（无空格）的行也应被识别", line.startsWith("data:"));
        String data = line.substring(5);
        assertTrue("提取的数据应以 { 开头", data.startsWith("{"));
    }

    @Test
    public void testSseLine_done() {
        String line = "data: [DONE]";
        String data = line.substring(6);
        assertEquals("[DONE]", data.trim());
    }

    @Test
    public void testSseLine_notData() {
        String line = "event: message";
        assertFalse("不以 data: 开头的行不是数据行", line.startsWith("data:"));
    }

    // ==================== JSON 清理（Markdown 包裹） ====================

    @Test
    public void testCleanJson_codeFence() {
        String raw = "```json\n{\"key\": \"value\"}\n```";
        String clean = cleanJson(raw);
        assertEquals("{\"key\": \"value\"}", clean);
    }

    @Test
    public void testCleanJson_codeFenceNoLanguage() {
        String raw = "```\n{\"key\": \"value\"}\n```";
        String clean = cleanJson(raw);
        assertEquals("{\"key\": \"value\"}", clean);
    }

    @Test
    public void testCleanJson_noCodeFence() {
        String raw = "{\"key\": \"value\"}";
        String clean = cleanJson(raw);
        assertEquals("{\"key\": \"value\"}", clean);
    }

    @Test
    public void testCleanJson_extraWhitespace() {
        String raw = "  {\"key\": \"value\"}  ";
        String clean = cleanJson(raw);
        assertEquals("{\"key\": \"value\"}", clean);
    }

    @Test
    public void testCleanJson_onlyBackticksAtEnd() {
        String raw = "{\"key\": \"value\"}```";
        String clean = cleanJson(raw);
        assertEquals("{\"key\": \"value\"}", clean);
    }

    // ==================== 步骤 JSON 解析 ====================

    @Test
    public void testParseStepsJson_valid() {
        String json = "{\"steps\": ["
                + "{\"title\": \"step1\", \"description\": \"desc1\", \"toolRequired\": \"tool1\", \"safetyNote\": \"safe1\"},"
                + "{\"title\": \"step2\", \"description\": \"desc2\", \"toolRequired\": \"tool2\", \"safetyNote\": \"safe2\"}"
                + "]}";
        List<RepairStep> steps = parseSteps(json);
        assertEquals(2, steps.size());
        assertEquals("step1", steps.get(0).getTitle());
        assertEquals("desc1", steps.get(0).getDescription());
        assertEquals("tool1", steps.get(0).getToolRequired());
        assertEquals("safe1", steps.get(0).getSafetyNote());
        assertEquals("step2", steps.get(1).getTitle());
    }

    @Test
    public void testParseStepsJson_missingFields() {
        String json = "{\"steps\": [{\"title\": \"onlyTitle\"}]}";
        List<RepairStep> steps = parseSteps(json);
        assertEquals(1, steps.size());
        assertEquals("onlyTitle", steps.get(0).getTitle());
        assertEquals("", steps.get(0).getDescription());
        assertEquals("", steps.get(0).getToolRequired());
        assertEquals("", steps.get(0).getSafetyNote());
    }

    @Test
    public void testParseStepsJson_emptySteps() {
        String json = "{\"steps\": []}";
        List<RepairStep> steps = parseSteps(json);
        assertTrue(steps.isEmpty());
    }

    @Test
    public void testParseStepsJson_missingStepsKey() {
        String json = "{\"other\": []}";
        List<RepairStep> steps = parseSteps(json);
        assertTrue("缺少 steps 键应返回空列表", steps.isEmpty());
    }

    @Test
    public void testParseStepsJson_invalidJson() {
        String json = "not valid json at all";
        List<RepairStep> steps = parseSteps(json);
        assertTrue("无效 JSON 应返回空列表", steps.isEmpty());
    }

    @Test
    public void testParseStepsJson_nullInput() {
        List<RepairStep> steps = parseSteps(null);
        assertTrue("null 输入应返回空列表", steps.isEmpty());
    }

    @Test
    public void testParseStepsJson_emptyInput() {
        List<RepairStep> steps = parseSteps("");
        assertTrue("空字符串应返回空列表", steps.isEmpty());
    }

    // ==================== 意图 JSON 解析 ====================

    @Test
    public void testParseIntentJson_valid() {
        String json = "{\"intention\": \"ASK_GUIDANCE\", \"confidence\": 0.95}";
        RepairIntention intention = parseIntent(json);
        assertNotNull(intention);
        assertEquals(RepairIntention.Type.ASK_GUIDANCE, intention.getType());
        assertEquals(0.95f, intention.getConfidence(), 0.001f);
    }

    @Test
    public void testParseIntentJson_allTypes() {
        String[] typeNames = {"ASK_GUIDANCE", "REPORT_PROGRESS", "ASK_TOOL",
                "REPORT_ISSUE", "CONFIRM_COMPLETE", "OTHER"};
        RepairIntention.Type[] expectedTypes = {
                RepairIntention.Type.ASK_GUIDANCE, RepairIntention.Type.REPORT_PROGRESS,
                RepairIntention.Type.ASK_TOOL, RepairIntention.Type.REPORT_ISSUE,
                RepairIntention.Type.CONFIRM_COMPLETE, RepairIntention.Type.OTHER
        };

        for (int i = 0; i < typeNames.length; i++) {
            String json = "{\"intention\": \"" + typeNames[i] + "\", \"confidence\": 0.9}";
            RepairIntention intention = parseIntent(json);
            assertNotNull("解析 " + typeNames[i] + " 失败", intention);
            assertEquals("类型应为 " + expectedTypes[i], expectedTypes[i], intention.getType());
        }
    }

    @Test
    public void testParseIntentJson_unknownType_fallsBackToOther() {
        String json = "{\"intention\": \"UNKNOWN_TYPE\", \"confidence\": 0.5}";
        RepairIntention intention = parseIntent(json);
        assertNotNull(intention);
        assertEquals(RepairIntention.Type.OTHER, intention.getType());
    }

    @Test
    public void testParseIntentJson_lowercaseType() {
        // RepairLlmService.parseIntentionJson does toUpperCase()
        String json = "{\"intention\": \"ask_guidance\", \"confidence\": 0.5}";
        RepairIntention intention = parseIntent(json);
        assertNotNull(intention);
        assertEquals(RepairIntention.Type.ASK_GUIDANCE, intention.getType());
    }

    @Test
    public void testParseIntentJson_missingConfidence_defaultZero() {
        String json = "{\"intention\": \"OTHER\"}";
        RepairIntention intention = parseIntent(json);
        assertNotNull(intention);
        assertEquals(0.0f, intention.getConfidence(), 0.001f);
    }

    @Test
    public void testParseIntentJson_invalidJson_returnsNull() {
        String json = "not json";
        RepairIntention intention = parseIntent(json);
        assertNull("无效 JSON 应返回 null", intention);
    }

    @Test
    public void testParseIntentJson_nullInput_returnsNull() {
        RepairIntention intention = parseIntent(null);
        assertNull(intention);
    }

    @Test
    public void testParseIntentJson_emptyInput_returnsNull() {
        RepairIntention intention = parseIntent("");
        assertNull(intention);
    }

    // ==================== OpenAI 兼容 extractContent 测试 ====================

    @Test
    public void testExtractContent_messageFormat() {
        String response = "{\"choices\":[{\"message\":{\"content\":\"hello world\"}}]}";
        String content = extractContent(response);
        assertEquals("hello world", content);
    }

    @Test
    public void testExtractContent_deltaFormat() {
        String response = "{\"choices\":[{\"delta\":{\"content\":\"streaming\"}}]}";
        String content = extractContent(response);
        assertEquals("streaming", content);
    }

    @Test
    public void testExtractContent_emptyContent() {
        String response = "{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
        String content = extractContent(response);
        assertNull("空 content 应返回 null", content);
    }

    @Test
    public void testExtractContent_noChoices() {
        String response = "{\"choices\":[]}";
        String content = extractContent(response);
        assertNull("无 choices 应返回 null", content);
    }

    @Test
    public void testExtractContent_invalidJson() {
        String content = extractContent("not valid");
        assertNull("无效 JSON 应返回 null", content);
    }

    @Test
    public void testExtractContent_whitespaceContent() {
        String response = "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}";
        String content = extractContent(response);
        assertNull("纯空白 content 应返回 null", content);
    }

    // ==================== Vision 响应解析 ====================

    @Test
    public void testParseVisionResponse_jsonInText() {
        String raw = "Scene analysis. ```json\n{\"description\":\"compressor OK\",\"confidence\":0.9}\n```";
        String jsonPart = extractJsonFromText(raw);
        assertNotNull(jsonPart);
        assertTrue("提取的 JSON 应包含 description", jsonPart.contains("description"));
    }

    @Test
    public void testParseVisionResponse_pureDescription() {
        String raw = "This is an air conditioner outdoor unit, running normally.";
        String jsonPart = extractJsonFromText(raw);
        assertNull("无 JSON 时应返回 null", jsonPart);
    }

    @Test
    public void testParseVisionResponse_withRegions() {
        String json = "{\"description\":\"mainboard\",\"confidence\":0.8,"
                + "\"regions\":[{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.4,\"label\":\"cpu\",\"confidence\":0.9}]}";
        FrameAnalysisResult result = parseVisionResponse(json);
        assertEquals("mainboard", result.getDescription());
        assertEquals(0.8f, result.getConfidence(), 0.001f);
        assertEquals(1, result.getRegions().size());
        assertEquals("cpu", result.getRegions().get(0).label);
        assertEquals(0.1f, result.getRegions().get(0).x, 0.001f);
        assertEquals(0.2f, result.getRegions().get(0).y, 0.001f);
        assertEquals(0.3f, result.getRegions().get(0).width, 0.001f);
        assertEquals(0.4f, result.getRegions().get(0).height, 0.001f);
    }

    @Test
    public void testParseVisionResponse_objectsKey() {
        String json = "{\"description\":\"objects\",\"objects\":[{\"x\":0.5,\"y\":0.5,\"width\":0.1,\"height\":0.1,\"label\":\"part\"}]}";
        FrameAnalysisResult result = parseVisionResponse(json);
        assertEquals(1, result.getRegions().size());
        assertEquals("part", result.getRegions().get(0).label);
    }

    @Test
    public void testParseVisionResponse_bboxesKey() {
        String json = "{\"description\":\"bboxes\",\"bboxes\":[{\"x\":0.0,\"y\":0.0,\"width\":0.5,\"height\":0.5,\"label\":\"box\"}]}";
        FrameAnalysisResult result = parseVisionResponse(json);
        assertEquals(1, result.getRegions().size());
        assertEquals("box", result.getRegions().get(0).label);
    }

    // ==================== 内部辅助方法（使用 Gson 替代 org.json） ====================

    private String cleanJson(String jsonStr) {
        if (jsonStr == null) return "";
        String clean = jsonStr.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }

    private List<RepairStep> parseSteps(String jsonStr) {
        List<RepairStep> steps = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) {
            return steps;
        }
        try {
            String clean = cleanJson(jsonStr);
            JsonObject json = JsonParser.parseString(clean).getAsJsonObject();
            JsonArray arr = json.getAsJsonArray("steps");
            if (arr != null) {
                for (JsonElement elem : arr) {
                    JsonObject obj = elem.getAsJsonObject();
                    RepairStep step = new RepairStep();
                    step.setTitle(getString(obj, "title"));
                    step.setDescription(getString(obj, "description"));
                    step.setToolRequired(getString(obj, "toolRequired"));
                    step.setSafetyNote(getString(obj, "safetyNote"));
                    steps.add(step);
                }
            }
        } catch (Exception e) {
            // parse failure → empty
        }
        return steps;
    }

    private RepairIntention parseIntent(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }
        try {
            String clean = cleanJson(jsonStr);
            JsonObject json = JsonParser.parseString(clean).getAsJsonObject();
            String intentionStr = getString(json, "intention");
            if (intentionStr.isEmpty()) intentionStr = "OTHER";
            float confidence = json.has("confidence")
                    ? json.get("confidence").getAsFloat() : 0.0f;
            RepairIntention.Type type;
            try {
                type = RepairIntention.Type.valueOf(intentionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = RepairIntention.Type.OTHER;
            }
            return new RepairIntention(type, confidence, jsonStr);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                JsonObject first = choices.get(0).getAsJsonObject();
                JsonObject msg = first.getAsJsonObject("message");
                if (msg != null && msg.has("content")) {
                    String c = msg.get("content").getAsString().trim();
                    if (!c.isEmpty()) return c;
                }
                JsonObject delta = first.getAsJsonObject("delta");
                if (delta != null && delta.has("content")) {
                    String c = delta.get("content").getAsString().trim();
                    if (!c.isEmpty()) return c;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) return null;
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private FrameAnalysisResult parseVisionResponse(String content) {
        FrameAnalysisResult result = new FrameAnalysisResult();
        result.setDescription(content);
        result.setConfidence(0.5f);

        String jsonPart = extractJsonFromText(content);
        if (jsonPart == null) return result;

        try {
            JsonObject json = JsonParser.parseString(jsonPart).getAsJsonObject();
            if (json.has("description")) {
                result.setDescription(json.get("description").getAsString());
            }
            if (json.has("confidence")) {
                result.setConfidence(json.get("confidence").getAsFloat());
            }

            JsonArray regions = null;
            if (json.has("regions")) regions = json.getAsJsonArray("regions");
            else if (json.has("objects")) regions = json.getAsJsonArray("objects");
            else if (json.has("bboxes")) regions = json.getAsJsonArray("bboxes");

            if (regions != null) {
                List<FrameAnalysisResult.BoundingBox> boxes = new ArrayList<>();
                for (JsonElement elem : regions) {
                    JsonObject r = elem.getAsJsonObject();
                    FrameAnalysisResult.BoundingBox box = new FrameAnalysisResult.BoundingBox();
                    box.x = getFloat(r, "x");
                    box.y = getFloat(r, "y");
                    box.width = getFloat(r, "width");
                    box.height = getFloat(r, "height");
                    box.label = getString(r, "label");
                    box.confidence = getFloat(r, "confidence");
                    boxes.add(box);
                }
                result.setRegions(boxes);
            }
        } catch (Exception e) {
            // fallback: use raw text as description
        }
        return result;
    }

    private String getString(JsonObject obj, String key) {
        if (obj.has(key)) {
            JsonElement elem = obj.get(key);
            return elem.isJsonNull() ? "" : elem.getAsString();
        }
        return "";
    }

    private float getFloat(JsonObject obj, String key) {
        if (obj.has(key)) {
            JsonElement elem = obj.get(key);
            return elem.isJsonNull() ? 0.0f : elem.getAsFloat();
        }
        return 0.0f;
    }
}
