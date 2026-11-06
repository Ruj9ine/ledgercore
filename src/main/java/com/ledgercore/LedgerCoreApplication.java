package com.ledgercore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LedgerCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerCoreApplication.class, args);
    }
}