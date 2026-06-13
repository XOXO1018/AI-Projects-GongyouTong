package com.gongyoutong.app.repair;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.ai.RepairLlmService;
import com.gongyoutong.app.ai.VivoAsrService;
import com.gongyoutong.app.ai.VivoTtsService;

import java.util.ArrayList;
import java.util.List;

/**
 * 语音交互管理器 —— 协调 ASR + TTS + 意图识别
 *
 * 职责：
 * 1. 管理语音输入（按钮按下 → ASR → 最终文本）
 * 2. 将识别文本送入 LLM 进行意图识别
 * 3. 根据意图自动执行操作（播报指导、确认完成、记录问题等）
 * 4. 通过 TTS 播报反馈给用户
 *
 * 线程安全：所有回调通过 mainHandler 切换到主线程
 */
public class VoiceInteractionManager {
    private static final String TAG = "VoiceInteractionManager";

    private final VivoAsrService asrService;
    private final VivoTtsService ttsService;
    private final RepairLlmService llmService;
    private final RepairStateMachine stateMachine;
    private final Handler mainHandler;

    private VoiceCallback callback;
    private boolean ttsEnabled = true;
    private boolean isListening = false;

    /** 语音对话上下文（最近 3 轮 = 6 条消息） */
    private final List<String> dialogueHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 6;

    /** 回调接口：供 UI 层监听语音交互事件 */
    public interface VoiceCallback {
        /** ASR 识别到文本（含中间结果和最终结果） */
        void onRecognized(String text, boolean isFinal);
        /** 意图识别完成 */
        void onIntent(RepairIntention intention);
        /** TTS 开始播报 */
        void onTtsStart();
        /** TTS 播报完成 */
        void onTtsComplete();
        /** 发生错误 */
        void onError(String msg);
    }

