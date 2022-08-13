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
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    private static MockWebServer mockWebServer;

    private static String emailEndpoint = "http://localhost:{port}/email";
    private static String activateAuthenticationEndpoint = "http://localhost:{port}/authentications/activate/";

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
        r.add("activate-authentication-rest-service", () -> activateAuthenticationEndpoint.replace("{port}",  mockWebServer.getPort()+""));
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
        client.get().uri("/public/accounts/active/"+uuid)
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

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<String> result = client.get().uri("/public/accounts/activate/" + authenticationId+"/mysecret")
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("account activated");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PUT");

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
        EntityExchangeResult<String> result = client.get().uri("/public/accounts/activate/" + authenticationId+"/mysecret")
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("secret has expired");
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
        EntityExchangeResult<String> result = client.get().uri("/public/accounts/activate/" + authenticationId+"/myecret")
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("secret does not match");
    }

    @Test
    public void activateAccountWhenNoAccountExists() throws InterruptedException {
        UUID id = UUID.randomUUID();
        final String authenticationId = "activateAccounttest";

        LOG.info("activate account for userId: {}", id);
        EntityExchangeResult<String> result  = client.get().uri("/public/accounts/activate/" + authenticationId+"/secret")
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        assertThat(result.getResponseBody()).isEqualTo("No account with authenticationId");
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

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/emailactivationlink/"+emailTo)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody()) ;
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

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/emailactivationlink/"+emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        assertThat(result.getResponseBody()).isEqualTo("no account with email");
        LOG.info("response: {}", result.getResponseBody()) ;
    }

    @Test
    public void emailMySecretForPasswordReset() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";

        Account account = new Account(emailTo, emailTo, true, LocalDateTime.now());
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/emailmysecret/"+emailTo)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("email sent");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    @Test
    public void emailMySecretForPasswordResetNoAccount() throws InterruptedException {
        String emailTo = "emailActivationLink@sonam.co";

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/emailmysecret/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("Account is not active or does not exist");
    }

    @Test
    public void emailMySecretForPasswordResetAccountNotActive() {
        String emailTo = "emailMySecretForPasswordResetAccountNotActive@sonam.co";

        Account account = new Account(emailTo, emailTo, false, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/emailmysecret/"+emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("Account is not active or does not exist");
    }

    @Test
    public void createAccount() throws InterruptedException {
        String emailTo = "createAccount@sonam.co";
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("email sent"));
        EntityExchangeResult<String> result = webTestClient.post().uri("/accounts/"+emailTo+"/"+emailTo)
                .exchange().expectStatus().isCreated().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getResponseBody()).isEqualTo("email sent");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    @Test
    public void createAccountWithExistingAuthIdAndEmail() {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        Account account = new Account(authId, emailTo, false, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<String> result = webTestClient.post().uri("/accounts/" + authId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("Account already exists with authenticationId or email");
    }

    @Test
    public void createAccountWithExistingAuthId() {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccount@sonam.co";
        Account account = new Account(authId, "createAccountWithExistingAuthId@sonam.co", false, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");

        EntityExchangeResult<String> result = webTestClient.post().uri("/accounts/" + authId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("Account already exists with authenticationId or email");
    }

    @Test
    public void createAccountWithExistingEmail() {
        String authId = "createAccountWithExistingAuthId";
        String emailTo = "createAccountWithExistingEmailAndActive@sonam.email";
        Account account = new Account(authId, emailTo, true, LocalDateTime.now());

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        LOG.info("try to POST with the same email/authId");
        String anotherId = UUID.randomUUID().toString();

        EntityExchangeResult<String> result = webTestClient.post().uri("/accounts/" + anotherId + "/" + emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("Account already exists with authenticationId or email");
    }

    @Test
    public void sendAuthenticationId() throws InterruptedException {
        String emailTo = "sendAuthenticationId@sonam.co";
        String authId = "sendAuthenticationId";

        Account account = new Account(authId, emailTo, true, LocalDateTime.now());
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("email sent"));

        accountRepository.save(account).subscribe(account1 -> LOG.info("saved account with email"));

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/email/authenticationId/"+emailTo)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("email sent");
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");

        LOG.info("assert the path for authenticate was created using path '/create'");
        assertThat(request.getPath()).startsWith("/email");
    }

    @Test
    public void sendAuthenticationIdNoEmail() {
        String emailTo = "sendAuthenticationId@sonam.co";
        String authId = "sendAuthenticationId";

        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/email/authenticationId/"+emailTo)
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("Account does not exist with this authenticationId");
    }

    @Test
    public void validateSecret() {
        final String authId = "createAccountWithExistingEmail";
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/validate/secret/" + authId + "/" + "123hello")
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("passwordsecret matches");
    }

    @Test
    public void validateSecretNotMatch() {
        final String authId = "createAccountWithExistingEmail";
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/validate/secret/" + authId + "/" + "123hell")
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("secret does not match");
    }

    @Test
    public void validateSecretExpired() {
        final String authId = "createAccountWithExistingEmail";
        PasswordSecret passwordSecret = new PasswordSecret(authId, "123hello", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(-1));

        passwordSecretRepository.save(passwordSecret).subscribe(account1 -> LOG.info("saved passwordsecret"));

        LOG.info("put validate secret");


        EntityExchangeResult<String> result = webTestClient.put().uri("/public/accounts/validate/secret/" + authId + "/" + "123hello")
                .exchange().expectStatus().isBadRequest().expectBody(String.class).returnResult();

        LOG.info("response: {}", result.getResponseBody());
        assertThat(result.getResponseBody()).isEqualTo("secret has expired");
    }
}

