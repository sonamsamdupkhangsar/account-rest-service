package me.sonam.account.handler;

import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

public interface UserAccount {
    // returns boolean if account is active
    Mono<String> isAccountActive(ServerRequest serverRequest);
    /**
     * if account with userId exists then it will activate account
     * else it will create account and activate it
     * @param serverRequest
     * @return
     */
    Mono<String> activateAccount(ServerRequest serverRequest);
    Mono<String> emailActivationLink(ServerRequest serverRequest);
    Mono<String> emailMySecret(ServerRequest serverRequest);
    // internal service
    Mono<String> createAccount(ServerRequest serverRequest);
    //internal service
    Mono<String> deleteAccount(ServerRequest serverRequest);
    Mono<String> sendAuthenticationId(ServerRequest serverRequest);
    Mono<String> validateEmailLoginSecret(ServerRequest serverRequest);
}
