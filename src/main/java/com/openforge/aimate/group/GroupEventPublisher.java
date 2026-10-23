package com.openforge.aimate.group;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/group.";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(String groupPublicId, String type, Object payload) {
        String destination = TOPIC_PREFIX + groupPublicId;
        try {
            messagingTemplate.convertAndSend(destination, Map.of(
                    "type", type,
                    "groupPublicId", groupPublicId,
                    "payload", payload
            ));
        } catch (Exception e) {
            log.warn("[GroupPublisher] Failed to deliver {} to {}: {}", type, destination, e.getMessage());
        }
    }
}
