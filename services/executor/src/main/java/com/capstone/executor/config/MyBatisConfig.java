package com.capstone.executor.config;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// MyBatis configuration for mapper scanning
@Configuration(proxyBeanMethods = false)
public class MyBatisConfig {
    
    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.capstone.executor.mapper");
        return configurer;
    }
}
