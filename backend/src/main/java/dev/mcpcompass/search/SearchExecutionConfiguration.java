package dev.mcpcompass.search;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
class SearchExecutionConfiguration {

    @Bean(name = "searchRetrievalExecutor", destroyMethod = "close")
    ExecutorService searchRetrievalExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
