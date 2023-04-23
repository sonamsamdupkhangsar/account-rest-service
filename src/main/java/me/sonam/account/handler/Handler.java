package me.sonam.account.handler;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public interface Handler {
    // returns boolean if account is active
    Mono<ServerResponse> isAccountActive(ServerRequest serverRequest);
    /**
     * if account with userId exists then it will activate account
     * else it will create account and activate it
     * @param serverRequest
     * @return
     */
    Mono<ServerResponse> activateAccount(ServerRequest serverRequest);
    Mono<ServerResponse> emailActivationLink(ServerRequest serverRequest);
    Mono<ServerResponse> createAccount(ServerRequest serverRequest);
    Mono<ServerResponse> emailMySecret(ServerRequest serverRequest);
    Mono<ServerResponse> sendLoginId(ServerRequest serverRequest);
    Mono<ServerResponse> validateEmailLoginSecret(ServerRequest serverRequest);
    Mono<ServerResponse> delete(ServerRequest serverRequest);
    Mono<ServerResponse> updateAuthenticationPassword(ServerRequest serverRequest);
}
