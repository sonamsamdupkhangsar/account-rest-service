package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.PasswordSecretRepository;
import me.sonam.account.repo.entity.Account;
import me.sonam.account.repo.entity.PasswordSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;

@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Application.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class DeleteMyDataIntegTest {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteMyDataIntegTest.class);

    @Autowired
    ApplicationContext context;

    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordSecretRepository passwordSecretRepository;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        this.webTestClient = WebTestClient
                .bindToApplicationContext(this.context)
                // add Spring Security test Support
                .apply(springSecurity())
                .configureClient()
                .filter(basicAuthentication("user", "password"))
                .build();
    }
    //@WithMockUser
    private final String tokenValue ="";
  //  @WithMockCustomUser(token = tokenValue, userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")
    @Test
    public void deleteMyData() throws InterruptedException {
        String email = "deleteWithPasswordSecretExpiredAndAccountActiveTrue@sonam.co";
        String authId = "deleteWithPasswordSecretExpiredAndAccountActiveTrue";

        // Create an authentication object

        final String accessToken = "eyJraWQiOiI5NmM2ZmEwZS1kZWE3LTRmOGMtYTc0MS1jMDA5ZjAzOWFkMjYiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImF1ZCI6ImI0ZGZlM2ZiLTE2OTItNDRiOC05MmFiLTM2NmNjYzg0YjUzOS1hdXRoem1hbmFnZXIiLCJuYmYiOjE3MjA3MzM1OTgsInNjb3BlIjpbIm9wZW5pZCIsInByb2ZpbGUiXSwiaXNzIjoiaHR0cDovL2FwaS1nYXRld2F5OjkwMDEvaXNzdWVyIiwiZXhwIjoxNzIwNzM0Nzk4LCJ1c2VyUm9sZSI6WyJVU0VSX1JPTEUiXSwiaWF0IjoxNzIwNzMzNTk4LCJ1c2VySWQiOiJkNmNlYTdhMS05NTcxLTRiNmItODc4Yy0wY2MxNjEzNDAwMmEiLCJqdGkiOiIwYTcxMjhiYi0xMDQ0LTQ0ZjktOTVhMC1iNzMzMGExMTQ3ZmEifQ.ITRIz6TgLIDRqAB7dFtp4qudx_m-6O7rUtCkDAp6Ebbl68G8qRwj8oHUjduW-AEkcOiYts7cYV_5bK2McjLcabaYYnfulAM6h8h98NiS8Uwm7KkqWGz7DkJRz_dp50yC0H4MMeKzlxjpPA0KPgBEPzotN7oYzqRmtHdsMkc1YLcOAHYwyZXTGJ8H05p4YHP7_7_wZbrkzvGiX26hgUza63Nydjs-wWRQhmvOn8SKJ4-5_YBYi-V_99fShYelqCiFYeLbxfrXrv3yZ-ebM180HzRQ-1U0VB7uoySPf8O_h36Hefd-h7ypqoQhMl4WSCld3MNxfZ1qUOZCo6PNynnUYQ";


        Jwt jwt = jwt(authId, UUID.fromString("5d8de63a-0b45-4c33-b9eb-d7fb8d662107"));
        //  when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, true, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));


        EntityExchangeResult<Map<String, String>> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/accounts/delete")
                .headers(httpHeaders -> httpHeaders.setBearerAuth(accessToken))
                /*.headers(addJwt(jwt))*/.exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference
                        <Map<String, String>>(){}).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("account deleted with userid");

        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
    }


      /*  Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user", "password",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
*/
    // Set the authentication in the context
    // SecurityContextHolder.getContext().setAuthentication(authentication);


    private Jwt jwt(String subjectName, UUID userId) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName, "userId", userId.toString()));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }

}
