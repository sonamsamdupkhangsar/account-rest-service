package me.sonam.account.repo.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * represents a Account record in Account table.
 */
public class Account implements Persistable<UUID> {
    @Id
    private UUID id;
    private String authenticationId;    //this should be unique
    private String email;               // not unique, can be duplicate so accounts can reuse existing emails
    private Boolean active;
    private LocalDateTime accessDateTime;

    @Transient
    private boolean newAccount;

    public Account() {
    }

    public Account(String authenticationId, String email, Boolean active, LocalDateTime localDateTime) {
        this.newAccount = true;
        this.id = UUID.randomUUID();
        this.email = email;
        this.authenticationId = authenticationId;
        this.active = active;
        this.accessDateTime = localDateTime;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return this.newAccount;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return this.email;
    }

    public Boolean getActive() {
        return this.active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getAccessDateTime() {
        return this.accessDateTime;
    }

    public void setAccessDateTime(LocalDateTime accessDateTime) {
        this.accessDateTime = accessDateTime;
    }

    public void setNewAccount(boolean newAccount) {
        this.newAccount = newAccount;
    }

    public String getAuthenticationId() {
        return this.authenticationId;
    }
    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", email=" + email +
                ", authenticationId=" + authenticationId +
                ", active=" + active +
                ", accessDateTime=" + accessDateTime +
                ", newAccount=" + newAccount +
                '}';
    }
}