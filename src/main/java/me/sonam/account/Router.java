package me.sonam.account;

import me.sonam.account.handler.AccountHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * Set AccountService methods route for checking active and to actiate acccount
 */
@Configuration
public class Router {
    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    @Bean
    public RouterFunction<ServerResponse> route(AccountHandler handler) {
        LOG.info("building router function");
        return RouterFunctions.route(GET("/accounts/active/authenticationId/{authenticationId}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::isAccountActive)
                .andRoute(GET("/accounts/activate/{authenticationId}/{secret}")
                .and(accept(MediaType.APPLICATION_JSON)), handler::activateAccount)
                .andRoute(PUT("/accounts/emailactivationlink/{authenticationId}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::emailActivationLink)
                .andRoute(PUT("/accounts/emailmysecret/{authenticationId}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::emailMySecret)
                .andRoute(POST("/accounts/{authenticationId}/{email}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::createAccount)
                .andRoute(PUT("/accounts/email/authenticationId/{email}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::sendLoginId)
                .andRoute(PUT("/accounts/validate/secret/{authenticationId}/{secret}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::validateEmailLoginSecret)
                .andRoute(DELETE("/accounts/email/{email}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::delete)
                .andRoute(PUT("/accounts/authentications/password")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::updateAuthenticationPassword);

    }
}
