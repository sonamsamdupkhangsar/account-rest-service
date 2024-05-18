package me.sonam.account.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
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

        return userAccount.isAccountActive(serverRequest.pathVariable("authenticationId")).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
        .onErrorResume(e -> {
            LOG.error("is account active check failed", e);
            return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("error", e.getMessage()));
        });
    }

    @Override
    public Mono<ServerResponse> activateAccount(ServerRequest serverRequest) {
        LOG.info("activate account");
        return userAccount.activateAccount(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(s))
                .onErrorResume(e -> {
                    LOG.error("activate account failed: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> emailActivationLinkUsingEmail(ServerRequest serverRequest) {
        LOG.info("email activation link handler using email address");
        return userAccount.emailActivationLinkUsingEmail(serverRequest).flatMap(s ->
                        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("email activation link failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> emailActivationLink(ServerRequest serverRequest) {
        LOG.info("email activation link handler");
        return userAccount.emailActivationLink(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("email activation link failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> createAccount(ServerRequest serverRequest) {
        LOG.info("create initial account");
        return userAccount.createAccount(serverRequest).flatMap(s ->
                ServerResponse.created(URI.create("/accounts/")).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("create account failed: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> emailMySecretUsingEmail(ServerRequest serverRequest) {
        LOG.info("email my secret");
        return userAccount.emailMySecretUsingEmail(serverRequest).flatMap(s ->
                        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("emailMySecret failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> emailMySecret(ServerRequest serverRequest) {
        LOG.info("email my secret");
        return userAccount.emailMySecret(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("emailMySecret failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> sendLoginId(ServerRequest serverRequest) {
        LOG.info("send login id");
        return userAccount.sendAuthenticationId(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("sengLoginId failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> validateEmailLoginSecret(ServerRequest serverRequest) {
        LOG.info("validate login secret");

        String email = serverRequest.pathVariable("email"); //"authenticationId");
        String secret = serverRequest.pathVariable("secret");

        return userAccount.validateEmailLoginSecret(email, secret).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.error("validateEmailLoginSecret failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        LOG.info("delete account using if password secret has expired and account is false");

        return userAccount.delete(serverRequest).flatMap(s ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.warn("deleted failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }

    @Override
    public Mono<ServerResponse> updateAuthenticationPassword(ServerRequest serverRequest) {
        LOG.info("delete account using if password secret has expired and account is false");
        return serverRequest.bodyToMono(Map.class)
                .flatMap(map -> {
                    final String email = map.get("email").toString();
                    final String secret = map.get("secret").toString();
                    final String password = map.get("password").toString();

                    return userAccount.updateAuthenticationPassword(email, secret, password);
                })
                .flatMap(s ->
                    ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("message", s)))
                .onErrorResume(e -> {
                    LOG.warn("deleted failed, error: {}", e.getMessage());
                    return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                });
    }
}
