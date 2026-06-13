package com.gongyoutong.app.repair;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 图片生成结果数据类
 * 用于解析 vivo 蓝心大模型图片生成 API 的返回结果
 */
public class ImageGenerationResult {

    private static final String TAG = "ImageGenerationResult";

    private int code;
    private String message;
    private List<GeneratedImage> images;

    public ImageGenerationResult() {
        this.images = new ArrayList<>();
    }

    // ==================== Getters / Setters ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<GeneratedImage> getImages() {
        return images;
    }

    public void setImages(List<GeneratedImage> images) {
        this.images = images != null ? images : new ArrayList<>();
    }

    /**
     * 提取所有图片的 URL 列表
     */
    public List<String> getImageUrls() {
        List<String> urls = new ArrayList<>();
        if (images != null) {
            for (GeneratedImage img : images) {
                if (img.getUrl() != null && !img.getUrl().isEmpty()) {
                    urls.add(img.getUrl());
                }
            }
        }
        return urls;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 从 JSON 字符串解析 ImageGenerationResult
     *
     * @param json API 返回的 JSON 字符串
     * @return 解析后的结果对象，解析失败返回 null
     */
    public static ImageGenerationResult fromJson(String json) {
        if (json == null || json.isEmpty()) {
            Log.w(TAG, "fromJson: json is null or empty");
            return null;
        }

        try {
            JSONObject root = new JSONObject(json);
            ImageGenerationResult result = new ImageGenerationResult();

            result.setCode(root.optInt("code", -1));
            result.setMessage(root.optString("message", ""));

            // 解析 data.images 数组
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray imagesArray = data.optJSONArray("images");
                if (imagesArray != null && imagesArray.length() > 0) {
                    List<GeneratedImage> imageList = new ArrayList<>();
                    for (int i = 0; i < imagesArray.length(); i++) {
                        JSONObject imgObj = imagesArray.getJSONObject(i);
                        GeneratedImage generatedImage = new GeneratedImage();
                        generatedImage.setUrl(imgObj.optString("url", ""));
                        generatedImage.setSize(imgObj.optString("size", ""));
                        imageList.add(generatedImage);
                    }
                    result.setImages(imageList);
                }
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "fromJson parse error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为成功响应
     */
    public boolean isSuccess() {
        return code == 0;
    }

    // ==================== 内部分类 ====================

    /**
     * 生成的单张图片信息
     */
    public static class GeneratedImage {
        private String url;   // 图片 URL
        private String size;  // 图片尺寸（如 "2048x2048"）

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return "GeneratedImage{url='" + url + "', size='" + size + "'}";
        }
    }
}
