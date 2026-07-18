package com.gongyoutong.app.repair;

import android.util.Log;

import org.json.JSONObject;

/**
 * AI 视频生成结果数据类
 * 用于解析 vivo 蓝心大模型视频生成 API 的返回结果
 */
public class VideoGenerationResult {

    private static final String TAG = "VideoGenerationResult";

    private int code;
    private String message;
    private String taskId;
    private String status;
    private String videoUrl;
    private String lastFrameUrl;
    private int duration;
    private String resolution;
    private String ratio;
    private int framesPerSecond;

    public VideoGenerationResult() {
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

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getLastFrameUrl() {
        return lastFrameUrl;
    }

    public void setLastFrameUrl(String lastFrameUrl) {
        this.lastFrameUrl = lastFrameUrl;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getRatio() {
        return ratio;
    }

    public void setRatio(String ratio) {
        this.ratio = ratio;
    }

    public int getFramesPerSecond() {
        return framesPerSecond;
    }

    public void setFramesPerSecond(int framesPerSecond) {
        this.framesPerSecond = framesPerSecond;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 从 JSON 字符串解析 VideoGenerationResult（提交任务响应）
     *
     * @param json API 返回的 JSON 字符串
     * @return 解析后的结果对象，解析失败返回 null
     */
    public static VideoGenerationResult fromSubmitResponse(String json) {
        if (json == null || json.isEmpty()) {
            Log.w(TAG, "fromJson: json is null or empty");
            return null;
        }

        try {
            JSONObject root = new JSONObject(json);
            VideoGenerationResult result = new VideoGenerationResult();

            result.setCode(root.optInt("code", -1));
            result.setMessage(root.optString("message", ""));

            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                result.setTaskId(data.optString("id", ""));
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "fromJson parse error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从 JSON 字符串解析 VideoGenerationResult（查询任务响应）
     *
     * @param json API 返回的 JSON 字符串
     * @return 解析后的结果对象，解析失败返回 null
     */
    public static VideoGenerationResult fromQueryResponse(String json) {
        if (json == null || json.isEmpty()) {
            Log.w(TAG, "fromJson: json is null or empty");
            return null;
        }

        try {
            JSONObject root = new JSONObject(json);
            VideoGenerationResult result = new VideoGenerationResult();

            result.setCode(root.optInt("code", -1));
            result.setMessage(root.optString("message", ""));

            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                result.setTaskId(data.optString("id", ""));
                result.setStatus(data.optString("status", ""));
                result.setDuration(data.optInt("duration", 0));
                result.setResolution(data.optString("resolution", ""));
                result.setRatio(data.optString("ratio", ""));
                result.setFramesPerSecond(data.optInt("framespersecond", 0));

                JSONObject content = data.optJSONObject("content");
                if (content != null) {
                    result.setVideoUrl(content.optString("video_url", ""));
                    result.setLastFrameUrl(content.optString("last_frame_url", ""));
                }
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "fromJson parse error: " + e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为成功响应（提交任务）
     */
    public boolean isSubmitSuccess() {
        return code == 0 && taskId != null && !taskId.isEmpty();
    }

    /**
     * 判断是否为成功响应（查询任务）
     */
    public boolean isQuerySuccess() {
        return code == 0;
    }

    /**
     * 判断任务是否已完成
     */
    public boolean isCompleted() {
        return "succeeded".equals(status);
    }

    /**
     * 判断任务是否失败
     */
    public boolean isFailed() {
        return "failed".equals(status);
    }

    /**
     * 判断任务是否仍在处理中
     */
    public boolean isProcessing() {
        return status != null && !isCompleted() && !isFailed();
    }

    @Override
    public String toString() {
        return "VideoGenerationResult{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", taskId='" + taskId + '\'' +
                ", status='" + status + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                '}';
    }
}