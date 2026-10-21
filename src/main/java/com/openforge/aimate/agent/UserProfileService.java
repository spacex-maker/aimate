package com.openforge.aimate.agent;

import com.openforge.aimate.domain.UserProfile;
import com.openforge.aimate.repository.UserProfileRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final SecretaryAgentService secretaryAgentService;

    public UserProfileService(
            UserProfileRepository profileRepository,
            @Lazy SecretaryAgentService secretaryAgentService
    ) {
        this.profileRepository = profileRepository;
        this.secretaryAgentService = secretaryAgentService;
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> find(Long userId) {
        return profileRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view(Long userId) {
        UserProfile p = profileRepository.findByUserId(userId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        if (p == null) {
            m.put("completed", false);
            m.put("occupation", null);
            m.put("age", null);
            m.put("gender", null);
            m.put("bio", null);
            return m;
        }
        m.put("completed", p.isCompleted());
        m.put("occupation", p.getOccupation());
        m.put("age", p.getAge());
        m.put("gender", p.getGender());
        m.put("bio", p.getBio());
        m.put("extraJson", p.getExtraJson());
        return m;
    }

    @Transactional
    public Map<String, Object> upsert(Long userId, ProfileBody body) {
        UserProfile p = profileRepository.findByUserId(userId).orElseGet(() -> {
            UserProfile n = new UserProfile();
            n.setUserId(userId);
            return n;
        });
        if (body.occupation() != null) p.setOccupation(trim(body.occupation(), 64));
        if (body.age() != null) {
            if (body.age() < 1 || body.age() > 120) {
                throw new IllegalArgumentException("年龄不合法");
            }
            p.setAge(body.age());
        }
        if (body.gender() != null) p.setGender(trim(body.gender(), 32));
        if (body.bio() != null) p.setBio(trim(body.bio(), 512));
        if (body.extraJson() != null) p.setExtraJson(body.extraJson());

        boolean complete = StringUtils.hasText(p.getOccupation())
                && p.getAge() != null
                && StringUtils.hasText(p.getGender());
        p.setCompleted(complete);
        profileRepository.save(p);

        if (complete) {
            secretaryAgentService.ensureSecretaryAfterProfile(userId);
        }
        return view(userId);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public record ProfileBody(String occupation, Integer age, String gender, String bio, String extraJson) {}
}
