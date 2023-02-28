package me.sonam.account.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
        .onErrorResume(e -> {
            LOG.error("is account active check failed", e);
            return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("error", e.getMessage())));
        });
    }

    @Override
    public Mono<ServerResponse> activateAccount(ServerRequest serverRequest) {
        LOG.info("activate account");
        return userAccount.activateAccount(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("activate account failed", e);
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ServerResponse> emailActivationLink(ServerRequest serverRequest) {
        LOG.info("email activation link handler");
        return userAccount.emailActivationLink(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("email activation link failed", e);
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ServerResponse> createAccount(ServerRequest serverRequest) {
        LOG.info("create initial account");
        return userAccount.createAccount(serverRequest).flatMap(s ->
                ServerResponse.created(URI.create("/accounts/")).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("create account failed: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ServerResponse> emailMySecret(ServerRequest serverRequest) {
        LOG.info("email my secret");
        return userAccount.emailMySecret(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("emailMySecred failed", e);
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ServerResponse> sendLoginId(ServerRequest serverRequest) {
        LOG.info("send login id");
        return userAccount.sendAuthenticationId(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("sengLoginId failed", e);
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ServerResponse> validateEmailLoginSecret(ServerRequest serverRequest) {
        LOG.info("validate login secret");
        return userAccount.validateEmailLoginSecret(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("validateEmailLoginSecret failed", e);
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        LOG.info("delete account using if password secret has expired and account is false");

        return userAccount.delete(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(getMap(Pair.of("message", s))))
                .onErrorResume(e -> {
                    LOG.error("deleted failed", e);
                    return ServerResponse.badRequest().bodyValue(getMap(Pair.of("error", e.getMessage())));
                });
    }

    public static Map<String, String> getMap(Pair<String, String>... pairs){

        Map<String, String> map = new HashMap<>();

        for(Pair<String, String> pair: pairs) {
            map.put(pair.getFirst(), pair.getSecond());
        }
        return map;

    }
}
