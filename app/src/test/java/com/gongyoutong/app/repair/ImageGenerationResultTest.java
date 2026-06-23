package com.gongyoutong.app.repair;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 测试 ImageGenerationResult.fromJson() 解析两种响应格式
 */
public class ImageGenerationResultTest {

    @Test
    public void testParseImagesArray() {
        // 测试 data.images 数组格式
        String json = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {" +
                "  \"images\": [" +
                "    {\"url\": \"https://example.com/image1.jpg\", \"size\": \"2048x2048\"}," +
                "    {\"url\": \"https://example.com/image2.jpg\", \"size\": \"2048x2048\"}" +
                "  ]" +
                "}" +
                "}";

        ImageGenerationResult result = ImageGenerationResult.fromJson(json);
        assertNotNull("解析结果不应为 null", result);
        assertTrue("code 应为 0", result.isSuccess());
        assertEquals("应解析出 2 张图片", 2, result.getImageUrls().size());
        assertEquals("第一张图片 URL 不正确", "https://example.com/image1.jpg", result.getImageUrls().get(0));
        assertEquals("第二张图片 URL 不正确", "https://example.com/image2.jpg", result.getImageUrls().get(1));
    }

    @Test
    public void testParseImageString() {
        // 测试 data.image 字符串格式（回退处理）
        String json = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {" +
                "  \"image\": \"https://example.com/single_image.jpg\"" +
                "}" +
                "}";

        ImageGenerationResult result = ImageGenerationResult.fromJson(json);
        assertNotNull("解析结果不应为 null", result);
        assertTrue("code 应为 0", result.isSuccess());
        assertEquals("应解析出 1 张图片", 1, result.getImageUrls().size());
        assertEquals("图片 URL 不正确", "https://example.com/single_image.jpg", result.getImageUrls().get(0));
    }

    @Test
    public void testParseBothFormats() {
        // 测试同时包含 data.image 和 data.images 时优先使用 data.images
        String json = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {" +
                "  \"image\": \"https://example.com/single.jpg\"," +
                "  \"images\": [" +
                "    {\"url\": \"https://example.com/multi1.jpg\", \"size\": \"2048x2048\"}" +
                "  ]" +
                "}" +
                "}";

        ImageGenerationResult result = ImageGenerationResult.fromJson(json);
        assertNotNull("解析结果不应为 null", result);
        assertTrue("code 应为 0", result.isSuccess());
        assertEquals("应解析出 1 张图片（优先使用 images 数组）", 1, result.getImageUrls().size());
        assertEquals("图片 URL 不正确", "https://example.com/multi1.jpg", result.getImageUrls().get(0));
    }

    @Test
    public void testParseErrorResponse() {
        // 测试错误响应
        String json = "{" +
                "\"code\": 1001," +
                "\"message\": \"Invalid parameter\"," +
                "\"data\": null" +
                "}";

        ImageGenerationResult result = ImageGenerationResult.fromJson(json);
        assertNotNull("解析结果不应为 null", result);
        assertFalse("code 不应为 0", result.isSuccess());
        assertEquals("错误消息不正确", "Invalid parameter", result.getMessage());
    }

    @Test
    public void testParseEmptyData() {
        // 测试空 data
        String json = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {}" +
                "}";

        ImageGenerationResult result = ImageGenerationResult.fromJson(json);
        assertNotNull("解析结果不应为 null", result);
        assertTrue("code 应为 0", result.isSuccess());
        assertTrue("应解析出 0 张图片", result.getImageUrls().isEmpty());
    }

    @Test
    public void testParseNullJson() {
        // 测试 null 输入
        ImageGenerationResult result = ImageGenerationResult.fromJson(null);
        assertNull("null 输入应返回 null", result);
    }

    @Test
    public void testParseEmptyJson() {
        // 测试空字符串输入
        ImageGenerationResult result = ImageGenerationResult.fromJson("");
        assertNull("空字符串输入应返回 null", result);
    }
}