package me.sonam.account.handler;

import jakarta.annotation.PostConstruct;
import me.sonam.account.handler.email.Email;
import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.PasswordSecretRepository;
import me.sonam.account.repo.entity.Account;
import me.sonam.account.repo.entity.PasswordSecret;
import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;


public class UserAccountService implements UserAccount {
    private static final Logger LOG = LoggerFactory.getLogger(UserAccountService.class);

    @Value("${user-rest-service.root}${user-rest-service.activate}")
    private String activateUser;

    @Value("${user-rest-service.root}${user-rest-service.delete}")
    private String deleteUser;

    @Value("${authentication-rest-service.root}${authentication-rest-service.activate}")
    private String activateAuthenticationEndpoint;

    @Value("${authentication-rest-service.root}${authentication-rest-service.delete}")
    private String deleteAuthentication;

    @Value("${authentication-rest-service.root}${authentication-rest-service.update-no-auth-password}")
    private String updateAuthenticationNoAuthPassword;

    @Value("${email-rest-service.root}${email-rest-service.emails}")
    private String emailEp;

    @Value("${emailFrom}")
    private String emailFrom;

    @Value("${emailBody}")
    private String emailBody;

    @Value("${account-rest-service.root}${account-rest-service.activate}")
    private String accountActivateLink;

    @Value("${secretExpire}")
    private int secretExpiresInHour;

    @Value("${auth-manager-app}")
    private String authManagerAppUrl;

    @Value("${passwordResetPath}")
    private String passwordResetPath;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordSecretRepository passwordSecretRepository;

    private WebClient.Builder webClientBuilder;
    @Autowired
    private ReactiveRequestContextHolder reactiveRequestContextHolder;


    public UserAccountService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void setWebClient() {
        webClientBuilder.filter(reactiveRequestContextHolder.headerFilter());
    }

    @Override
    public Mono<String> isAccountActive(String authenticationId) {
        LOG.info("checking account active status for userId");

        return accountRepository.existsByAuthenticationIdAndActiveTrue(authenticationId)
                .flatMap(aBoolean -> Mono.just("Account active status is " + aBoolean.toString()));
    }

