package com.vulmo.a11y;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class A11yApplication {

    public static void main(String[] args) {
        SpringApplication.run(A11yApplication.class, args);
    }
}
