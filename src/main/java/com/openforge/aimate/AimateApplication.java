package com.openforge.aimate;

import com.openforge.aimate.agent.ScriptToolProperties;
import com.openforge.aimate.memory.VectorDbProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({
        VectorDbProperties.class,
        ScriptToolProperties.class,
        com.openforge.aimate.agent.ScriptDockerProperties.class,
        com.openforge.aimate.config.OidcProperties.class,
        com.openforge.aimate.storage.ChatCosProperties.class,
        com.openforge.aimate.office.OfficeCvmProperties.class
})
public class AimateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AimateApplication.class, args);
    }
}
