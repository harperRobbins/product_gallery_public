package com.szwego.gallery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "wsAlbumCrawlExecutor")
    public ThreadPoolTaskExecutor wsAlbumCrawlExecutor(WsAlbumProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = properties.getCrawlThreadPoolSize() == null ? 2 : Math.max(1, properties.getCrawlThreadPoolSize());
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(Math.max(poolSize, poolSize * 2));
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ws-album-crawl-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "wsAlbumImportExecutor")
    public ThreadPoolTaskExecutor wsAlbumImportExecutor(WsAlbumProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = properties.getCrawlThreadPoolSize() == null ? 2 : Math.max(1, properties.getCrawlThreadPoolSize());
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(Math.max(poolSize, poolSize * 2));
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ws-album-import-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "analyticsExecutor")
    public ThreadPoolTaskExecutor analyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("analytics-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
