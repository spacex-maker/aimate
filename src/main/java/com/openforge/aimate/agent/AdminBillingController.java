package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.BillingUsageDetail;
import com.openforge.aimate.agent.dto.BillingUsageResponse;
import com.openforge.aimate.agent.dto.BillingUsageSummary;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.LlmCallLogRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
public class AdminBillingController {

    private final LlmCallLogRepository callLogRepository;
    private final UserRepository userRepository;

    @GetMapping("/usage")
    public ResponseEntity<BillingUsageResponse> usage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<Object[]> rows = callLogRepository.aggregateUsage(userId, from, to);
        Map<Long, User> users = userRepository.findAllById(
                rows.stream().map(r -> (Long) r[0]).toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<BillingUsageSummary> summaries = new ArrayList<>();
        long grandTokens = 0;
        double grandCost = 0;
        for (Object[] r : rows) {
            Long uid = (Long) r[0];
            long prompt = ((Number) r[1]).longValue();
            long completion = ((Number) r[2]).longValue();
            long total = ((Number) r[3]).longValue();
            double cost = ((Number) r[4]).doubleValue();
            long calls = ((Number) r[5]).longValue();
            User u = users.get(uid);
            summaries.add(new BillingUsageSummary(
                    uid,
                    u != null ? u.getUsername() : String.valueOf(uid),
                    calls,
                    prompt,
                    completion,
                    total,
                    cost
            ));
            grandTokens += total;
            grandCost += cost;
        }
        return ResponseEntity.ok(new BillingUsageResponse(summaries, grandTokens, grandCost));
    }

    @GetMapping("/usage/{userId}")
    public ResponseEntity<List<BillingUsageDetail>> usageDetail(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<LlmCallLog> logs = callLogRepository.findDetails(
                userId, from, to, PageRequest.of(0, Math.min(Math.max(limit, 1), 500)));
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        List<BillingUsageDetail> details = logs.stream()
                .map(l -> new BillingUsageDetail(
                        l.getId(),
                        l.getProvider(),
                        l.getModel(),
                        l.getCallType() != null ? l.getCallType().name() : null,
                        l.getSessionId(),
                        l.getPromptTokens(),
                        l.getCompletionTokens(),
                        l.getTotalTokens(),
                        l.getEstimatedCostUsd(),
                        l.isSuccess(),
                        l.getCreateTime() != null ? l.getCreateTime().format(fmt) : null
                ))
                .toList();
        return ResponseEntity.ok(details);
    }
}
