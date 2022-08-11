package me.sonam.account.repo;


import me.sonam.account.repo.entity.Account;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AccountRepository extends ReactiveCrudRepository<Account, UUID> {
    Mono<Boolean> existsByAuthenticationIdAndActiveTrue(String var1);
    Mono<Boolean> existsByAuthenticationIdAndActiveFalse(String var1);
    Mono<Boolean> existsByEmailAndActiveTrue(String email);
    Mono<Account> findByAuthenticationId(String authenticationId);
    Mono<Integer> countByAuthenticationId(String authenticationId);
    Mono<Account> findByEmail(String email);
    Mono<Void> deleteByAuthenticationId(String authenticationId);
    Mono<Boolean> existsByAuthenticationId(String authenticationId);
    @Modifying
    @Query("Update Account a set a.active=true and a.access_date_time= :localDateTime where a.user_id= :userId")
    Mono<Integer> activeAccount(@Param("userId") UUID userId, @Param("localDateTime") LocalDateTime localDateTime);
}
