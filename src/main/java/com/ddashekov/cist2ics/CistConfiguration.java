package com.ddashekov.cist2ics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class CistConfiguration {

    /**
     * Sets short timeouts, because CIST hosts often accept no connections at all.
     */
    @Bean
    RestClientCustomizer cistTimeouts(@Value("${cist.connect-timeout}") Duration connectTimeout,
                                      @Value("${cist.read-timeout}") Duration readTimeout) {
        return builder -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
