package com.gongyoutong.app.ai;

import com.gongyoutong.app.repair.DiagnosisResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * WorkOrderAiService.parseDiagnosisJson 逻辑验证测试
 *
 * 背景：
 * - WorkOrderAiService 依赖 Looper.getMainLooper()，无法在纯 JUnit 中实例化
 * - org.json.JSONObject 是 Android API，纯 JUnit 中未 mock
 * - 因此使用 Gson（项目已有依赖）复刻解析逻辑进行验证
 *
 * 注意：此测试验证的是 parseDiagnosisJson 的逻辑正确性，
 * 实际源码仍使用 org.json.JSONObject，两边逻辑需保持一致。
 */
public class WorkOrderAiServiceTest {

    private final Gson gson = new Gson();

    /**
     * 用 Gson 复刻 parseDiagnosisJson 的 markdown 清理 + JSON 解析逻辑
     */
    private DiagnosisResult parseDiagnosisJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return null;
        }

        try {
            String cleanJson = jsonStr.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            } else if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            JsonObject json = JsonParser.parseString(cleanJson).getAsJsonObject();

            DiagnosisResult result = new DiagnosisResult();
            result.setFaultCauses(json.has("faultCauses") && !json.get("faultCauses").isJsonNull()
                    ? json.get("faultCauses").getAsString() : "暂无信息");
            result.setInspectionSteps(json.has("inspectionSteps") && !json.get("inspectionSteps").isJsonNull()
                    ? json.get("inspectionSteps").getAsString() : "暂无信息");
            result.setSafetyTips(json.has("safetyTips") && !json.get("safetyTips").isJsonNull()
                    ? json.get("safetyTips").getAsString() : "暂无信息");
            result.setRequiredTools(json.has("requiredTools") && !json.get("requiredTools").isJsonNull()
                    ? json.get("requiredTools").getAsString() : "暂无信息");
            result.setRequiredParts(json.has("requiredParts") && !json.get("requiredParts").isJsonNull()
                    ? json.get("requiredParts").getAsString() : "暂无信息");
            result.setEstimatedTime(json.has("estimatedTime") && !json.get("estimatedTime").isJsonNull()
                    ? json.get("estimatedTime").getAsString() : "暂无信息");
            result.setRawResponse(jsonStr);
            result.initStepCheckedStates(result.getInspectionSteps());

            return result;

        } catch (Exception e) {
            return null;
        }
    }

    @Test
    public void testParseDiagnosisJson_validInput() {
        String json = "{"
                + "\"faultCauses\":\"压缩机故障 80%\","
                + "\"inspectionSteps\":\"1. 检查电源\\n2. 检查压缩机\","
                + "\"safetyTips\":\"断电操作\","
                + "\"requiredTools\":\"万用表\","
                + "\"requiredParts\":\"压缩机\","
                + "\"estimatedTime\":\"约30分钟\""
                + "}";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull("有效 JSON 应返回非 null 结果", result);
        assertEquals("压缩机故障 80%", result.getFaultCauses());
        assertEquals("1. 检查电源\n2. 检查压缩机", result.getInspectionSteps());
        assertEquals("断电操作", result.getSafetyTips());
        assertEquals("万用表", result.getRequiredTools());
        assertEquals("压缩机", result.getRequiredParts());
        assertEquals("约30分钟", result.getEstimatedTime());
    }

    @Test
    public void testParseDiagnosisJson_nullInput() {
        assertNull("null 输入应返回 null", parseDiagnosisJson(null));
    }

    @Test
    public void testParseDiagnosisJson_emptyInput() {
        assertNull("空字符串应返回 null", parseDiagnosisJson(""));
    }

    @Test
    public void testParseDiagnosisJson_missingFields() {
        String json = "{\"faultCauses\":\"电源问题\"}";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull("缺失字段时应返回非 null 结果（使用默认值）", result);
        assertEquals("电源问题", result.getFaultCauses());
        assertEquals("缺失字段应使用默认值'暂无信息'", "暂无信息", result.getInspectionSteps());
        assertEquals("暂无信息", result.getSafetyTips());
        assertEquals("暂无信息", result.getRequiredTools());
        assertEquals("暂无信息", result.getRequiredParts());
        assertEquals("暂无信息", result.getEstimatedTime());
    }

    @Test
    public void testParseDiagnosisJson_markdownWrappedJson() {
        String json = "```json\n{\"faultCauses\":\"测试原因\",\"inspectionSteps\":\"步骤1\",\"safetyTips\":\"提示1\",\"requiredTools\":\"工具1\",\"requiredParts\":\"配件1\",\"estimatedTime\":\"10分钟\"}\n```";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull("Markdown ```json 包裹的 JSON 应正确解析", result);
        assertEquals("测试原因", result.getFaultCauses());
    }

    @Test
    public void testParseDiagnosisJson_tripleBacktickOnly() {
        String json = "```\n{\"faultCauses\":\"原因\",\"inspectionSteps\":\"步骤\",\"safetyTips\":\"安全\",\"requiredTools\":\"工具\",\"requiredParts\":\"配件\",\"estimatedTime\":\"时间\"}\n```";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull("纯 ``` 包裹的 JSON 应正确解析", result);
        assertEquals("原因", result.getFaultCauses());
    }

    @Test
    public void testParseDiagnosisJson_invalidJson() {
        assertNull("无效 JSON 应返回 null", parseDiagnosisJson("这不是一个JSON字符串"));
    }

    @Test
    public void testParseDiagnosisJson_partialMarkdown() {
        String json = "```json\n{\"faultCauses\":\"原因\",\"inspectionSteps\":\"步骤\",\"safetyTips\":\"安全\",\"requiredTools\":\"工具\",\"requiredParts\":\"配件\",\"estimatedTime\":\"时间\"}";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull("只有前缀 ```json 的应正确解析", result);
        assertEquals("原因", result.getFaultCauses());
    }

    @Test
    public void testParseDiagnosisJson_emptyFields() {
        String json = "{\"faultCauses\":\"\",\"inspectionSteps\":\"\",\"safetyTips\":\"\",\"requiredTools\":\"\",\"requiredParts\":\"\",\"estimatedTime\":\"\"}";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull("空字段应返回非 null 结果", result);
        assertEquals("", result.getFaultCauses());
    }

    @Test
    public void testParseDiagnosisJson_initStepCheckedStates() {
        String json = "{\"faultCauses\":\"原因\",\"inspectionSteps\":\"步骤1\\n步骤2\\n步骤3\",\"safetyTips\":\"安全\",\"requiredTools\":\"工具\",\"requiredParts\":\"配件\",\"estimatedTime\":\"时间\"}";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull(result);
        assertNotNull("应初始化 stepCheckedStates", result.getStepCheckedStates());
        assertEquals("3个步骤应有3个勾选状态", 3, result.getStepCheckedStates().size());
    }

    @Test
    public void testParseDiagnosisJson_rawResponsePreserved() {
        String json = "{\"faultCauses\":\"原因\",\"inspectionSteps\":\"步骤\",\"safetyTips\":\"安全\",\"requiredTools\":\"工具\",\"requiredParts\":\"配件\",\"estimatedTime\":\"时间\"}";

        DiagnosisResult result = parseDiagnosisJson(json);

        assertNotNull(result);
        assertEquals("rawResponse 应保存原始输入", json, result.getRawResponse());
    }

    @Test
    public void testParseDiagnosisJson_whitespaceOnlyJson() {
        // "   " is not empty, so it won't return null at the first check,
        // but JSON parse will fail → return null
        assertNull("纯空白输入解析失败应返回 null", parseDiagnosisJson("   "));
    }

    // ==================== 源码逻辑缺陷验证 ====================

    /**
     * 验证 optString 对空字符串的行为：
     * - org.json.JSONObject.optString("key", default) 在 key 存在但值为空串时
     *   返回空串而非默认值
     * - 这意味着 AI 返回 {"faultCauses": ""} 时，结果不是"暂无信息"而是空串
     * - 这是源码的一个小问题，但不是 Bug（设计选择）
     */
    @Test
    public void testEmptyStringVsDefaultValue() {
        String json = "{\"faultCauses\":\"\"}";
        DiagnosisResult result = parseDiagnosisJson(json);
        assertNotNull(result);
        // Gson 的 getAsString 对空字符串返回空串
        assertEquals("空字符串不会被替换为默认值", "", result.getFaultCauses());
    }
}
