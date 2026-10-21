package com.openforge.aimate.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String bootstrapDisplayName;

    public AdminBootstrap(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${aimate.admin.bootstrap-email:}") String bootstrapEmail,
            @Value("${aimate.admin.bootstrap-password:}") String bootstrapPassword,
            @Value("${aimate.admin.bootstrap-display-name:超级管理员}") String bootstrapDisplayName
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapDisplayName = bootstrapDisplayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminAccountRepository.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(bootstrapEmail) || !StringUtils.hasText(bootstrapPassword)) {
            log.warn("admin_users is empty and aimate.admin.bootstrap-* is not set");
            return;
        }
        AdminAccount admin = new AdminAccount();
        admin.setEmail(bootstrapEmail.trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setDisplayName(bootstrapDisplayName);
        admin.setAccountEnabled(true);
        adminAccountRepository.save(admin);
        log.info("Created bootstrap admin account {}", admin.getEmail());
    }
}
