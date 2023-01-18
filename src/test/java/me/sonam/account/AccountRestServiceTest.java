package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.PasswordSecretRepository;
import me.sonam.account.repo.entity.Account;
import me.sonam.account.repo.entity.PasswordSecret;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


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

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;
    private static MockWebServer mockWebServer;

    private static String emailEndpoint = "http://localhost:{port}/email";
    private static String activateAuthenticationEndpoint = "http://localhost:{port}";///authentications/activate/";
    private static String activateUserEndpoint = "http://localhost:{port}";///user/activate/";

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
    }

    @AfterAll
    public static void shutdownMockWebServer() throws IOException {
        LOG.info("shutdown and close mockWebServer");
        mockWebServer.shutdown();
        mockWebServer.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws IOException {
        r.add("email-rest-service", () -> emailEndpoint.replace("{port}", mockWebServer.getPort() + ""));
        //r.add("activate-authentication-rest-service", () -> activateAuthenticationEndpoint.replace("{port}",  mockWebServer.getPort()+""));
        r.add("authentication-rest-service.root", () -> activateAuthenticationEndpoint.replace("{port}",  mockWebServer.getPort()+""));
        //r.add("activate-user-rest-service", () -> activateUserEndpoint.replace("{port}",  mockWebServer.getPort()+""));
        r.add("user-rest-service.root", () -> activateUserEndpoint.replace("{port}",  mockWebServer.getPort()+""));
        LOG.info("updated email-rest-service properties: {}" );
        LOG.info("mockWebServer.port: {}", mockWebServer.getPort());
    }

    @AfterEach
    public void deleteAccountRepo() {
        accountRepository.deleteAll().subscribe();
    }

    @Test
    public void isAccountActive() {
        final String uuid = UUID.randomUUID().toString();
        LOG.info("check for uuid: {}", uuid);
        client.get().uri("/accounts/active/authenticationId/"+uuid)
                .exchange().expectStatus().isOk();

    }

    @Test
    public void activateAccount() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";

        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now());
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in false active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("activate response from authentication-rest-service endpoint is success"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("activate response from user-rest-service endpoint is success"));

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result = client.get().uri("/accounts/activate/" + authenticationId+"/mysecret")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getResponseBody().get("message")).isEqualTo("account activated");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");
        mockWebServer.takeRequest();

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/authentications/activate/");


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

        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now());
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret",
                ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result = client.get().uri("/accounts/activate/" + authenticationId+"/mysecret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret has expired");
    }

    @Test
    public void activateAccountBadSecret() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";

        Account account = new Account(authenticationId, "activateAccount.test@sonam.email", false, LocalDateTime.now());
        accountRepository.save(account)
                .subscribe(account1 -> LOG.info("Saved account in active state"));

        PasswordSecret passwordSecret = new PasswordSecret(authenticationId, "mysecret",
                ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));
        passwordSecretRepository.save(passwordSecret).subscribe(passwordSecret1 -> LOG.info("save password secret"));

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result = client.get().uri("/accounts/activate/" + authenticationId+"/myecret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret does not match");
    }

    @Test
    public void activateAccountWhenNoAccountExists() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<Map> result  = client.get().uri("/accounts/activate/" + authenticationId+"/secret")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("No account with authenticationId");
    }


    @Test
    public void testAccountDuplicatesWhenNewFlagTrue() {
        LOG.info("testing count of unique rows when saving account multiple times");
        String email = "sonam@sonam.me";

        Account account = new Account(email, email, true, LocalDateTime.now());
        Mono<Account> accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag once with email: {}", email);
        accountMono.subscribe(account1 -> LOG.info("account: {}", account1));

        accountRepository.countByAuthenticationId(email).as(StepVerifier::create)
                .assertNext(count ->  {LOG.info("count now is: {}", count); assertThat(count).isEqualTo(1);})
                .verifyComplete();

        account = new Account(email, email, true, LocalDateTime.now());
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
        Account account = new Account(email, email, true, LocalDateTime.now());
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

        Account account = new Account(emailTo, emailTo, false, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

       // webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(30)).build();

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/emailactivationlink/"+emailTo)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message")) ;
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        //the body is empty for some reason.
        String body = new String(request.getBody().getBuffer().readByteArray());
        LOG.info("path: {}", request.getPath());
        LOG.info("request: {}", body);

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    /**
     * Test for sending email activation link when there is No Account in Account repository
     */
    @Test
    public void emailActivationLinkNoAcount() {
        String emailTo = "emailActivationLinkWithNoAccount@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/emailactivationlink/"+emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("no account with email");
        LOG.info("response: {}", result.getResponseBody().get("error")) ;
    }

    @Test
    public void emailMySecretForPasswordReset() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";

        Account account = new Account(emailTo, emailTo, true, LocalDateTime.now());
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/emailmysecret/"+emailTo)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getResponseBody().get("message")).isEqualTo("email sent");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    @Test
    public void emailMySecretForPasswordResetNoAccount() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/emailmysecret/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is not active or does not exist");
    }

    @Test
    public void emailMySecretForPasswordResetAccountNotActive() {
        String emailTo = "emailMySecretForPasswordResetAccountNotActive@sonam.co";

        Account account = new Account(emailTo, emailTo, false, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/emailmysecret/"+emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is not active or does not exist");
    }

    @Test
    public void createAccount() throws InterruptedException {
        String emailTo = "createAccount@sonam.co";
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("email sent"));
        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+emailTo+"/"+emailTo)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("email sent");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    @Test
    public void createAccountWithExistingAuthIdAndEmail() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";

        Account account = new Account(authId, emailTo, false, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" + authId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();


        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("a user with this email already exists");
    }

    @Test
    public void createAccountWithExistingAuthIdActiveFalse() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        Account account = new Account(authId, "createAccountWithExistingAuthId@sonam.co", false, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" + authId + "/" + emailTo)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("message")).isEqualTo("email sent");
    }


    @Test
    public void createAccountWithExistingAuthIdActiveTrue() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        Account account = new Account(authId, "createAccountWithExistingAuthId@sonam.co", true, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" + authId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is already active with authenticationId");
    }

    @Test
    public void createAccountWithNewAuthIdWithExistingEmail() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        Account account = new Account(authId, emailTo, true, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");
        final String newAuthId = "createAccountWithNewAuthIdWithExistingEmail";


        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" + newAuthId + "/" + emailTo)
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

        Account account = new Account(authId, email, false, LocalDateTime.now());
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        final String newEmail = "createAccountWithExistingAuthIdButNewEmailAndActiveFalse@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+authId+"/"+newEmail)
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody().get("message")).isEqualTo("email sent");

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

        Account account = new Account(authId, email, true, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        final String newEmail = "createAccountWithExistingAuthIdButNewEmailAndActiveFalse@sonam.co";

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/"+authId+"/"+newEmail)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        assertThat(result.getResponseBody().get("error")).isEqualTo("Account is already active with authenticationId");
    }


    @Test
    public void createAccountWithExistingEmailAndActiveTrue() throws InterruptedException {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccountWithExistingEmailAndActive@sonam.email";
        Account account = new Account(authId, emailTo, true, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");
        String anotherId = UUID.randomUUID().toString();

        EntityExchangeResult<Map> result = webTestClient.post().uri("/accounts/" + anotherId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();


        LOG.info("response: {}", result.getResponseBody().get("error"));
        //assertThat(result.getResponseBody().get("message")).isEqualTo("Account already exists with authenticationId or email");
        assertThat(result.getResponseBody().get("error")).isEqualTo("a user with this email already exists");
    }

    @Test
    public void sendAuthenticationId() throws InterruptedException {
        String emailTo = "sendAuthenticationId@sonam.co";
        String authId = "sendAuthenticationId";

        Account account = new Account(authId, emailTo, true, LocalDateTime.now());
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/email/authenticationId/"+emailTo)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getResponseBody().get("message")).isEqualTo("email sent");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    @Test
    public void sendAuthenticationIdNoEmail() {
        String emailTo = "sendAuthenticationId@sonam.co";
        String authId = "sendAuthenticationId";

        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/email/authenticationId/"+emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("Account does not exist with this authenticationId");
    }

    @Test
    public void validateSecret() {
        final String authId = "createAccountWithExistingEmail";
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/validate/secret/" + authId + "/" + "123hello")
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));
        assertThat(result.getResponseBody().get("message")).isEqualTo("passwordsecret matches");
    }

    @Test
    public void validateSecretNotMatch() {
        final String authId = "createAccountWithExistingEmail";
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/validate/secret/" + authId + "/" + "123hell")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret does not match");
    }

    @Test
    public void validateSecretExpired() {
        final String authId = "createAccountWithExistingEmail";
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<Map> result = webTestClient.put().uri("/accounts/validate/secret/" + authId + "/" + "123hello")
                .exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));
        assertThat(result.getResponseBody().get("error")).isEqualTo("secret has expired");
    }

    @Test
    public void deleteWithNonExistingEmail() throws InterruptedException {
        String email = "deleteWithPasswordSecretAndAccountFalse@sonam.co";
        String authId = "deleteWithPasswordSecretAndAccountFalse";

        Jwt jwt = jwt(authId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Account account = new Account(authId, email, false, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        EntityExchangeResult<Map> result = webTestClient.delete().uri("/accounts/email/"+"bogusemail@email.com")
                .headers(addJwt(jwt))
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
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Account account = new Account(authId, email, false, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret("no-corresponding-id", "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        EntityExchangeResult<Map> result = webTestClient.delete().uri("/accounts/email/"+email)
                .headers(addJwt(jwt))
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
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Account account = new Account(authId, email, false, LocalDateTime.now());
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("user deleted"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("authentication deleted"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));


        EntityExchangeResult<Map> result = webTestClient.delete().uri("/accounts/email/"+email)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("message"));

        assertThat(result.getResponseBody().get("message")).isEqualTo("deleted authenticationId that is active false");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/user/deleteWithPasswordSecretAndAccountFalse");

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
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Account account = new Account(authId, email, false, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        EntityExchangeResult<Map> result = webTestClient.delete().uri("/accounts/email/"+email)
                .headers(addJwt(jwt))
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
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Account account = new Account(authId, email, true, LocalDateTime.now());
        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));


        EntityExchangeResult<Map> result = webTestClient.delete().uri("/accounts/email/"+email)
                .headers(addJwt(jwt)).exchange().expectStatus().isBadRequest().expectBody(Map.class).returnResult();

        LOG.info("response: {}", result.getResponseBody().get("error"));

        assertThat(result.getResponseBody().get("error")).isEqualTo("account is active, can't delete");
        passwordSecretRepository.existsById(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
        accountRepository.existsByAuthenticationId(authId).subscribe(aBoolean -> LOG.info("is true?: {}", aBoolean));
    }


    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }


}

