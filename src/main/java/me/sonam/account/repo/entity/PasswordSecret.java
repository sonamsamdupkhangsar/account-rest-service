package me.sonam.account.repo.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

public class PasswordSecret implements Persistable<String> {
    @Id
    private String authenticationId;
    private String secret;
    private LocalDateTime expireDate;

    @Transient
    private boolean newAccount;

    public PasswordSecret() {
    }

    public PasswordSecret(String authenticationId, String secret, LocalDateTime expireDate) {
        this.authenticationId = authenticationId;
        this.secret = secret;
        this.expireDate = expireDate;
        newAccount = true;
    }

    public String getAuthenticationId() {
        return this.authenticationId;
    }
    public String getSecret() {
        return this.secret;
    }
    public LocalDateTime getExpireDate() {
        return this.expireDate;
    }

    public String getId() {
        return authenticationId;
    }

    @Override
    public boolean isNew() {
        return this.newAccount;
    }
}