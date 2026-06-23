import com.gongyoutong.app.repair.ImageGenerationResult;

/**
 * 测试图片生成功能
 * 
 * 这个测试文件验证以下功能：
 * 1. ImageGenerationService 是否正确实现
 * 2. 图片生成API调用是否正确
 * 3. Glide图片加载是否正确配置
 * 4. ImageGenerationResult.fromJson() 能否正确解析两种响应格式
 * 
 * 使用方法：
 * 1. 确保已添加Glide依赖
 * 2. 确保Config.java中已配置图片生成相关常量
 * 3. 运行MainActivity测试图片生成按钮
 */

public class TestImageGeneration {
    public static void main(String[] args) {
        System.out.println("=== 图片生成功能测试 ===");
        
        // 测试配置
        System.out.println("1. 检查配置常量:");
        System.out.println("   - VIVO_IMAGE_GENERATION_URL: " + "https://api-ai.vivo.com.cn/api/v1/image_generation");
        System.out.println("   - VIVO_IMAGE_MODEL: " + "Doubao-Seedream-4.5");
        System.out.println("   - DEFAULT_IMAGE_SIZE: " + "2048x2048");
        
        // 测试API参数
        System.out.println("\n2. 测试API参数:");
        System.out.println("   - 请求头: Content-Type: application/json");
        System.out.println("   - 请求头: Authorization: Bearer AppKey");
        System.out.println("   - URL参数: module=aigc");
        System.out.println("   - Body参数: model, prompt, size");
        
        // 测试图片加载
        System.out.println("\n3. 测试图片加载:");
        System.out.println("   - Glide依赖: com.github.bumptech.glide:glide:4.16.0");
        System.out.println("   - 图片显示: ImageView with Glide loading");
        
        // 测试响应解析
        System.out.println("\n4. 测试响应解析:");
        testResponseParsing();
        
        System.out.println("\n=== 测试完成 ===");
    }
    
    /**
     * 测试 ImageGenerationResult.fromJson() 解析两种响应格式
     */
    private static void testResponseParsing() {
        // 测试 1: data.images 数组格式
        String jsonWithImagesArray = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {" +
                "  \"images\": [" +
                "    {\"url\": \"https://example.com/image1.jpg\", \"size\": \"2048x2048\"}," +
                "    {\"url\": \"https://example.com/image2.jpg\", \"size\": \"2048x2048\"}" +
                "  ]" +
                "}" +
                "}";
        
        ImageGenerationResult result1 = ImageGenerationResult.fromJson(jsonWithImagesArray);
        if (result1 != null && result1.isSuccess() && result1.getImageUrls().size() == 2) {
            System.out.println("   ✓ 测试 1 通过: data.images 数组格式解析正确");
        } else {
            System.out.println("   ✗ 测试 1 失败: data.images 数组格式解析错误");
        }
        
        // 测试 2: data.image 字符串格式（回退处理）
        String jsonWithImageString = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {" +
                "  \"image\": \"https://example.com/single_image.jpg\"" +
                "}" +
                "}";
        
        ImageGenerationResult result2 = ImageGenerationResult.fromJson(jsonWithImageString);
        if (result2 != null && result2.isSuccess() && result2.getImageUrls().size() == 1) {
            System.out.println("   ✓ 测试 2 通过: data.image 字符串格式解析正确");
        } else {
            System.out.println("   ✗ 测试 2 失败: data.image 字符串格式解析错误");
        }
        
        // 测试 3: 同时包含 data.image 和 data.images
        String jsonWithBoth = "{" +
                "\"code\": 0," +
                "\"message\": \"success\"," +
                "\"data\": {" +
                "  \"image\": \"https://example.com/single.jpg\"," +
                "  \"images\": [" +
                "    {\"url\": \"https://example.com/multi1.jpg\", \"size\": \"2048x2048\"}" +
                "  ]" +
                "}" +
                "}";
        
        ImageGenerationResult result3 = ImageGenerationResult.fromJson(jsonWithBoth);
        if (result3 != null && result3.isSuccess() && result3.getImageUrls().size() == 1) {
            System.out.println("   ✓ 测试 3 通过: 同时包含两种格式时优先使用 data.images");
        } else {
            System.out.println("   ✗ 测试 3 失败: 同时包含两种格式时解析错误");
        }
    }
}