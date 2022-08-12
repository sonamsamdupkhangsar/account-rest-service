package me.sonam.account.handler;

import me.sonam.account.handler.email.Email;
import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.PasswordSecretRepository;
import me.sonam.account.repo.entity.Account;
import me.sonam.account.repo.entity.PasswordSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class UserAccountService implements UserAccount {
    private static final Logger LOG = LoggerFactory.getLogger(UserAccountService.class);

    private WebClient webClient;

    @Value("${activate-authentication-rest-service}")
    private String activateAuthentication;

    @Value("${email-rest-service}")
    private String emailEp;

    @Value("${emailFrom}")
    private String emailFrom;

    @Value("${emailBody}")
    private String emailBody;

    @Value("${account-activate-link}")
    private String accountActivateLink;

    @Value("${secretExpire}")
    private int secretExpiresInHour;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordSecretRepository passwordSecretRepository;

    @PostConstruct
    public void setWebClient() {
        webClient = WebClient.builder().build();
    }

    @Override
    public Mono<String> isAccountActive(ServerRequest serverRequest) {
        LOG.info("checking account active status for userId");

        return accountRepository.existsByAuthenticationIdAndActiveTrue(
                serverRequest.pathVariable("authenticationId"))
                .flatMap(aBoolean -> Mono.just(aBoolean.toString()));
    }

    @Override
    public Mono<String> activateAccount(ServerRequest serverRequest) {
        String authenticationId = serverRequest.pathVariable("authenticationId");
        String secret = serverRequest.pathVariable("secret");
        LOG.info("activate account for authenticationId: {}", authenticationId);

        return passwordSecretRepository.findById(authenticationId).flatMap(passwordSecret -> {
                    if (!passwordSecret.getSecret().equals(secret)) {
                        LOG.error("secret does not match from database: {} vs passed: {}", passwordSecret.getSecret(), secret);
                        return Mono.error(new AccountException("secret does not match"));
                    }
                    else if (passwordSecret.getExpireDate().isBefore(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime())) {
                        LOG.error("secret has expired");
                        return Mono.error(new AccountException("secret has expired"));
                    }
                    else {
                        return  accountRepository.findByAuthenticationId(authenticationId);
                    }
                })
                .switchIfEmpty(Mono.error(new AccountException("No account with authenticationId")))
                .doOnNext(account -> {
                    if (!account.getActive()){
                        account.setActive(true);
                        account.setAccessDateTime(LocalDateTime.now());
                        account.setNewAccount(false);
                        LOG.info("set account to active if not");
                    }
                    else {
                        LOG.info("account was active from before");
                    }
                })
                .doOnNext(account -> {
                    LOG.info("saving account: {}", account.toString());
                    accountRepository.save(account);
                })
                .flatMap(account -> {
                    LOG.info("send activate webrequest to authentication-rest-service");
                    StringBuilder stringBuilder = new StringBuilder(activateAuthentication).append(authenticationId);
                    WebClient.ResponseSpec spec = webClient.put().uri(stringBuilder.toString()).retrieve();

                    return spec.bodyToMono(String.class).flatMap(myemail-> {
                        LOG.info("activation response from authentication-rest-service is: {}", myemail);
                        return Mono.just("activate response from authentication-rest-service endpoint is success");
                    }).onErrorResume(throwable -> Mono.error(new AccountException("Email activation failed: "+ throwable.getMessage())));
                })
                .thenReturn("account activated");
    }

    @Override
    public Mono<String> emailActivationLink(ServerRequest serverRequest) {
        LOG.info("email Activation link");

        String authId = serverRequest.pathVariable("authenticationId");

        return accountRepository.existsByAuthenticationIdAndActiveTrue(authId).
                filter(aBoolean -> {
                    LOG.info("aBoolean existsByUserIdAndActiveTrue: {}", aBoolean);
                    if (aBoolean == true) {
                        return false;
                    }
                    return true;
                } )
                .switchIfEmpty(Mono.error(new AccountException("Account already exists and is active")))
                .doOnNext(aBoolean -> {
                    LOG.info("delete from passwordSecret repo if there is any: {}", authId);
                    passwordSecretRepository.deleteById(authId);
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
                .map(passwordSecret -> new StringBuilder(emailBody)
                        .append(accountActivateLink).append("/").append(authId)
                        .append("/").append(passwordSecret.getSecret())
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                .flatMap(stringBuilder -> accountRepository.findByAuthenticationId(authId)
                        .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                        .zipWith(Mono.just(stringBuilder)))
                .flatMap(objects -> email(objects.getT1().getEmail(), "Activation link", objects.getT2().toString()));
    }

    @Override
    public Mono<String> emailMySecret(ServerRequest serverRequest) {
        LOG.info("email my secret for password reset");
        String authenticationId = serverRequest.pathVariable("authenticationId");

        return accountRepository.existsByAuthenticationIdAndActiveTrue(authenticationId)
                .filter(aBoolean -> aBoolean)
                .switchIfEmpty(Mono.error(new AccountException("Account is not active or does not exist")))
                .doOnNext(aBoolean -> {
                    LOG.info("delete from passwordSecret repo if there is any: {}", authenticationId);
                    passwordSecretRepository.deleteById(authenticationId);
                })
                .flatMap(unused -> {
                    LOG.info("generate random text: {}", unused);
                    return generateRandomText(10);
                })
                .flatMap(randomText -> Mono.just(new PasswordSecret(authenticationId, randomText,
                        ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
                .flatMap(passwordSecret -> {
                    LOG.info("passwordSecret created");
                    return passwordSecretRepository.save(passwordSecret);
                })
                .map(passwordSecret -> new StringBuilder("You new secret is: " + passwordSecret.getSecret())
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                .flatMap(stringBuilder -> accountRepository.findByAuthenticationId(authenticationId)
                        .switchIfEmpty(Mono.error(new AccountException("no account with email")))
                        .zipWith(Mono.just(stringBuilder)))
                .flatMap(objects -> email(objects.getT1().getEmail(), "Your requested information", objects.getT2().toString()));
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

        return accountRepository.existsByAuthenticationIdOrEmail(authenticationId, email).filter(aBoolean -> !aBoolean)
                .switchIfEmpty(Mono.error(new AccountException("Account already exists with authenticationId or email")))
                .flatMap(aBoolean -> Mono.just(new Account(authenticationId, email, false, ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime())))
                .flatMap(account -> accountRepository.save(account))
                .flatMap(account -> Mono.just("saved account with In-Active state"))
                 .doOnNext(s -> {
                     LOG.info("delete from passwordSecret repo if there is any: {}", authenticationId);
                    passwordSecretRepository.deleteById(authenticationId);
                })
                .flatMap(unused -> {
                    LOG.info("generate random text: {}", unused);
                    return generateRandomText(10);
                })
                .flatMap(randomText -> Mono.just(new PasswordSecret(authenticationId, randomText,
                        ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().plusHours(secretExpiresInHour))))
                .flatMap(passwordSecret -> {
                    LOG.info("passwordSecret created");
                    return passwordSecretRepository.save(passwordSecret);
                })
                .map(passwordSecret -> new StringBuilder(emailBody).append(" ")
                        .append(accountActivateLink).append("/").append(authenticationId)
                        .append("/").append(passwordSecret.getSecret())
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                .flatMap(stringBuilder -> email(email, "Activation link", stringBuilder.toString()))
                .thenReturn("Account created");
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
                .flatMap(account -> Mono.just(new StringBuilder("Your reuqested login id "+ account.getAuthenticationId())
                        .append("\nMessage sent at UTC time: ").append(ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime()))
                        .zipWith(accountMono))
                .flatMap(objects -> email(objects.getT2().getEmail(), "Your requested information", objects.getT1().toString()));
    }

    //authId/passwordsecret
    @Override
    public Mono<String> validateEmailLoginSecret(ServerRequest serverRequest) {
        LOG.info("validate email login secret");

        String authenticationId = serverRequest.pathVariable("authenticationId");
        String secret = serverRequest.pathVariable("secret");

        return passwordSecretRepository.findById(authenticationId)
                .switchIfEmpty(Mono.error(new AccountException("no passwordsecret found with authenticationId")))
                .flatMap(passwordSecret -> {
                    if (passwordSecret.getSecret().equals(secret)) {
                        if (ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().isAfter(passwordSecret.getExpireDate())) {
                            return Mono.error(new AccountException("secret has expired"));
                        }
                        return Mono.just("passwordsecret matches");
                    }
                    else {
                        return Mono.error(new AccountException("secret does not match"));
                    }
                });
    }

    private Mono<String> email(String emailTo, String subject, String messageBody) {
        LOG.info("sending email to {}, subject: {}, body: {}", emailEp, subject, messageBody);

        Email email = new Email(emailFrom, emailTo, subject, messageBody);
        WebClient.ResponseSpec spec = webClient.post().uri(emailEp).bodyValue(email).retrieve();

        return spec.bodyToMono(String.class).flatMap(myemail-> {
            LOG.info("activation email response is: {}", myemail);
            return Mono.just("email sent");
        }).onErrorResume(throwable -> Mono.error(new AccountException("Email activation failed: "+ throwable.getMessage())));
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