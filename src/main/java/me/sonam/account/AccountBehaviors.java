package me.sonam.account;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public interface AccountBehaviors {
    Mono<ServerResponse> isAccountActive(ServerRequest serverRequest);
    Mono<ServerResponse> getById(ServerRequest serverRequest);
    Mono<ServerResponse> activateAccount(ServerRequest serverRequest);
}
