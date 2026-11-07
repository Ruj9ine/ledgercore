package com.ledgercore.transaction;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TransactionWebConfig implements WebMvcConfigurer {

    private final RejectedMoneyRequestInterceptor rejectedMoneyRequestInterceptor;

    public TransactionWebConfig(RejectedMoneyRequestInterceptor rejectedMoneyRequestInterceptor) {
        this.rejectedMoneyRequestInterceptor = rejectedMoneyRequestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rejectedMoneyRequestInterceptor).addPathPatterns(
                "/api/v1/wallets/*/deposits",
                "/api/v1/wallets/*/withdrawals",
                "/api/v1/wallets/*/transfers");
    }
}
