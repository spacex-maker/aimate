package com.openforge.aimate.office;

import com.openforge.aimate.domain.AgentDailyPoints;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentDailyPointsRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AgentDailyPointsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AgentDailyPointsRepository dailyPointsRepository;
    private final UserAgentRepository userAgentRepository;

    @Transactional(readOnly = true)
    public long spentToday(Long userAgentId) {
        if (userAgentId == null) return 0L;
        return dailyPointsRepository.findByUserAgentIdAndDay(userAgentId, today())
                .map(AgentDailyPoints::getSpentPoints)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public void assertWithinDailyLimit(Long userAgentId, long upcomingAmount) {
        if (userAgentId == null || upcomingAmount <= 0) return;
        UserAgent ua = userAgentRepository.findById(userAgentId).orElse(null);
        if (ua == null || ua.getDailyPointsLimit() == null || ua.getDailyPointsLimit() <= 0) return;
        long spent = spentToday(userAgentId);
        if (spent + upcomingAmount > ua.getDailyPointsLimit()) {
            throw new AgentDailyLimitExceededException(
                    "该 Agent 今日积分已达上限（" + ua.getDailyPointsLimit() + "），请明日再试或调高限额");
        }
    }

    @Transactional
    public void recordSpend(Long userId, Long userAgentId, long amount) {
        if (userAgentId == null || amount <= 0) return;
        LocalDate day = today();
        AgentDailyPoints row = dailyPointsRepository.findByUserAgentIdAndDay(userAgentId, day)
                .orElseGet(() -> {
                    AgentDailyPoints n = new AgentDailyPoints();
                    n.setUserAgentId(userAgentId);
                    n.setUserId(userId != null ? userId : 0L);
                    n.setDay(day);
                    n.setSpentPoints(0);
                    return n;
                });
        if (userId != null) row.setUserId(userId);
        row.setSpentPoints(row.getSpentPoints() + amount);
        dailyPointsRepository.save(row);
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