    /**
     * 构造语音交互管理器
     *
     * @param stateMachine 维修状态机，用于获取当前上下文和执行操作
     */
    public VoiceInteractionManager(RepairStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.asrService = new VivoAsrService();
        this.ttsService = VivoTtsService.getInstance();
        this.llmService = RepairLlmService.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==================== 设置回调 & 开关 ====================

    /** 设置语音事件回调 */
    public void setCallback(VoiceCallback cb) {
        this.callback = cb;
    }

    /** 设置 TTS 播报开关 */
    public void setTtsEnabled(boolean enabled) {
        this.ttsEnabled = enabled;
        if (!enabled) {
            stopSpeaking();
        }
    }

    /** 查询 TTS 是否启用 */
    public boolean isTtsEnabled() {
        return ttsEnabled;
    }

    /** 是否正在监听语音输入 */
    public boolean isListening() {
        return isListening;
    }

    // ==================== 语音输入 ====================

    /**
     * 开始监听（按住按钮时调用）
     * 启动 ASR 识别，识别到的文本在回调中处理
     */
    public void startListening() {
        if (isListening) {
            return;
        }
        isListening = true;
        asrService.startRecognition(new VivoAsrService.AsrCallback() {
            @Override
            public void onResult(String text, boolean isFinal) {
                if (callback != null) {
                    callback.onRecognized(text, isFinal);
                }
                if (isFinal && text != null && !text.isEmpty()) {
                    processVoiceInput(text);
                }
            }

            @Override
            public void onError(String errorMessage) {
                isListening = false;
                if (callback != null) {
                    callback.onError("语音识别失败: " + errorMessage);
                }
            }

            @Override
            public void onRecordingStateChanged(boolean isRecording) {
                // 内部追踪录音状态
                if (!isRecording) {
                    isListening = false;
                }
            }
        });
    }

    /**
     * 停止监听（松开按钮时调用）
     */
    public void stopListening() {
        isListening = false;
        asrService.stopRecognition();
    }

    /**
     * 处理语音输入：意图识别 → 分派动作
     */
    private void processVoiceInput(String text) {
        isListening = false;
        addToHistory("user", text);
        String stateContext = stateMachine.getCurrentState().name();

        llmService.recognizeIntent(text, stateContext, new RepairLlmService.IntentCallback() {
            @Override
            public void onIntent(RepairIntention intention) {
                addToHistory("intent", intention.getType().name());
                dispatchIntention(intention);
                if (callback != null) {
                    callback.onIntent(intention);
                }
            }

            @Override
            public void onError(String msg) {
                Log.e(TAG, "意图识别失败: " + msg);
                // 降级：直接作为问题报告
                RepairIntention fallback = new RepairIntention(
                        RepairIntention.Type.REPORT_ISSUE, 0.5f, text
                );
                dispatchIntention(fallback);
            }
        });
    }

    /**
     * 根据意图分派动作
     */
    private void dispatchIntention(RepairIntention intention) {
        if (intention == null || intention.getType() == null) {
            return;
        }

        switch (intention.getType()) {
            case ASK_GUIDANCE:
                // 请求指导 → 播报当前步骤说明
                RepairStep step = stateMachine.getCurrentStep();
                if (step != null) {
                    speak(step.getDescription());
                } else {
                    speak("当前没有可用的维修步骤");
                }
                break;

            case REPORT_PROGRESS:
            case CONFIRM_COMPLETE:
                // 报告进度/确认完成 → 完成当前步骤，进入下一步
                speak("好的，操作已确认");
                stateMachine.completeCurrent();
                // 完成后播报下一步
                announceNextStep();
                break;

            case ASK_TOOL:
                // 询问工具 → 播报所需工具
                RepairStep current = stateMachine.getCurrentStep();
                if (current != null && current.getToolRequired() != null
                        && !current.getToolRequired().isEmpty()) {
                    speak("这一步需要用到 " + current.getToolRequired());
                } else {
                    speak("当前步骤不需要特殊工具");
                }
                break;

            case REPORT_ISSUE:
                // 报告问题 → 进入错误纠正状态
                speak("收到，已记录您反馈的问题");
                stateMachine.transitionTo(RepairState.ERROR_CORRECT);
                break;

            case OTHER:
            default:
                speak("请再说一次");
                break;
        }
    }

    // ==================== TTS 播报 ====================

    /**
     * 通过 TTS 播报文本
     *
     * @param text 要播报的文本
     */
    public void speak(String text) {
        if (!ttsEnabled || text == null || text.trim().isEmpty()) {
            return;
        }
        addToHistory("assistant", text);
        ttsService.speak(text, new VivoTtsService.TtsCallback() {
            @Override
            public void onStart() {
                if (callback != null) {
                    callback.onTtsStart();
                }
            }

            @Override
            public void onComplete() {
                if (callback != null) {
                    callback.onTtsComplete();
                }
            }

            @Override
            public void onError(String msg) {
                Log.e(TAG, "TTS播报失败: " + msg);
            }
        });
    }

    /** 停止当前 TTS 播报 */
    public void stopSpeaking() {
        ttsService.stop();
    }

    // ==================== 资源管理 ====================

    /**
     * 释放所有资源
     */
    public void release() {
        stopListening();
        stopSpeaking();
        dialogueHistory.clear();
        asrService.destroy();
    }

    /**
     * 获取对话历史副本
     *
     * @return 对话历史列表的防御性拷贝
     */
    public List<String> getDialogueHistory() {
        return new ArrayList<>(dialogueHistory);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 完成当前步骤后播报下一步
     */
    private void announceNextStep() {
        RepairStep nextStep = stateMachine.getCurrentStep();
        if (nextStep != null) {
            speak("下一步：" + nextStep.getTitle() + "。" + nextStep.getDescription());
        } else {
            speak("所有步骤已完成，请确认维修结果");
        }
    }

    /**
     * 添加条目到对话历史，保持最多 MAX_HISTORY 条
     */
    private void addToHistory(String role, String text) {
        dialogueHistory.add(role + ": " + text);
        if (dialogueHistory.size() > MAX_HISTORY) {
            int removeCount = dialogueHistory.size() - MAX_HISTORY;
            dialogueHistory.subList(0, removeCount).clear();
        }
    }
}
