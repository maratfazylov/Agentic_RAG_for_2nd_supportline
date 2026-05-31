package com.hybridrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HybridRagAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HybridRagAgentApplication.class, args);
    }
}
