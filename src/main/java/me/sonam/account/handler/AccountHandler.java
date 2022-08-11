package me.sonam.account.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * AccountHandler implements the Handler interface.
 * It handles exception thrown by the UserAccount implementation
 * and wraps them in a badrequest message.
 */
@Component
public class AccountHandler implements Handler {
    private static final Logger LOG = LoggerFactory.getLogger(AccountHandler.class);

    @Autowired
    private UserAccount userAccount;

    @Override
    public Mono<ServerResponse> isAccountActive(ServerRequest serverRequest) {
        LOG.info("isAccountActive");

        return userAccount.isAccountActive(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
        .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> activateAccount(ServerRequest serverRequest) {
        LOG.info("activate account");
        return userAccount.activateAccount(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> emailActivationLink(ServerRequest serverRequest) {
        LOG.info("email activation link handler");
        return userAccount.emailActivationLink(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> createAccount(ServerRequest serverRequest) {
        LOG.info("create initial account");
        return userAccount.createAccount(serverRequest).flatMap(s ->
                ServerResponse.created(URI.create("/accounts/")).contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> emailMySecret(ServerRequest serverRequest) {
        LOG.info("email my secret");
        return userAccount.emailMySecret(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> sendLoginId(ServerRequest serverRequest) {
        LOG.info("send login id");
        return userAccount.sendAuthenticationId(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> validateEmailLoginSecret(ServerRequest serverRequest) {
        LOG.info("validate login secret");
        return userAccount.validateEmailLoginSecret(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(s))
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
                        .fromValue(e.getMessage())));
    }
}
