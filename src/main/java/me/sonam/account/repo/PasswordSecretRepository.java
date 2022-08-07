package me.sonam.account.repo;

import me.sonam.account.repo.entity.PasswordSecret;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface PasswordSecretRepository extends ReactiveCrudRepository<PasswordSecret, String> {
    Mono<Boolean> existsByAuthenticationIdAndSecretAndExpireDateAfter(String authenticationId, String secret, LocalDateTime localDateTime);
}
