package me.sonam.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;


@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountRestServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(AccountRestServiceTest.class);

    @Autowired
    private WebTestClient client;

    @Test
    public void isAccountActive() {
        client.get().uri("/active/"+UUID.randomUUID().toString())
                .exchange().expectStatus().isOk();

    }


    @Test
    public void activateAccount() {
        client.post().uri("/activate/"+UUID.randomUUID().toString())
                .exchange().expectStatus().isOk();

    }
}
