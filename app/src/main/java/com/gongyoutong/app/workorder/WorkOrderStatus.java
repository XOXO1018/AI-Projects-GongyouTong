package com.gongyoutong.app.workorder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态枚举
 * 定义工单的合法状态及状态流转规则
 */
public enum WorkOrderStatus {
    PENDING("待接单"),
    ACCEPTED("已接单"),
    DEPARTED("已出发"),
    ARRIVED("已到场"),
    REPAIRING("维修中"),
    VERIFYING("待验收"),
    COMPLETED("已完成"),
    EXCEPTION("异常");

    private final String displayName;

    WorkOrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 合法状态流转映射 */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(PENDING, Set.of(ACCEPTED, EXCEPTION));
        TRANSITIONS.put(ACCEPTED, Set.of(DEPARTED, EXCEPTION));
        TRANSITIONS.put(DEPARTED, Set.of(ARRIVED, EXCEPTION));
        TRANSITIONS.put(ARRIVED, Set.of(REPAIRING, EXCEPTION));
        TRANSITIONS.put(REPAIRING, Set.of(VERIFYING, EXCEPTION));
        TRANSITIONS.put(VERIFYING, Set.of(COMPLETED, EXCEPTION));
        TRANSITIONS.put(EXCEPTION, Set.of(REPAIRING));
    }

    /**
     * 判断当前状态是否可以流转到目标状态
     * @param target 目标状态
     * @return true 表示可以流转
     */
    public boolean canTransitionTo(WorkOrderStatus target) {
        Set<WorkOrderStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * 从字符串解析状态枚举
     * 支持枚举名（如 PENDING）和显示名（如 待接单）
     * @param status 状态字符串
     * @return 对应的枚举值，无法匹配时返回 PENDING
     */
    public static WorkOrderStatus fromString(String status) {
        if (status == null || status.isEmpty()) {
            return PENDING;
        }
        for (WorkOrderStatus s : values()) {
            if (s.name().equals(status) || s.displayName.equals(status)) {
                return s;
            }
        }
        return PENDING;
    }
}
