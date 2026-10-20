package com.umdc.smartkin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.umdc.smartkin.client")
@SpringBootApplication(
        scanBasePackages = {
                "com.umdc.commons.services",
                "com.umdc.smartkin",
                "com.umdc.security"
        }
)
public class SmartKinBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartKinBackendApplication.class, args);
    }
}
