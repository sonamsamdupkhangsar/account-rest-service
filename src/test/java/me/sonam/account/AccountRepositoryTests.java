package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.entity.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.H2Dialect;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataR2dbcTest
public class AccountRepositoryTests {
    private static final Logger LOG = LoggerFactory.getLogger(AccountRestServiceTest.class);

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private AccountRepository accounts;

    @AfterEach
    public void deleteALl() {
        accounts.deleteAll().subscribe(unused -> LOG.info("deleted all accounts"));
    }

    @Test
    public void getAllAccounts() {
        UUID userId = UUID.randomUUID();
        String email = "getAllAccounts@sonam.me";
        Account account = new Account(email, email, false, LocalDateTime.now(), userId);

        R2dbcEntityTemplate template = new R2dbcEntityTemplate(databaseClient, H2Dialect.INSTANCE);
        template.insert(Account.class).using(account).then().as(StepVerifier::create).verifyComplete();
        Mono<Account> findByLastName = accounts.findByAuthenticationId(email);
        accounts.countByAuthenticationId(email).subscribe(integer -> LOG.info("found {} accounts with email: {}", integer, email));

        findByLastName.as(StepVerifier::create)
            .assertNext(actual -> {
                assertThat(actual.getAuthenticationId()).isEqualTo(email);
            })
            .verifyComplete();
    }

    @Test
    public void updateAccount() {
        String email = "updateAccount@sonam.me";
        UUID userId = UUID.randomUUID();
        Account account = new Account(email, email, false, LocalDateTime.now(), userId);

        R2dbcEntityTemplate template = new R2dbcEntityTemplate(databaseClient, H2Dialect.INSTANCE);
        template.insert(Account.class).using(account).then().as(StepVerifier::create).verifyComplete();
        Mono<Account> findByLastName = accounts.findByAuthenticationId(email);


        findByLastName.as(StepVerifier::create)
                .assertNext(actual -> {
                    assertThat(actual.getAuthenticationId()).isEqualTo(email);
                })
                .verifyComplete();

        findByLastName.subscribe(account1 -> {
            UUID id = account1.getId();
            account1.setAccessDateTime(LocalDateTime.now());
            Mono<Account> savedAccount = accounts.save(account1);
            savedAccount.subscribe(testSavedAccount ->{
               assertThat(testSavedAccount.getId()).isSameAs(id);
               LOG.info("did a save now");
            });
        });
    }
}
