package com.exchangerate.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExchangeRateManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExchangeRateManagerApplication.class, args);
    }
}
