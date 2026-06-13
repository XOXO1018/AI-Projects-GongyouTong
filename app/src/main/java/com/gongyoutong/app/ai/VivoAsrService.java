package com.gongyoutong.app.ai;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.gongyoutong.app.Config;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * vivo 蓝心大模型 - 实时短语音识别服务
 * 基于 WebSocket 协议（v2 接口），实现实时语音转文字功能
 *
 * 音频参数：PCM 16kHz 16bit 单声道
 * 最大识别时长：60秒
 *
 * 接口文档参考：蓝心大模型能力/实施短语音识别.docx
 */
public class VivoAsrService {

    private static final String TAG = "VivoAsrService";

    // ASR 音频参数
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FRAME_DURATION_MS = 40;
    private static final int FRAME_SIZE = SAMPLE_RATE * FRAME_DURATION_MS / 1000; // 640 samples
    private static final int BYTE_PER_SAMPLE = 2;
    private static final int FRAME_BYTES = FRAME_SIZE * BYTE_PER_SAMPLE; // 1280 bytes
    private static final int MAX_RECORD_DURATION_MS = 60000;

    // 录音
    private AudioRecord audioRecord;
    private volatile boolean isRecording = false;
    private WebSocket webSocket;
    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 回调
    private AsrCallback callback;
    private long recordStartTime;
    private String accumulatedText = "";
    private String currentRequestId;

    public interface AsrCallback {
        void onResult(String text, boolean isFinal);
        void onError(String errorMessage);
        void onRecordingStateChanged(boolean isRecording);
    }

    public VivoAsrService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public void startRecognition(AsrCallback callback) {
        this.callback = callback;
        this.accumulatedText = "";

        executor.execute(() -> {
            try {
                boolean connected = connectWebSocket();
                if (!connected) {
                    notifyError("无法连接语音识别服务");
                    return;
                }
                startRecording();
            } catch (Exception e) {
                Log.e(TAG, "startRecognition error: " + e.getMessage());
                notifyError("启动语音识别失败: " + e.getMessage());
            }
        });
    }

    public void stopRecognition() {
        executor.execute(() -> {
            stopRecording();
            closeWebSocket();
        });
    }

    public void destroy() {
        stopRecognition();
        executor.shutdown();
    }

    public boolean isRecording() {
        return isRecording;
    }

    // ==================== WebSocket 连接 ====================

