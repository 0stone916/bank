package com.bank.bankmock.common.config;

import com.bank.bankmock.common.filter.GlobalSecurityFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<GlobalSecurityFilter> securityFilter() {
        FilterRegistrationBean<GlobalSecurityFilter> registrationBean = new FilterRegistrationBean<>();
        
        // 우리가 만든 필터 주입
        registrationBean.setFilter(new GlobalSecurityFilter());
        
        // 필터가 적용될 URL 패턴 설정 (/* 는 모든 요청에 적용)
        registrationBean.addUrlPatterns("/*");
        
        // 필터 실행 순서 설정 (낮을수록 먼저 실행됨. 보안 필터는 가장 앞단에 위치해야 함)
        registrationBean.setOrder(1);
        
        return registrationBean;
    }
}