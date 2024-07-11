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
        return RouterFunctions
                .route(GET("/accounts/{authenticationId}/active")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::isAccountActive)

                // user will click a link from their inbox that contains a url pointing this
                // endpoint to activate such as 'http://api-gateway:8080/accounts/test6/active/r5DFO6SFx2'
                .andRoute(GET("/accounts/{authenticationId}/active/{secret}")
                    .and(accept(MediaType.APPLICATION_JSON)), handler::activateAccount)

                // called by authorization server
                .andRoute(PUT("/accounts/active/email/{email}/password-secret")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::emailActivationLinkUsingEmail)

                // called by authorization server
                .andRoute(PUT("/accounts/email/{email}/password-secret")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::emailMySecretUsingEmail)


                .andRoute(POST("/accounts/{userId}/{authenticationId}/{email}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::createAccount)

                // called by authorization server
                .andRoute(PUT("/accounts/email/{email}/authentication-id")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::sendLoginId)

                // user will click on link in email and get directed to authorization server and which will
                // call this endpoint to start the process from authorization server
                .andRoute(GET("/accounts/{email}/password-secret/{secret}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::validateEmailLoginSecret)

                .andRoute(DELETE("/accounts/email/{email}")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::delete)

                // authorization server will call this endpoint to update password
                .andRoute(PUT("/accounts/password-secret")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::updateAuthenticationPassword)
                .andRoute(DELETE("/accounts/delete")
                        .and(accept(MediaType.APPLICATION_JSON)), handler::deleteMyData);



    }
}
