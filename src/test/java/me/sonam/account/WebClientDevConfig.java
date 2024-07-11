package me.sonam.account;

import me.sonam.account.handler.UserAccountService;
import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("localdevtest")
@Configuration
public class WebClientDevConfig {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientDevConfig.class);
    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning non-loadbalanced webclient");
        return WebClient.builder();
    }

    @Bean
    public ReactiveRequestContextHolder reactiveRequestContextHolder() {
        return new ReactiveRequestContextHolder(webClientBuilder());
    }
    @Bean
    public UserAccountService userAccountService() {
        return new UserAccountService(webClientBuilder());
    }

}
