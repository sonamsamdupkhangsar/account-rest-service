package me.sonam.account.config;

import me.sonam.account.handler.UserAccountService;
import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("!localdevtest")
@Configuration
public class WebClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientConfig.class);
    @LoadBalanced
    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning load balanced webclient part");
        return WebClient.builder();
    }
    @LoadBalanced
    @Bean("noFilter")
    public WebClient.Builder webClientBuilderNoFilter() {
        LOG.info("returning for noFilter load balanced webclient part");
        return WebClient.builder();
    }

    @Bean
    public ReactiveRequestContextHolder reactiveRequestContextHolder() {
        return new ReactiveRequestContextHolder(webClientBuilderNoFilter());
    }

    @Bean
    public UserAccountService userAccountService() {
        return new UserAccountService(webClientBuilder());
    }
}