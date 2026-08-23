package com.exchangerate.manager;

import com.exchangerate.manager.config.ExchangeRateProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ExchangeRateProperties.class)
public class ExchangeRateManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExchangeRateManagerApplication.class, args);
    }
}
