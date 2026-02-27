package com.openforge.aimate;

import com.openforge.aimate.memory.MilvusProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// Register ConfigurationProperties globally so they are available
// regardless of whether the conditional Milvus beans are loaded.
@SpringBootApplication
@EnableConfigurationProperties(MilvusProperties.class)
public class AimateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AimateApplication.class, args);
    }
}
