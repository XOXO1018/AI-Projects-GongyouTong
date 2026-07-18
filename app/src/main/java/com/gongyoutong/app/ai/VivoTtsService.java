package com.gongyoutong.app.ai;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
 * vivo TTS 语音合成服务
 * 基于 WebSocket 协议，文字转语音并播放
 *
 * 接口文档参考：蓝心大模型能力/音频生成.docx
 */
public class VivoTtsService {

    private static final String TAG = "VivoTtsService";

    private static volatile VivoTtsService sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying;
    private WebSocket webSocket;

    private VivoTtsService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(AiConfig.VIVO_TTS_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        executor = Executors.newFixedThreadPool(1);
        mainHandler = new Handler(Looper.getMainLooper());
        isPlaying = false;
    }

    public static VivoTtsService getInstance() {
        if (sInstance == null) {
            synchronized (VivoTtsService.class) {
                if (sInstance == null) {
                    sInstance = new VivoTtsService();
                }
            }
        }
        return sInstance;
    }

    // ==================== 回调接口 ====================

    public interface TtsCallback {
        void onStart();
        void onComplete();
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * 文字转语音并播放
     */
    public void speak(String text, TtsCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("合成文本为空"));
            return;
        }

        stopCurrent();

        executor.execute(() -> {
            try {
                // 1. 构建 WebSocket URL 查询参数
                String requestId = UUID.randomUUID().toString();
                String systemTime = String.valueOf(System.currentTimeMillis() / 1000);

                String wsUrl = AiConfig.VIVO_TTS_WS_URL
                        + "?engineid=" + AiConfig.VIVO_TTS_ENGINE_ID
                        + "&system_time=" + systemTime
                        + "&user_id=" + AiConfig.VIVO_TTS_USER_ID
                        + "&model=unknown"
                        + "&product=unknown"
                        + "&package=com.gongyoutong.app"
                        + "&client_version=unknown"
                        + "&system_version=unknown"
                        + "&sdk_version=unknown"
                        + "&android_version=unknown"
                        + "&requestId=" + requestId;

                Request request = new Request.Builder()
                        .url(wsUrl)
                        .addHeader("Authorization", AiConfig.authHeader())
                        .addHeader("vaid", "123456789")
                        .build();

                Log.d(TAG, "TTS 连接: engineid=" + AiConfig.VIVO_TTS_ENGINE_ID + ", 文本长度=" + text.length());

                // 2. 建立 WebSocket 连接
                CountDownLatch connectLatch = new CountDownLatch(1);
                AtomicBoolean connected = new AtomicBoolean(false);
                String[] connectError = {null};

                // 用于收集 PCM 音频数据
                ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
                AtomicBoolean synthesisComplete = new AtomicBoolean(false);
                CountDownLatch completeLatch = new CountDownLatch(1);

                webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        Log.d(TAG, "TTS WebSocket opened, code=" + response.code());
                        // 连接打开不代表握手成功，需等待 error_code=0 消息
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String msg) {
                        try {
                            JSONObject json = new JSONObject(msg);
                            int errorCode = json.optInt("error_code", -1);

                            if (!connected.get()) {
                                // 握手阶段
                                if (errorCode == 0) {
                                    connected.set(true);
                                    connectLatch.countDown();
                                    Log.d(TAG, "TTS handshake success");
                                    // 握手成功后发送合成请求
                                    sendSynthesisRequest(webSocket, text);
                                } else {
                                    String errMsg = json.optString("error_msg", "握手失败");
                                    connectError[0] = errMsg;
                                    connectLatch.countDown();
                                    Log.e(TAG, "TTS handshake failed: " + errMsg);
                                }
                                return;
                            }

                            // 合成阶段 - 接收音频数据
                            if (errorCode != 0) {
                                String errMsg = json.optString("error_msg", "合成错误");
                                Log.e(TAG, "TTS synthesis error: " + errMsg);
                                connectError[0] = errMsg;
                                completeLatch.countDown();
                                return;
                            }

                            JSONObject data = json.optJSONObject("data");
                            if (data == null) return;

                            int status = data.optInt("status", -1);
                            String audioBase64 = data.optString("audio", "");

                            if (!audioBase64.isEmpty()) {
                                byte[] pcmChunk = Base64.decode(audioBase64, Base64.DEFAULT);
                                pcmBuffer.write(pcmChunk);
                            }

                            if (status == 0) {
                                Log.d(TAG, "TTS synthesis started");
                            } else if (status == 2) {
                                Log.d(TAG, "TTS synthesis complete, total PCM bytes=" + pcmBuffer.size());
                                synthesisComplete.set(true);
                                completeLatch.countDown();
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "TTS onMessage error: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        Log.e(TAG, "TTS WebSocket failure: " + (t != null ? t.getMessage() : "unknown"));
                        connectError[0] = t != null ? t.getMessage() : "WebSocket 连接失败";
                        connected.set(false);
                        connectLatch.countDown();
                        completeLatch.countDown();
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        Log.d(TAG, "TTS WebSocket closed: code=" + code + ", reason=" + reason);
                    }
                });

                // 等待握手完成
                boolean handshakeOk = connectLatch.await(10, TimeUnit.SECONDS);
                if (!handshakeOk || !connected.get()) {
                    String err = connectError[0] != null ? connectError[0] : "语音合成服务连接超时";
                    mainHandler.post(() -> callback.onError(err));
                    closeWebSocket();
                    return;
                }

                // 等待合成完成
                boolean synthesisOk = completeLatch.await(AiConfig.VIVO_TTS_TIMEOUT, TimeUnit.SECONDS);
                closeWebSocket();

                if (!synthesisOk || !synthesisComplete.get()) {
                    String err = connectError[0] != null ? connectError[0] : "语音合成超时或失败";
                    mainHandler.post(() -> callback.onError(err));
                    return;
                }

                // 将 PCM 转换为 WAV 并播放
                byte[] pcmData = pcmBuffer.toByteArray();
                if (pcmData.length == 0) {
                    mainHandler.post(() -> callback.onError("未收到音频数据"));
                    return;
                }

                String wavPath = pcmToWavFile(pcmData);
                if (wavPath == null) {
                    mainHandler.post(() -> callback.onError("音频转换失败"));
                    return;
                }

                playFile(wavPath, callback);

            } catch (Exception e) {
                Log.e(TAG, "TTS error: " + e.getMessage());
                mainHandler.post(() -> callback.onError("语音合成出错，请重试"));
                closeWebSocket();
            }
        });
    }

    public void stop() {
        stopCurrent();
    }

    public void release() {
        stopCurrent();
        closeWebSocket();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 发送文本合成请求（WebSocket text 帧）
     */
    private void sendSynthesisRequest(WebSocket ws, String text) {
        try {
            String encodedText = Base64.encodeToString(text.getBytes("UTF-8"), Base64.NO_WRAP);

            JSONObject req = new JSONObject();
            req.put("aue", 0);                              // PCM
            req.put("auf", "audio/L16;rate=24000");         // 24kHz
            req.put("vcn", AiConfig.VIVO_TTS_VOICE);
            req.put("speed", 50);
            req.put("volume", 50);
            req.put("text", encodedText);
            req.put("encoding", "utf8");
            req.put("reqId", System.currentTimeMillis());

            ws.send(req.toString());
            Log.d(TAG, "TTS synthesis request sent, vcn=" + AiConfig.VIVO_TTS_VOICE);
        } catch (Exception e) {
            Log.e(TAG, "sendSynthesisRequest error: " + e.getMessage());
        }
    }

    private void closeWebSocket() {
        if (webSocket != null) {
            try {
                webSocket.close(1000, "session ended");
            } catch (Exception e) {
                Log.e(TAG, "closeWebSocket error: " + e.getMessage());
            }
            webSocket = null;
        }
    }

    private void stopCurrent() {
        mainHandler.post(() -> {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.reset();
                    mediaPlayer.release();
                } catch (Exception e) {
                    Log.w(TAG, "stopCurrent error: " + e.getMessage());
                }
                mediaPlayer = null;
            }
            isPlaying = false;
        });
    }

    /**
     * 将 PCM 数据转换为 WAV 文件
     */
    private String pcmToWavFile(byte[] pcmData) {
        FileOutputStream fos = null;
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File tempFile = new File(tempDir, "tts_" + UUID.randomUUID().toString() + ".wav");
            fos = new FileOutputStream(tempFile);

            int channels = 1;
            int sampleRate = AiConfig.VIVO_TTS_SAMPLE_RATE;
            int bitsPerSample = 16;
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int blockAlign = channels * bitsPerSample / 8;
            int dataSize = pcmData.length;
            int fileSize = 36 + dataSize;

            // WAV header
            fos.write("RIFF".getBytes());
            fos.write(intToByteArray(fileSize));
            fos.write("WAVE".getBytes());
            fos.write("fmt ".getBytes());
            fos.write(intToByteArray(16));                   // PCM
            fos.write(shortToByteArray((short) 1));          // PCM format
            fos.write(shortToByteArray((short) channels));
            fos.write(intToByteArray(sampleRate));
            fos.write(intToByteArray(byteRate));
            fos.write(shortToByteArray((short) blockAlign));
            fos.write(shortToByteArray((short) bitsPerSample));
            fos.write("data".getBytes());
            fos.write(intToByteArray(dataSize));
            fos.write(pcmData);
            fos.flush();

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "pcmToWavFile error: " + e.getMessage());
            return null;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }

    private byte[] intToByteArray(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private byte[] shortToByteArray(short value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }

    /**
     * 通过文件路径播放音频
     */
    private void playFile(String filePath, TtsCallback callback) {
        mainHandler.post(() -> {
            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(filePath);
                mediaPlayer.setOnPreparedListener(mp -> {
                    isPlaying = true;
                    callback.onStart();
                    mp.start();
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    isPlaying = false;
                    callback.onComplete();
                    releaseMediaPlayer(mp);
                    new File(filePath).delete();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    isPlaying = false;
                    Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                    callback.onError("音频播放失败");
                    releaseMediaPlayer(mp);
                    new File(filePath).delete();
                    return true;
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Log.e(TAG, "playFile error: " + e.getMessage());
                callback.onError("音频播放出错");
                new File(filePath).delete();
            }
        });
    }

    private void releaseMediaPlayer(MediaPlayer mp) {
        try {
            mp.reset();
            mp.release();
        } catch (Exception e) {
            Log.w(TAG, "releaseMediaPlayer error: " + e.getMessage());
        }
    }
}