    private boolean connectWebSocket() {
        try {
            currentRequestId = UUID.randomUUID().toString();
            String systemTime = String.valueOf(System.currentTimeMillis());

            // 构建 URL 查询参数（根据官方文档）
            String wsUrl = Config.VIVO_ASR_WS_URL
                    + "?engineid=" + Config.VIVO_ASR_ENGINE_ID
                    + "&system_time=" + systemTime
                    + "&user_id=" + Config.VIVO_ASR_USER_ID
                    + "&model=unknown"
                    + "&product=unknown"
                    + "&package=com.gongyoutong.app"
                    + "&client_version=unknown"
                    + "&system_version=unknown"
                    + "&sdk_version=unknown"
                    + "&android_version=unknown"
                    + "&requestId=" + currentRequestId;

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", "Bearer " + Config.VIVO_APP_KEY)
                    .build();

            CountDownLatch connectLatch = new CountDownLatch(1);
            AtomicBoolean connected = new AtomicBoolean(false);

            webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.d(TAG, "ASR WebSocket connected, response=" + response.code());
                    connected.set(true);
                    connectLatch.countDown();
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    handleServerMessage(text);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.e(TAG, "ASR WebSocket failure: " + t.getMessage());
                    connected.set(false);
                    connectLatch.countDown();
                    notifyError("语音识别连接断开");
                    stopRecording();
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    Log.d(TAG, "ASR WebSocket closed: code=" + code + ", reason=" + reason);
                }
            });

            // 等待连接建立（最多 5 秒）
            boolean success = connectLatch.await(5, TimeUnit.SECONDS);
            return success && connected.get();

        } catch (Exception e) {
            Log.e(TAG, "connectWebSocket error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送启动配置（根据官方文档 started 消息格式）
     */
    private void sendStartConfig() {
        try {
            JSONObject asrInfo = new JSONObject();
            asrInfo.put("front_vad_time", 6000);     // 前置 VAD 6 秒
            asrInfo.put("end_vad_time", 2500);       // 后置 VAD 2.5 秒
            asrInfo.put("audio_type", "pcm");
            asrInfo.put("chinese2digital", 1);       // 开启中文数字转换
            asrInfo.put("punctuation", 1);           // 开启标点

            JSONObject config = new JSONObject();
            config.put("type", "started");
            config.put("request_id", currentRequestId);
            config.put("asr_info", asrInfo);
            config.put("business_info", "");

            if (webSocket != null) {
                webSocket.send(config.toString());
                Log.d(TAG, "ASR started config sent: " + config.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "sendStartConfig error: " + e.getMessage());
        }
    }

    /**
     * 处理服务端返回消息（根据官方文档响应格式）
     */
    private void handleServerMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String action = json.optString("action", "");

            if ("started".equals(action)) {
                // 握手成功，服务端已准备好接收音频
                int code = json.optInt("code", -1);
                if (code == 0) {
                    Log.d(TAG, "ASR service started successfully, sid=" + json.optString("sid"));
                    notifyRecordingStateChanged(true);
                } else {
                    String desc = json.optString("desc", "未知错误");
                    Log.e(TAG, "ASR start failed: code=" + code + ", desc=" + desc);
                    notifyError("语音识别服务启动失败: " + desc);
                }

            } else if ("result".equals(action)) {
                // 识别结果
                String type = json.optString("type", "");
                if (!"asr".equals(type)) return;

                JSONObject data = json.optJSONObject("data");
                if (data == null) return;

                String text = data.optString("text", "");
                boolean isLast = data.optBoolean("is_last", false);
                int reformation = data.optInt("reformation", 0);

                // reformation: 0=追加, 1=修正
                if (reformation == 1) {
                    accumulatedText = text;
                } else {
                    accumulatedText = text;
                }

                Log.d(TAG, "ASR result: text=" + accumulatedText + ", isLast=" + isLast);

                if (isLast) {
                    notifyResult(accumulatedText, true);
                } else {
                    notifyResult(accumulatedText, false);
                }

            } else if ("error".equals(action)) {
                int code = json.optInt("code", -1);
                String desc = json.optString("desc", "未知错误");
                Log.e(TAG, "ASR error: code=" + code + ", desc=" + desc);
                notifyError("语音识别错误(code=" + code + "): " + desc);
                stopRecording();
            }

        } catch (Exception e) {
            Log.e(TAG, "handleServerMessage error: " + e.getMessage());
        }
    }

    private void closeWebSocket() {
        if (webSocket != null) {
            try {
                // 发送 --close-- 标记（根据官方文档）
                webSocket.send(okio.ByteString.encodeUtf8("--close--"));
            } catch (Exception e) {
                Log.e(TAG, "send close marker error: " + e.getMessage());
            }
            try {
                webSocket.close(1000, "session ended");
            } catch (Exception e) {
                Log.e(TAG, "closeWebSocket error: " + e.getMessage());
            }
            webSocket = null;
        }
    }

    // ==================== 录音相关 ====================

    private void startRecording() {
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int bufferSize = Math.max(minBufferSize, FRAME_BYTES * 4);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                notifyError("录音初始化失败，请检查麦克风权限");
                return;
            }

            // 连接成功后发送配置
            sendStartConfig();

            audioRecord.startRecording();
            isRecording = true;
            recordStartTime = System.currentTimeMillis();

            Log.d(TAG, "Recording started, sampleRate=" + SAMPLE_RATE + ", frameBytes=" + FRAME_BYTES);

            // 录音循环：读取 PCM 数据并以二进制帧发送
            byte[] buffer = new byte[FRAME_BYTES];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, FRAME_BYTES);
                if (read > 0 && webSocket != null) {
                    // 以 binary 帧发送音频数据（与 Python demo 一致）
                    boolean sent = webSocket.send(okio.ByteString.of(buffer, 0, read));
                    if (!sent) {
                        Log.w(TAG, "Failed to send audio frame");
                    }
                }

                // 检查最大时长
                if (System.currentTimeMillis() - recordStartTime > MAX_RECORD_DURATION_MS) {
                    Log.d(TAG, "Max recording duration reached");
                    break;
                }
            }

            // 录音结束，发送 --end-- 标记（根据官方文档）
            if (webSocket != null) {
                webSocket.send(okio.ByteString.encodeUtf8("--end--"));
                Log.d(TAG, "Sent --end-- marker");
            }

        } catch (Exception e) {
            Log.e(TAG, "startRecording error: " + e.getMessage());
            notifyError("录音失败: " + e.getMessage());
        } finally {
            stopRecordingInternal();
        }
    }

    private void stopRecording() {
        isRecording = false;
        notifyRecordingStateChanged(false);
        stopRecordingInternal();
    }

    private void stopRecordingInternal() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "stopRecordingInternal error: " + e.getMessage());
            }
            audioRecord = null;
        }
    }

    // ==================== 通知回调 ====================

    private void notifyResult(String text, boolean isFinal) {
        if (callback != null) {
            callback.onResult(text, isFinal);
        }
    }

    private void notifyError(String message) {
        if (callback != null) {
            callback.onError(message);
        }
    }

    private void notifyRecordingStateChanged(boolean recording) {
        if (callback != null) {
            callback.onRecordingStateChanged(recording);
        }
    }
}
