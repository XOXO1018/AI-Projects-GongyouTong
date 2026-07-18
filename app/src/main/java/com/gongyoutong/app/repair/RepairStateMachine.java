package com.gongyoutong.app.repair;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 维修状态机 — 7 状态流转 + 灵活跳转 + Observer 通知
 * 线程安全：synchronized 保护状态切换
 */
public class RepairStateMachine {

    private static final String TAG = "RepairStateMachine";

    /** 当前状态 */
    private RepairState currentState = RepairState.DEVICE_IDENTIFY;

    /** 维修步骤列表 */
    private final List<RepairStep> steps = new ArrayList<>();

    /** 当前步骤索引（0-based） */
    private int currentStepIndex = 0;

    /** 状态变化监听器列表（线程安全） */
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    /** 主线程 Handler */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ========== 状态流转 ==========

    /**
     * 切换到指定状态（线程安全）
     *
     * @param newState 目标状态
     */
    public synchronized void transitionTo(RepairState newState) {
        if (newState == currentState) {
            return;
        }
        Log.d(TAG, "状态切换: " + currentState + " → " + newState);
        RepairState oldState = currentState;
        currentState = newState;
        notifyStateChanged(oldState, newState);
    }

    /**
     * 跳过当前步骤
     */
    public synchronized void skipCurrent() {
        if (steps.isEmpty()) {
            return;
        }
        RepairStep step = steps.get(currentStepIndex);
        step.setSkipped(true);
        Log.d(TAG, "跳过步骤 " + (currentStepIndex + 1) + ": " + step.getTitle());

        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
            notifyStepChanged();
        } else {
            transitionTo(RepairState.COMPLETION_CHECK);
        }
    }

    /**
     * 回退到上一步
     */
    public synchronized void goBack() {
        if (currentStepIndex > 0) {
            // 取消当前步骤的完成状态
            RepairStep current = steps.get(currentStepIndex);
            current.setCompleted(false);
            currentStepIndex--;
            Log.d(TAG, "回退到步骤 " + (currentStepIndex + 1));
            notifyStepChanged();
            if (currentState != RepairState.STEP_GUIDE) {
                transitionTo(RepairState.STEP_GUIDE);
            }
        }
    }

    /**
     * 完成当前步骤，进入下一步
     */
    public synchronized void completeCurrent() {
        if (steps.isEmpty()) {
            return;
        }
        RepairStep step = steps.get(currentStepIndex);
        step.setCompleted(true);
        step.setSkipped(false);
        Log.d(TAG, "完成步骤 " + (currentStepIndex + 1) + ": " + step.getTitle());

        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
            notifyStepChanged();
        } else {
            transitionTo(RepairState.COMPLETION_CHECK);
        }
    }

    /**
     * 跳转到指定步骤（灵活跳转）
     *
     * @param stepIndex 目标步骤索引（0-based）
     */
    public synchronized void jumpToStep(int stepIndex) {
        if (stepIndex >= 0 && stepIndex < steps.size()) {
            currentStepIndex = stepIndex;
            Log.d(TAG, "跳转到步骤 " + (stepIndex + 1));
            notifyStepChanged();
            if (currentState != RepairState.STEP_GUIDE) {
                transitionTo(RepairState.STEP_GUIDE);
            }
        }
    }

    // ========== 步骤管理 ==========

    /**
     * 设置维修步骤列表并重置到第一步
     *
     * @param newSteps 新的步骤列表
     */
    public synchronized void setSteps(List<RepairStep> newSteps) {
        steps.clear();
        if (newSteps != null) {
            steps.addAll(newSteps);
        }
        currentStepIndex = 0;
    }

    /**
     * 获取当前步骤
     *
     * @return 当前步骤，无步骤时返回 null
     */
    public synchronized RepairStep getCurrentStep() {
        if (steps.isEmpty() || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    /**
     * 获取步骤列表副本
     *
     * @return 步骤列表的防御性拷贝
     */
    public synchronized List<RepairStep> getSteps() {
        return new ArrayList<>(steps);
    }

    /**
     * 获取当前步骤索引
     *
     * @return 当前步骤索引（0-based）
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    /**
     * 获取总步骤数
     *
     * @return 步骤总数
     */
    public int getTotalSteps() {
        return steps.size();
    }

    /**
     * 获取当前状态
     *
     * @return 当前状态枚举值
     */
    public RepairState getCurrentState() {
        return currentState;
    }

    // ========== Observer ==========

    /**
     * 添加状态变化监听器
     *
     * @param listener 监听器实例
     */
    public void addListener(StateChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除状态变化监听器
     *
     * @param listener 监听器实例
     */
    public void removeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器状态已变化（回调到主线程）
     */
    private void notifyStateChanged(final RepairState oldState, final RepairState newState) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (StateChangeListener listener : listeners) {
                    listener.onStateChanged(oldState, newState);
                }
            }
        });
    }

    /**
     * 通知所有监听器步骤已变化（回调到主线程）
     */
    private void notifyStepChanged() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (StateChangeListener listener : listeners) {
                    listener.onStepChanged(currentStepIndex, getCurrentStep());
                }
            }
        });
    }

    // ========== Observer 接口 ==========

    /**
     * 状态变化监听器接口
     */
    public interface StateChangeListener {

        /**
         * 状态变化回调
         *
         * @param oldState 旧状态
         * @param newState 新状态
         */
        void onStateChanged(RepairState oldState, RepairState newState);

        /**
         * 步骤变化回调
         *
         * @param stepIndex 新步骤索引（0-based）
         * @param step      新步骤对象
         */
        void onStepChanged(int stepIndex, RepairStep step);
    }
}
