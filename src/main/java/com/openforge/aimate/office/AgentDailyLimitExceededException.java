package com.openforge.aimate.office;

import com.openforge.aimate.billing.InsufficientPointsException;

/** 日限额用尽：沿用积分不足中断路径，便于 Agent 循环统一处理。 */
public class AgentDailyLimitExceededException extends InsufficientPointsException {
    public AgentDailyLimitExceededException(String message) {
        super(message);
    }
}
