package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.PasswordSecretRepository;
import me.sonam.account.repo.entity.Account;
import me.sonam.account.repo.entity.PasswordSecret;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Before;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;
import static org.springframework.web.reactive.function.client.ExchangeFilterFunctions.basicAuthentication;


@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Application.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class AccountRestServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(AccountRestServiceTest.class);

    @Autowired
    private WebTestClient client;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordSecretRepository passwordSecretRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    ApplicationContext context;

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
    private static MockWebServer mockWebServer;

    private static String emailEndpoint = "http://localhost:{port}";
    private static String activateAuthenticationEndpoint = "http://localhost:{port}";///authentications/activate/";
    private static String activateUserEndpoint = "http://localhost:{port}";///user/activate/";
    private static String jwtRestServiceAccesstoken = "http://localhost:{port}";///jwts/accesstoken";
    @Before
    public void setUp() {
        LOG.info("setup mock");
        MockitoAnnotations.openMocks(this);
    }

    @BeforeAll
    static void setupMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
    }

    @AfterEach
    public void deleteALl() {
        accountRepository.deleteAll().subscribe(unused -> LOG.info("deleted all accounts"));
        passwordSecretRepository.deleteAll().subscribe(unused -> LOG.info("deleted all password secrets"));

        LOG.info("request coountmockWebServer.getRequestCount(): {}",mockWebServer.getRequestCount());

    }

    @AfterAll
    public static void shutdownMockWebServer() throws IOException {
        LOG.info("shutdown and close mockWebServer");
        mockWebServer.shutdown();
        mockWebServer.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws IOException {
        r.add("email-rest-service.root", () -> "http://localhost:"+ mockWebServer.getPort());
        r.add("authentication-rest-service.root", () -> "http://localhost:"+ mockWebServer.getPort());
        r.add("user-rest-service.root", () -> "http://localhost:"+ mockWebServer.getPort());
        r.add("auth-server.root", () -> "http://localhost:"+ mockWebServer.getPort());
    }

    @AfterEach
    public void deleteAccountRepo() {
        accountRepository.deleteAll().subscribe();
    }

    @Test
    public void isAccountActive() {
        final String uuid = UUID.randomUUID().toString();
        LOG.info("check for uuid: {}", uuid);
        client.get().uri("/accounts/"+uuid+"/active")
                .exchange().expectStatus().isOk();

    }

    /**
     * this will test the account authentication password for an un-logged in user
     */
    @Test
    public void accountAuthenticationPasswordUpdate() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";
        UUID userId = UUID.randomUUID();

        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", true, LocalDateTime.now(), userId);
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in faltruese active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"password updated\"}"));

        Map<String, String> map = Map.of("email", account.getEmail(),
                "secret", "mysecret", "password", "newPassword");

        LOG.info("update account authentication password");
        EntityExchangeResult<Map> result = client.put().uri("/accounts/password-secret")
                .bodyValue(map)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response from accounts/authentications/password update: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("message")).isEqualTo("password updated");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/authentications/noauth/password");

        StepVerifier.create(passwordSecretRepository.existsById(passwordSecret.getAuthenticationId())).assertNext(aBoolean -> {
            LOG.info("assert that passwordSecret with authId does not exists: {}", aBoolean);
            assertThat(aBoolean).isFalse();
        }).verifyComplete();
    }

    /**
     * this will test when user is not active but attempting to update authentication user password
     * @throws InterruptedException
     */
    @Test
    public void accountAuthenticationPasswordUpdateAccountNotActive() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now(), userId);
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in false active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        Map<String, String> map = Map.of("email", account.getEmail(),
                "secret", "mysecret", "password", "newPassword");

        LOG.info("update account authentication password");
        EntityExchangeResult<Map> result = client.put().uri("/accounts/password-secret")
                .bodyValue(map)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response from accounts/authentications/password update: {}", result.getResponseBody());
        assertThat(result.getResponseBody().get("error")).isEqualTo("account is not active or does not exist");
    }
    @Test
    public void activateAccount() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now(), userId);
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in false active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("activate response from authentication-rest-service endpoint is success"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("activate response from user-rest-service endpoint is success"));

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<String> result = client.get().uri("/accounts/" + authenticationId+"/active/mysecret")
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).contains("account activated");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/authentications/"+authenticationId+"/active");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/users/"+authenticationId+"/active");

        accountRepository.findByAuthenticationId(authenticationId).as(StepVerifier::create).
                assertNext(account1 -> {
                    LOG.info("assert active is now true");
                    assertThat(account1.getActive()).isTrue();
                })
                .verifyComplete();

    }

    @Test
    public void activateAccountExpiredPassword() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now(), userId);
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret",
                ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result = client.get().uri("/accounts/" + authenticationId+"/active/mysecret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret has expired");
    }

    @Test
    public void activateAccountBadSecret() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now(), userId);
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret",
                ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result = client.get().uri("/accounts/" + authenticationId+"/active/myecret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret does not match");
    }

    @Test
    public void activateAccountWhenNoAccountExists() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result  = client.get().uri("/accounts/" + authenticationId+"/active/secret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("No account with authenticationId");
    }


    @Test
    public void testAccountDuplicatesWhenNewFlagTrue() {
        LOG.info("testing count of unique rows when saving account multiple times");
        String email = "sonam@sonam.me";
        UUID userId = UUID.randomUUID();
        Account account = new Account(email, email, true, LocalDateTime.now(), userId);
        Mono<Account> accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag once with email: {}", email);
        accountMono.subscribe(account1 -> LOG.info("account: {}", account1));

        accountRepository.countByAuthenticationId(email).as(StepVerifier::create)
                .assertNext(count ->  {LOG.info("count now is: {}", count); assertThat(count).isEqualTo(1);})
                .verifyComplete();


        account = new Account(email, email, true, LocalDateTime.now(), userId);
        accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag twice with userId: {}", email);
        accountMono.subscribe(account1 -> LOG.info("account: {}", account1));

        accountRepository.countByAuthenticationId(email).as(StepVerifier::create)
                .assertNext(count -> { LOG.info("count now is: {}", count); assertThat(count).isEqualTo(2);})
            .verifyComplete();
    }

    @Test
    public void testAccountNewFlagTrueForUpdate() {

        LOG.info("testing count of unique rows when saving account multiple times");
        String email = "sonam@sonam.me";
        UUID userId = UUID.randomUUID();
        Account account = new Account(email, email, true, LocalDateTime.now(), userId);
        Mono<Account> accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag once with email: {}", email);
        accountMono.subscribe(account1 -> LOG.info("account1: {}", account1));

       accountRepository.countByAuthenticationId(email).as(StepVerifier::create)
                .assertNext(count ->  {LOG.info("count now is: {}", count); assertThat(count).isEqualTo(1);})
                .verifyComplete();

        account.setNewAccount(false);
        accountMono = accountRepository.save(account);
        accountMono.subscribe(account1 -> LOG.info("saved same account: {}", account1));

        accountRepository.countByAuthenticationId(account.getAuthenticationId()).as(StepVerifier::create)
                .assertNext(count -> {
                    LOG.info("newAccount=false, should only be 1 row for userId: {}, count: {}", account.getAuthenticationId(), count);
                    assertThat(count).isEqualTo(1);
                }).verifyComplete();
    }


    @Test
    public void emailActivationLink() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(emailTo, emailTo, false, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));
        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));


        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/active/email/"+emailTo+"/password-secret")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message")) ;
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");


        request = mockWebServer.takeRequest();
        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/emails");

        //the body is empty for some reason.
        String body = new String(request.getBody().getBuffer().readByteArray());
        LOG.info("path: {}", request.getPath());
        LOG.info("request: {}", body);
    }
    @Test
    public void emailActivationLinkNoAcount() {
        String emailTo = "emailActivationLinkWithNoAccount@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/active/email/"+emailTo+"/password-secret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("no account with email");
        LOG.info("response: {}", result.getResponseBody().get("error")) ;
    }
    @Test
    public void emailActivationLinkUsingEmail() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(emailTo, emailTo, false, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));
        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));


        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/active/email/"+emailTo+"/password-secret")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message")) ;
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");


        request = mockWebServer.takeRequest();
        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/emails");

        //the body is empty for some reason.
        String body = new String(request.getBody().getBuffer().readByteArray());
        LOG.info("path: {}", request.getPath());
        LOG.info("request: {}", body);
    }

    /**
     * Test for sending email activation link when there is No Account in Account repository
     */
    @Test
    public void emailActivationLinkNoAcountUsingEmail() {
        String emailTo = "emailActivationLinkWithNoAccount@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/active/email/"+emailTo+"/password-secret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("no account with email");
        LOG.info("response: {}", result.getResponseBody().get("error")) ;
    }

    @Test
    public void emailMySecretForPasswordReset() throws Exception {
        String emailTo = "emailActivationLink@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(emailTo, emailTo, true, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("request email secret");
        emailSecret(emailTo);
        LOG.info("request email secret again");
        emailSecret(emailTo);
        LOG.info("request email secret 3rd time");
        emailSecret(emailTo);
    }

    // this is used to call multiple times if needed
    private void  emailSecret(String emailTo) throws Exception {
        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        Flux<Map> mapFlux = webTestClient.put().uri("/accounts/email/"+emailTo+"/password-secret")
                .exchange().expectStatus().isOk().returnResult(Map.class).getResponseBody();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");


        request = mockWebServer.takeRequest();
        LOG.info("assert the path for authenticate was created using path '/create'");

        assertThat(request.getPath()).startsWith("/emails");

        StepVerifier.create(mapFlux).expectSubscription().assertNext( map -> {
            assertThat(map.get("message").toString()).isEqualTo("email successfully sent");
            LOG.info("assert message contains email successfully sent");

        }).verifyComplete();

        StepVerifier.create(passwordSecretRepository.existsById(emailTo)).assertNext(aBoolean -> {
                assertThat(aBoolean).isTrue();
                LOG.info("assert passwordSecret exists by authId: {}", emailTo);
        }).verifyComplete();
    }

    @Test
    public void emailMySecretForPasswordResetNoAccount() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/email/" + emailTo+"/password-secret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is not active or does not exist");
    }

    @Test
    public void emailMySecretForPasswordResetAccountNotActive() {
        String emailTo = "emailMySecretForPasswordResetAccountNotActive@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(emailTo, emailTo, false, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/email/"+emailTo+"/password-secret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is not active or does not exist");
    }

    @Test
    public void createAccount() throws InterruptedException {
        String emailTo = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        EntityExchangeResult<Map> result = webTestClient.post()
                .uri("/accounts/"+userId+"/"+emailTo+"/"+emailTo)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");


        request = mockWebServer.takeRequest();
        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/emails");

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("Account created successfully.  Check email for activating account");


        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("Account created successfully.  Check email for activating account");

        //RecordedRequest request = mockWebServer.takeRequest();
       // assertThat(request.getMethod()).isEqualTo("POST");

        //LOG.info("assert the path for authenticate was created using path '/create'");
        //assertThat(request.getPath()).startsWith("/email");
    }

    /**
     * this will create a account that is not active (false) and try to overwrite it if it's false.
     * which is allowable if the account creation didn't go thru successfully the first time.
     * @throws InterruptedException
     */
    @Test
    public void createAccountWithExistingAuthIdAndEmail() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, emailTo, false, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");
        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        EntityExchangeResult<Map> result = webTestClient
                .post().uri("/accounts/"+userId+"/" + authId + "/" + emailTo)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/oauth2/token");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("Account created successfully.  Check email for activating account");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/emails");

    }

    @Test
    public void createAccountWithExistingAuthIdActiveFalse() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, "createAccountWithExistingAuthId@sonam.co", false, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" +userId+"/"+ authId + "/" + emailTo)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("Account created successfully.  Check email for activating account");

        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/emails");

        assertThat(result.getResponseBody().get("message")).isEqualTo("Account created successfully.  Check email for activating account");
    }


    @Test
    public void createAccountWithExistingAuthIdActiveTrue() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, "createAccountWithExistingAuthId@sonam.co", true, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+userId+"/" + authId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is already active with authenticationId");
    }

    @Test
    public void createAccountWithNewAuthIdWithExistingEmail() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, emailTo, true, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");
        final String newAuthId = "createAccountWithNewAuthIdWithExistingEmail";


        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+userId+"/" + newAuthId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("a user with this email already exists");
    }

    /**
     * this test will verify that if an account exists with authId and active is false and another user
     * signed up with same authenticationId but new email then the previous authenticationId record
     * will be deleted and insert a new row.
     * @throws InterruptedException
     */
    @Test
    public void createAccountWithExistingAuthIdButNewEmailAndActiveFalse() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String email = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, false, LocalDateTime.now(), userId);
        LOG.info("try to POST with the same email/authId");

        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        final String newEmail = "createAccountWithExistingAuthIdButNewEmailAndActiveFalse@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+userId+"/"+authId+"/"+newEmail)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");


        request = mockWebServer.takeRequest();
        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/emails");

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("Account created successfully.  Check email for activating account");

        StepVerifier.create(accountRepository.findByAuthenticationId(authId)).assertNext(account1 ->
        {
            LOG.info("assert account1");
           assertThat(account1.getActive()).isFalse();
           assertThat(account1.getEmail()).isEqualTo(newEmail);
        });
    }

    @Test
    public void createAccountWithExistingAuthIdButNewEmailAndActiveTrue() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String email = "createAccount@sonam.co";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, true, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        final String newEmail = "createAccountWithExistingAuthIdButNewEmailAndActiveFalse@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+userId+"/"+authId+"/"+newEmail)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is already active with authenticationId");
    }


    @Test
    public void createAccountWithExistingEmailAndActiveTrue() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccountWithExistingEmailAndActive@sonam.email";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, emailTo, true, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");
        String anotherId = UUID.randomUUID().toString();

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" +userId+"/"+ anotherId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();


        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("a user with this email already exists");
    }

    @Test
    public void sendAuthenticationId() throws InterruptedException {
        String emailTo = "sendAuthenticationId@sonam.co";
        String authId = "sendAuthenticationId";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, emailTo, true, LocalDateTime.now(), userId);

        final String clientCredentialResponse = "{" +
                "    \"access_token\": \"eyJraWQiOiJhNzZhN2I0My00YTAzLTQ2MzAtYjVlMi0wMTUzMGRlYzk0MGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJwcml2YXRlLWNsaWVudCIsImF1ZCI6InByaXZhdGUtY2xpZW50IiwibmJmIjoxNjg3MTA0NjY1LCJzY29wZSI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMSIsImV4cCI6MTY4NzEwNDk2NSwiaWF0IjoxNjg3MTA0NjY1LCJhdXRob3JpdGllcyI6WyJtZXNzYWdlLnJlYWQiLCJtZXNzYWdlLndyaXRlIl19.Wx03Q96TR17gL-BCsG6jPxpdt3P-UkcFAuE6pYmZLl5o9v1ag9XR7MX71pfJcIhjmoog8DUTJXrq-ZB-IxIbMhIGmIHIw57FfnbBzbA8mjyBYQOLFOh9imLygtO4r9uip3UR0Ut_YfKMMi-vPfeKzVDgvaj6N08YNp3HNoAnRYrEJLZLPp1CUQSqIHEsGXn2Sny6fYOmR3aX-LcSz9MQuyDDr5AQcC0fbcpJva6aSPvlvliYABxfldDfpnC-i90F6azoxJn7pu3wTC7sjtvS0mt0fQ2NTDYXFTtHm4Bsn5MjZbOruih39XNsLUnp4EHpAh6Bb9OKk3LSBE6ZLXaaqQ\"," +
                "    \"scope\": \"message.read message.write\"," +
                "    \"token_type\": \"Bearer\"," +
                "    \"expires_in\": 299" +
                "}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(200).setBody(clientCredentialResponse));

        final String emailMsg = " {\"message\":\"email successfully sent\"}";
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(201).setBody(emailMsg));//"Account created successfully.  Check email for activating account"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        String urlEncodedEmail = URLEncoder.encode(emailTo, Charset.defaultCharset());

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/email/"+urlEncodedEmail+"/authentication-id")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getResponseBody().get("message")).isEqualTo("email successfully sent");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).startsWith("/oauth2/token");


        request = mockWebServer.takeRequest();
        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/emails");
    }

    @Test
    public void sendAuthenticationIdNoEmail() {
        String emailTo = "sendAuthenticationId@sonam.co";
        String authId = "sendAuthenticationId";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/email/"+emailTo+"/authentication-id")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("Account does not exist with this authenticationId");
    }

    @Test
    public void validateSecret() {
        final String authId = "createAccountWithExistingEmail";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, "sonam@sonam.cloud", true, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));


        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<Map> result = webTestClient.get().uri("/accounts/" + account.getEmail() + "/password-secret/" + "123hello")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getResponseBody().get("message")).isEqualTo("passwordsecret matches");
    }

    @Test
    public void validateSecretNotMatch() {
        final String authId = "createAccountWithExistingEmail";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, "sonam@sonam.cloud", true, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<Map> result = webTestClient.get().uri("/accounts/" + account.getEmail() + "/password-secret/" + "123hell")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret does not match");
    }

    @Test
    public void validateSecretExpired() {
        final String authId = "createAccountWithExistingEmail";
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, "sonam@sonam.cloud", true, LocalDateTime.now(), userId);

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<Map> result = webTestClient.get().uri("/accounts/" + account.getEmail() + "/password-secret/" + "123hello")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret has expired");
    }

    @Test
    public void deleteWithNonExistingEmail() throws InterruptedException {
        String email = "deleteWithPasswordSecretAndAccountFalse@sonam.co";
        String authId = "deleteWithPasswordSecretAndAccountFalse";

        Jwt jwt = jwt(authId);
//        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, false, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        EntityExchangeResult<Map> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/accounts/email/"+"bogusemail@email.com")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("no account with email");

        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is false?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is false?: {}", aBoolean));
    }

    @Test
    public void deleteWithNoAccount() throws InterruptedException {
        String email = "deleteWithPasswordSecretAndAccountFalse@sonam.co";
        String authId = "deleteWithPasswordSecretAndAccountFalse";

        Jwt jwt = jwt(authId);
//        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, false, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret("no-corresponding-id", "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        EntityExchangeResult<Map> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/accounts/email/"+email)

                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("no passwordSecret with authenticationId");

        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is false?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is false?: {}", aBoolean));
    }

    @Test
    public void deleteWithPasswordSecretExpiredAndAccountFalse() throws InterruptedException {
        String email = "deleteWithPasswordSecretAndAccountFalse@sonam.co";
        String authId = "deleteWithPasswordSecretAndAccountFalse";

        Jwt jwt = jwt(authId);
//        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, false, LocalDateTime.now(), userId);
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("user deleted"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("authentication deleted"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));


        EntityExchangeResult<Map> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/accounts/email/"+email)

                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("message")).isEqualTo("deleted authenticationId that is active false");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/users/deleteWithPasswordSecretAndAccountFalse");

        LOG.info("1. path: {}", request.getPath());
        request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/authentications/deleteWithPasswordSecretAndAccountFalse");

        LOG.info("2. path: {}", request.getPath());

        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is false?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is false?: {}", aBoolean));
    }

    @Test
    public void deleteWithPasswordSecretNotExpiredAndAccountFalse() throws InterruptedException {
        String email = "deleteWithPasswordSecretNotExpiredAndAccountFalse@sonam.co";
        String authId = "deleteWithPasswordSecretNotExpiredAndAccountFalse";

        Jwt jwt = jwt(authId);
//        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, false, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        EntityExchangeResult<Map> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/accounts/email/"+email)

                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("password has not expired, can't delete");
        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
    }

    @Test
    public void deleteWithPasswordSecretExpiredAndAccountActiveTrue() throws InterruptedException {
        String email = "deleteWithPasswordSecretExpiredAndAccountActiveTrue@sonam.co";
        String authId = "deleteWithPasswordSecretExpiredAndAccountActiveTrue";

        Jwt jwt = jwt(authId);

        UUID userId = UUID.randomUUID();
        Account account = new Account(authId, email, true, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));


        EntityExchangeResult<Map> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).
                delete().uri("/accounts/email/"+email)
               .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("account is active, can't delete");
        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
    }
  //  @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    private final String tokenValue ="";
   // @WithMockCustomUser(token = tokenValue, userId = "5d8de63a-0b45-4c33-b9eb-d7fb8d662107", username = "user@sonam.cloud", password = "password", role = "ROLE_USER")

    @WithMockUser
    @Test
    public void deleteMyData() throws InterruptedException {
        String email = "deleteWithPasswordSecretExpiredAndAccountActiveTrue@sonam.co";
        String authId = "deleteWithPasswordSecretExpiredAndAccountActiveTrue";
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(authId, userId);
      //  when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Account account = new Account(authId, email, true, LocalDateTime.now(), userId);
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));


        EntityExchangeResult<Map<String, String>> result = webTestClient.mutateWith(mockJwt().jwt(jwt)).delete().uri("/accounts/delete")
                /*.headers(addJwt(jwt))*/.exchange().expectStatus().isOk().expectBody(new ParameterizedTypeReference
                        <Map<String, String>>(){}).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        AssertionsForClassTypes.assertThat(result.getResponseBody().get("message")).isEqualTo("account deleted with userid");


        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
    }

    private Jwt jwt(String subjectName, UUID userId) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName, "userId", userId.toString()));
    }

    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }


}

