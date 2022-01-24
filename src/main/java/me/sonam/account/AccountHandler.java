package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handler
 */
@Component
public class AccountHandler implements AccountBehaviors {
    private static final Logger LOG = LoggerFactory.getLogger(AccountHandler.class);

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public Mono<ServerResponse> isAccountActive(ServerRequest serverRequest) {
        LOG.info("checking account active status for userId");

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).
                body(accountRepository.existsByUserIdAndActiveTrue(
                        UUID.fromString(serverRequest.pathVariable("userId"))), Boolean.class)
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> getById(ServerRequest serverRequest) {
        LOG.error("getById not implemented");
        return null;
    }

    @Override
    public Mono<ServerResponse> activateAccount(ServerRequest serverRequest) {
        LOG.info("checking account active status");

        return accountRepository.activeAccount(UUID
                        .fromString(serverRequest.pathVariable("userId")),
                LocalDateTime.now())
                .flatMap(a -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(a))
                .onErrorResume(e -> Mono.just("Error: "+ e.getMessage())
                .flatMap(s -> ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(s)));
    }


}
