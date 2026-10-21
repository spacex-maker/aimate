package com.openforge.aimate.billing;

import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.PointLedgerRepository;
import com.openforge.aimate.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointsService {

    public static final String CFG_REGISTER_GIFT = "CHAT_REGISTER_GIFT_POINTS";

    private final UserRepository userRepository;
    private final PointLedgerRepository ledgerRepository;
    private final SystemConfigService systemConfigService;

    public PointsService(
            UserRepository userRepository,
            PointLedgerRepository ledgerRepository,
            SystemConfigService systemConfigService
    ) {
        this.userRepository = userRepository;
        this.ledgerRepository = ledgerRepository;
        this.systemConfigService = systemConfigService;
    }

    @Transactional(readOnly = true)
    public long balance(Long userId) {
        if (userId == null) return 0L;
        return userRepository.findById(userId).map(User::getPointsBalance).orElse(0L);
    }

    @Transactional(readOnly = true)
    public void assertCanChat(Long userId) {
        if (userId == null) {
            throw new InsufficientPointsException("请先登录");
        }
        long bal = balance(userId);
        if (bal < 1) {
            throw new InsufficientPointsException("积分不足，请先充值后再对话");
        }
    }

    @Transactional
    public long deduct(Long userId, long amount, String reason, String refType, String refId,
                       String sessionId, Long llmCallLogId, String remark) {
        if (userId == null || amount <= 0) return balance(userId);
        int updated = userRepository.deductPointsIfEnough(userId, amount);
        if (updated == 0) {
            throw new InsufficientPointsException("积分不足，本轮已中断");
        }
        long after = balance(userId);
        PointLedger row = new PointLedger();
        row.setUserId(userId);
        row.setDelta(-amount);
        row.setBalanceAfter(after);
        row.setReason(reason);
        row.setRefType(refType);
        row.setRefId(refId);
        row.setSessionId(sessionId);
        row.setLlmCallLogId(llmCallLogId);
        row.setRemark(remark);
        ledgerRepository.save(row);
        return after;
    }

    @Transactional
    public long credit(Long userId, long amount, String reason, String refType, String refId, String remark) {
        if (userId == null || amount <= 0) return balance(userId);
        userRepository.addPoints(userId, amount);
        long after = balance(userId);
        PointLedger row = new PointLedger();
        row.setUserId(userId);
        row.setDelta(amount);
        row.setBalanceAfter(after);
        row.setReason(reason);
        row.setRefType(refType);
        row.setRefId(refId);
        row.setRemark(remark);
        ledgerRepository.save(row);
        return after;
    }

    @Transactional
    public void grantRegisterGiftIfConfigured(Long userId) {
        if (userId == null) return;
        long gift = systemConfigService.get(CFG_REGISTER_GIFT)
                .map(s -> {
                    try { return Long.parseLong(s.trim()); }
                    catch (Exception e) { return 100L; }
                })
                .orElse(100L);
        if (gift <= 0) return;
        credit(userId, gift, PointLedger.SYSTEM_GIFT, "REGISTER", String.valueOf(userId), "新用户注册赠送");
    }
}
