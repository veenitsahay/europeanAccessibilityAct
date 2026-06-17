package com.vulmo.a11y.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * One scan at a time by default: each scan spawns a headless Chromium that
 * wants 300-500 MB. Raise core/max pool size only on boxes with 4 GB+.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "scanExecutor")
    public Executor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("scan-");
        executor.initialize();
        return executor;
    }
}
