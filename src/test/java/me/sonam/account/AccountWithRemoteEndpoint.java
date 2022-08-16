package me.sonam.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class AccountWithRemoteEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(AccountWithRemoteEndpoint.class);

    @Autowired
    private WebTestClient webTestClient;


    // port-forward account-rest-service to port 8081
    @Test
    public void createAccount() throws InterruptedException {
        String emailTo = "createAccount2@sonam.email";

        LOG.info("create account");

        webTestClient.post().uri("http://localhost:8081/accounts/"
                +emailTo+"/"+emailTo)
                .exchange().expectStatus().isCreated().expectBody(String.class).consumeWith(stringEntityExchangeResult ->
                LOG.info("response: {}", stringEntityExchangeResult.getResponseBody()));

    }
}
