package com.gongyoutong.app.repair;

/**
 * 维修流程状态枚举
 * 定义了从设备识别到报告生成的完整 7 状态流转
 */
public enum RepairState {
    /** 识别设备 — 拍照/扫描获取设备信息 */
    DEVICE_IDENTIFY,
    /** 故障诊断 — AI 分析故障原因 */
    FAULT_DIAGNOSIS,
    /** 步骤指导 — 按步骤引导维修操作 */
    STEP_GUIDE,
    /** 动作验证 — 验证当前步骤操作是否正确 */
    ACTION_VERIFY,
    /** 错误纠正 — 检测到错误时指导修正 */
    ERROR_CORRECT,
    /** 完成确认 — 所有步骤完成后确认 */
    COMPLETION_CHECK,
    /** 报告生成 — 生成维修报告 */
    REPORT_GENERATE
}