    @Override
    public Mono<String> activateAccount(ServerRequest serverRequest) {
        String authenticationId = serverRequest.pathVariable("authenticationId");
        String secret = serverRequest.pathVariable("secret");
        LOG.info("activate account for authenticationId: {}", authenticationId);

        return accountRepository.existsByAuthenticationId(authenticationId)
                .filter(aBoolean -> aBoolean)
                .switchIfEmpty(Mono.error(new AccountException("No account with authenticationId")))
                .flatMap(aBoolean -> passwordSecretRepository.findById(authenticationId))
                .switchIfEmpty(Mono.error(new AccountException("account has already been activated or try reactivation with email")))
                .flatMap(passwordSecret -> {
                    if (!passwordSecret.getSecret().equals(secret)) {
                        LOG.error("secret does not match from database: {} vs passed: {}", passwordSecret.getSecret(), secret);
                         return Mono.error(new AccountException("secret does not match"));
                    }
                    else if (passwordSecret.getExpireDate().isBefore(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime())) {
                        LOG.error("secret has expired");
                         return Mono.error(new AccountException("secret has expired"));
                    }
                    else {
                        LOG.info("delete passwordSecret after validation");
                        return passwordSecretRepository.deleteById(authenticationId)
                                .then(accountRepository.findByAuthenticationId(authenticationId));
                    }
                })
                .switchIfEmpty(Mono.error(new AccountException("No account with authenticationId")))
                .doOnNext(account -> {
                    if (!account.getActive()){
                        account.setActive(true);
                        account.setAccessDateTime(LocalDateTime.now());
                        account.setNewAccount(false);
                        LOG.info("set account to active if not");
                        LOG.info("set account to active if not");
                    }
                    else {
                        LOG.info("account was active from before");
                    }
                })
                .flatMap(account -> {
                    LOG.info("saving account: {}", account.toString());
                    return accountRepository.save(account);
                })
                .flatMap(account -> {
                    String endpoint = activateAuthenticationEndpoint.replace("{authenticationId}", authenticationId);
                    //StringBuilder stringBuilder = new StringBuilder(activateAuthenticationEndpoint).append(authenticationId);
                    LOG.info("send activate webrequest to authentication-rest-service: {}", endpoint);
                    WebClient.ResponseSpec spec = webClientBuilder.build().put().uri(endpoint).retrieve();

                    return spec.bodyToMono(String.class).flatMap(s -> {
                        LOG.info("activation response from authentication-rest-service is: {}", s);
                        return Mono.just(s);
                    }).onErrorResume(throwable -> {
                        StringBuilder errorMessage = new StringBuilder("error on authentication rest service call");

                        if (throwable instanceof WebClientResponseException) {
                            WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                            LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                            errorMessage = errorMessage.append(", error: ").append(webClientResponseException.getResponseBodyAsString());
                        }
                        else {
                            errorMessage.append("error: ").append(throwable.getMessage());
                        }
                        return Mono.error(new AccountException(errorMessage.toString()));
                    });
                })
                .flatMap(account -> {
                    String endpoint = activateUser.replace("{authenticationId}", authenticationId);
                    LOG.info("send activate webrequest to user-rest-service: {}", endpoint);
                    WebClient.ResponseSpec spec = webClientBuilder.build().put().uri(endpoint).retrieve();

                    return spec.bodyToMono(String.class).flatMap(s -> {
                        LOG.info("activation response from user-rest-service is: {}", s);
                        return Mono.just(s);
                    }).onErrorResume(throwable -> {
                        StringBuilder errorMessage = new StringBuilder("error on activate user rest service call");

                        if (throwable instanceof WebClientResponseException) {
                            WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                            LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());
                            errorMessage.append(", error: ").append(webClientResponseException.getResponseBodyAsString());
                        }
                        else {
                            errorMessage.append("error: ").append(throwable.getMessage());
                        }
                        LOG.error("error on user rest service call {}", errorMessage, throwable);
                        return Mono.error(new AccountException(errorMessage.toString()));

                    });
                })
                .thenReturn("<html><head><title>account activated</title></head><body><p>Your account is activated." +
                        "  You can go to your app or go to <a href=\""+authManagerAppUrl+"\">Auth manager</a></p></body></html>");
    }

    @Override
    public Mono<String> emailActivationLinkUsingEmail(ServerRequest serverRequest) {
        LOG.info("email Activation link");

        String email = serverRequest.pathVariable("email");

        return accountRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                .flatMap(account -> {
            LOG.info("delete from passwordSecret repo if there is any by authenticationId: {}", account.getAuthenticationId());
            return emailByAuth(account);
        });
    }

    private Mono<String> emailByAuth(Account account) {
        LOG.info("use account mono to send email");

            return passwordSecretRepository.deleteById(account.getAuthenticationId())
                    .thenReturn("deleted")
                    .flatMap(unused -> generateRandomText(10))
                    .flatMap(randomText -> Mono.just(new PasswordSecret(account.getAuthenticationId(), randomText,
                            ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
                    .flatMap(passwordSecret -> {
                        LOG.info("passwordSecret created");
                        return passwordSecretRepository.save(passwordSecret);
                    })
                    .map(passwordSecret -> {
                        LOG.info("before replace: {}", accountActivateLink);
                        String endpoint = accountActivateLink.replace("{authenticationId}", account.getAuthenticationId())
                                .replace("{secret}", passwordSecret.getSecret());
                        LOG.info("accountActiveLink: {}", endpoint);
                        return new StringBuilder(emailBody).append(" ")
                                .append(endpoint)
                                .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime());

                    })
                    .flatMap(stringBuilder -> accountRepository.findByAuthenticationId(account.getAuthenticationId())
                            .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                            .zipWith(Mono.just(stringBuilder)))
                    .flatMap(objects -> email(objects.getT1().getEmail(), "Activation link", objects.getT2().toString()))
                    .thenReturn("Email activation link has been sent");
    }


    @Override
    public Mono<String> emailActivationLink(ServerRequest serverRequest) {
        LOG.info("email Activation link");

        String authId = serverRequest.pathVariable("authenticationId");

        return accountRepository.existsByAuthenticationIdAndActiveTrue(authId)
              /*  .filter(aBoolean -> {
                    LOG.info("aBoolean existsByUserIdAndActiveTrue: {}", aBoolean);
                    if (aBoolean == true) {
                        return false;
                    }
                    return true;
                } ) // check if a link was already sent, if it was then return indicating a link has already been sent
                .switchIfEmpty(Mono.error(new AccountException("Account already exists and is active")))*/
                .doOnNext(aBoolean -> {
                    LOG.info("delete from passwordSecret repo if there is any: {}", authId);
                    passwordSecretRepository.deleteById(authId).subscribe(unused -> LOG.info("delete existing password secret"));
                })
                .flatMap(unused -> {
                    LOG.info("generate random text: {}", unused);
                    return generateRandomText(10);
                })
                .flatMap(randomText -> Mono.just(new PasswordSecret(authId, randomText,
                        ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
                .flatMap(passwordSecret -> {
                    LOG.info("passwordSecret created");
                    return passwordSecretRepository.save(passwordSecret);
                })
                .map(passwordSecret -> {
                    LOG.info("before replace: {}", accountActivateLink);
                    String endpoint = accountActivateLink.replace("{authenticationId}", authId)
                            .replace("{secret}", passwordSecret.getSecret());
                    LOG.info("accountActiveLink: {}", accountActivateLink);
                    return new StringBuilder(emailBody).append(" ").append(endpoint)
                            .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime());
                })
                .flatMap(stringBuilder -> accountRepository.findByAuthenticationId(authId)
                        .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                        .zipWith(Mono.just(stringBuilder)))
                .flatMap(objects -> email(objects.getT1().getEmail(), "Activation link", objects.getT2().toString()))
                .thenReturn("Email activation link has been sent");
    }

    @Override
    public Mono<String> emailMySecretUsingEmail(ServerRequest serverRequest) {
        LOG.info("email my secret for password reset");
        String email = serverRequest.pathVariable("email");

        return accountRepository.findByEmailAndActiveTrue(email)
                .switchIfEmpty(Mono.error(new AccountException("Account is not active or does not exist")))
                .flatMap(this::emailSecret);
    }

    private Mono<String> emailSecret(Account account) {
        LOG.info("email with account object for emailing secret");
        return generateRandomText(10)
            .flatMap(string -> Mono.just(new PasswordSecret(account.getAuthenticationId(), string,
                    ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
            .flatMap(passwordSecret -> {
                LOG.info("delete existing if any passwordSecret by authId and save new passwordSecret");
                return passwordSecretRepository.deleteById(passwordSecret.getAuthenticationId())
                    .then(passwordSecretRepository.save(passwordSecret));
            })
            .map(passwordSecret -> {
                String endpoint = passwordResetPath.replace("{email}", account.getEmail()).replace("{secret}", passwordSecret.getSecret());

                return new StringBuilder("Please click on this link to initiate password change: " + endpoint)
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime());
            })
            .flatMap(stringBuilder -> accountRepository.findByAuthenticationId(account.getAuthenticationId())
            .switchIfEmpty(Mono.error(new AccountException("no account with email")))
            .zipWith(Mono.just(stringBuilder)))
            .flatMap(objects -> email(objects.getT1().getEmail(),"Your requested information", objects.getT2().toString()));
}


    @Override
    public Mono<String> emailMySecret(ServerRequest serverRequest) {
        LOG.info("email my secret for password reset");
        String authenticationId = serverRequest.pathVariable("authenticationId");

        return accountRepository.existsByAuthenticationIdAndActiveTrue(authenticationId)
                .filter(aBoolean -> aBoolean) //filter the boolean value to make it empty if false on switchIfEmtpy
                .switchIfEmpty(Mono.error(new AccountException("Account is not active or does not exist")))
                .flatMap(booleanValue -> {
                    LOG.info("generate random text");
                    return generateRandomText(10);
                })
                .flatMap(randomText -> Mono.just(new PasswordSecret(authenticationId, randomText,
                        ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
                .flatMap(passwordSecret -> {
                    LOG.info("delete existing if any passwordSecret by authId and save new passwordSecret");
                    return passwordSecretRepository.deleteById(passwordSecret.getAuthenticationId())
                            .then(passwordSecretRepository.save(passwordSecret));
                })
                .map(passwordSecret -> new StringBuilder("You new secret is: " + passwordSecret.getSecret())
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                .flatMap(stringBuilder -> accountRepository.findByAuthenticationId(authenticationId)
                        .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                        .zipWith(Mono.just(stringBuilder)))
                .flatMap(objects -> email(objects.getT1().getEmail(),"Your requested information", objects.getT2().toString()));
    }

    /**
     * Check account does not exist with authId or email already
     * Create account in false active state
     * @param serverRequest
     * @return
     */
    @Override
    public Mono<String> createAccount(ServerRequest serverRequest) {
        String authenticationId = serverRequest.pathVariable("authenticationId");
        String email = serverRequest.pathVariable("email");

        LOG.info("create account with authenticationId: {} and email: {}", authenticationId, email);

        return accountRepository.existsByAuthenticationIdAndActiveTrue(authenticationId).filter(aBoolean -> !aBoolean)
                .switchIfEmpty(Mono.error(new AccountException("Account is already active with authenticationId")))
                //delete any previous attempts that is not activated
                .flatMap(aBoolean -> accountRepository.deleteByAuthenticationIdAndActiveFalse(authenticationId))
                .flatMap(integer -> accountRepository.existsByEmail(email))
                .filter(aBoolean -> !aBoolean)
                .switchIfEmpty(Mono.error(new AccountException("a user with this email already exists")))
                .flatMap(integer -> Mono.just(new Account(authenticationId, email, false, ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime())))
                .flatMap(account -> accountRepository.save(account))
                .flatMap(account -> {
                    LOG.info("delete from passwordSecret repo if there is any: {}", authenticationId);
                    return passwordSecretRepository.deleteById(authenticationId).thenReturn("deleted");
                })
                .flatMap(s -> {
                    LOG.info("generate random text: {}", s);
                    return generateRandomText(10);
                })
                .flatMap(randomText -> Mono.just(new PasswordSecret(authenticationId, randomText,
                        ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
                .flatMap(passwordSecret -> {
                    LOG.info("passwordSecret created");
                    return passwordSecretRepository.save(passwordSecret);
                })
                .map(passwordSecret -> new StringBuilder(emailBody).append(" ")
                        .append(accountActivateLink.replace("{authenticationId}", authenticationId)
                                .replace("{secret}", passwordSecret.getSecret()))
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                .flatMap(stringBuilder -> email(email,"Activation link", stringBuilder.toString()))
                .then(Mono.just("Account created successfully.  Check email for activating account"));
    }

    @Override
    public Mono<String> sendAuthenticationId(ServerRequest serverRequest) {
        LOG.info("send login id or authenticationId");
        String email = serverRequest.pathVariable("email");

        Mono<Account> accountMono = accountRepository.findByEmail(email);

        return accountMono
                .switchIfEmpty(Mono.error(new AccountException("Account does not exist with this authenticationId")))
                .filter(account -> account.getActive())
                .switchIfEmpty(Mono.error(new AccountException("Account is not active")))
                .flatMap(account -> Mono.just(new StringBuilder("Your requested login id "+ account.getAuthenticationId())
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                        .zipWith(accountMono))
                .flatMap(objects -> email(objects.getT2().getEmail(),"Your requested information", objects.getT1().toString()));
    }

    //authId/passwordsecret
    @Override
    public Mono<String> validateEmailLoginSecret(String email, String secret) {
        LOG.info("validate email login secret");

        return accountRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new AccountException("no account found with email")))
                .flatMap(account -> validateEmailLoginSecret(account, secret));
    }

    private Mono<String> validateEmailLoginSecret(Account account, String secret) {
        LOG.info("use account object to validate password secret");

        return passwordSecretRepository.findById(account.getAuthenticationId())
                .switchIfEmpty(Mono.error(new AccountException("no passwordsecret found with authenticationId")))
                .flatMap(passwordSecret -> {
                    if (passwordSecret.getSecret().equals(secret)) {
                        if (ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().isAfter(passwordSecret.getExpireDate())) {
                            return Mono.error(new AccountException("secret has expired"));
                        }
                        return Mono.just("passwordsecret matches");
                    } else {
                        return Mono.error(new AccountException("secret does not match"));
                    }
                });
    }

    @Override
    public Mono<String> delete(ServerRequest serverRequest) {
        LOG.info("delete accounts where the passwordsecret have expired more than 1 day and that are not active");
        String email = serverRequest.pathVariable("email");

        return accountRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                .map(account -> account.getAuthenticationId())
                .flatMap(authenticationId ->
                        passwordSecretRepository.findById(authenticationId)
                        .switchIfEmpty(Mono.error(new AccountException("no passwordSecret with authenticationId")))
                        .filter(passwordSecret ->
                                ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().isAfter(passwordSecret.getExpireDate())
                        ).switchIfEmpty(Mono.error(new AccountException("password has not expired, can't delete")))
                        .flatMap(passwordSecret -> accountRepository.existsByAuthenticationIdAndActiveTrue(authenticationId))
                        .filter(aBoolean -> !aBoolean)
                        .switchIfEmpty(Mono.error(new AccountException("account is active, can't delete")))
                        .flatMap(aBoolean -> {
                                    LOG.info("send request to delete User record with authenticationId: {}", authenticationId);
                                    StringBuilder stringBuilder = new StringBuilder(deleteUser).append(authenticationId);
                                    LOG.info("delete user with endpoint: {}", stringBuilder.toString());

                                    return webClientBuilder.build().delete().uri(stringBuilder.toString()).retrieve().bodyToMono(String.class)
                                            .doOnNext(s -> {
                                                LOG.info("deleted user with authenticationId: {}, rest response is {}", authenticationId, s);
                                            }).onErrorResume(throwable -> {
                                                LOG.error("error occured deleting user record with authenticationId", throwable);
                                                return Mono.error(new AccountException("failed to delete user"));
                                            });
                                }
                        ).flatMap(s -> {
                    LOG.info("delete authentication object");
                    StringBuilder stringBuilder = new StringBuilder(deleteAuthentication).append(authenticationId);
                    LOG.info("delete authentication with endpoint: {}", stringBuilder.toString());

                    return webClientBuilder.build().delete().uri(stringBuilder.toString()).retrieve().bodyToMono(String.class)
                            .doOnNext(s2 -> {
                                LOG.info("deleted authentication with authenticationId: {}, rest response is {}", authenticationId, s2);
                            }).onErrorResume(throwable -> {
                                LOG.error("error occured on deletion request", throwable);
                                return Mono.error(new AccountException("failed to delete authentication"));
                            });
                }).flatMap(s -> {
                    LOG.info("delete passwordSecret if exists for authenticationId: {}", authenticationId);
                    return accountRepository.deleteByAuthenticationIdAndActiveFalse(authenticationId)
                            .then(passwordSecretRepository.deleteById(authenticationId))
                            .thenReturn("deleted authenticationId that is active false");
                }));
    }
    private Mono<String> email(String emailTo, String subject, String messageBody) {
        LOG.info("sending email to {}, subject: {}, body: {}", emailEp, subject, messageBody);

        return webClientBuilder.build().post().uri(emailEp)
                .bodyValue(new Email(emailFrom, emailTo, subject, messageBody))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(map-> {
                    LOG.info("email response is: {}", map);
                    return Mono.just(map.get("message").toString());
                }).onErrorResume(throwable -> {
                    LOG.error("email failed: {}", throwable.getMessage());
                    return  Mono.error(new AccountException("Email failed: "+ throwable.getMessage()));
                });
    }

    @Override
    public Mono<String> updateAuthenticationPassword(String email, String secret, final String password) {
        LOG.info("update authentication password: {}", email);

        return accountRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new AccountException("there is no account with the given email")))
                .flatMap(account -> updateAuthenticationPassword(account, secret, password));
    }

    private Mono<String> updateAuthenticationPassword(Account account, String secret, final String password) {
        return accountRepository.existsByAuthenticationIdAndActiveTrue(account.getAuthenticationId())
                .filter(aBoolean -> {
            LOG.info("exists by authId and active? {}", aBoolean);
            return  aBoolean;
        })
                .switchIfEmpty(Mono.error(new AccountException("account is not active or does not exist")))
                .flatMap(aBoolean -> {
                    LOG.info("active?: {}", aBoolean);
                    //return validateEmailLoginSecret(account.getEmail(), secret);
                    return validateEmailLoginSecret(account, secret);
                })
                .map(map -> {
                    LOG.info("delete passwordSecret by authenticationId after success validation of secret");
                    return passwordSecretRepository.deleteById(account.getAuthenticationId());
                }).flatMap(voidMono -> {
                            WebClient.ResponseSpec responseSpec = webClientBuilder.build().put().uri(updateAuthenticationNoAuthPassword)
                                    .bodyValue(Map.of("authenticationId", account.getAuthenticationId(),
                                            "password", password))
                                    .retrieve();

                            return  responseSpec.bodyToMono(Map.class).flatMap(map -> {
                                LOG.info("response from authentication-rest-service is {}", map.get("message"));
                                return Mono.just(map.get("message").toString());
                            }).onErrorResume(throwable -> {
                                LOG.error("password updated failed when calling authentication-rest-service {}", throwable.getMessage());
                                return Mono.error(new AccountException("Password update failed: " + throwable.getMessage()));
                            });
                        }
                );
    }

    /**
     * method to generate random length of text
     * @param n
     * @return
     */
    public Mono<String> generateRandomText(int n)
    {
        LOG.info("generate random text");

        // chose a Character random from this String
        String alphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index
                    = (int)(alphaNumericString.length()
                    * Math.random());

            // add Character one by one in end of sb
            sb.append(alphaNumericString
                    .charAt(index));
        }

        //return sb.toString();
        return Mono.just(sb.toString());
    }
}