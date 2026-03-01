package com.openforge.aimate;

import com.openforge.aimate.agent.ScriptToolProperties;
import com.openforge.aimate.memory.MilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ MilvusProperties.class, ScriptToolProperties.class, com.openforge.aimate.agent.ScriptDockerProperties.class })
public class AimateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AimateApplication.class, args);
    }
}
