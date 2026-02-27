package com.openforge.aimate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activates Spring Data JPA Auditing so that @CreatedDate / @LastModifiedDate
 * on BaseEntity are automatically populated by the framework.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
