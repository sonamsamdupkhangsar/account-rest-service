package me.sonam.account;

import io.r2dbc.spi.ConnectionFactory;
import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.entity.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.connectionfactory.init.ConnectionFactoryInitializer;
import org.springframework.data.r2dbc.connectionfactory.init.ResourceDatabasePopulator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@SpringBootApplication
public class Application {
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /*@Bean
    ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
        System.out.println("create tables");
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ByteArrayResource(("CREATE SEQUENCE primary_key;"
                + "DROP TABLE IF EXISTS Account;"
                + "CREATE TABLE Account (id UUID PRIMARY KEY, active boolean, lastAccessed datetime);"
                + "DROP TABLE IF EXISTS video;"
                + "CREATE TABLE video ( id SERIAL PRIMARY KEY, name varchar(255),  thumb varchar(255), path VARCHAR(255) NOT NULL, stored datetime NOT NULL);"
        )
                .getBytes())));


        return initializer;
    }*/


    @Bean
    org.springframework.data.r2dbc.connectionfactory.init.ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {

        org.springframework.data.r2dbc.connectionfactory.init.ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));

        return initializer;
    }

    @Bean
    public CommandLineRunner accountsSave(AccountRepository repository) {

        return (args) -> {

            // save a few customers
            repository.saveAll(Arrays.asList(new Account(UUID.randomUUID(), true, LocalDateTime.now()),
                    new Account(UUID.randomUUID(), true, LocalDateTime.now()),
                    new Account(UUID.randomUUID(), false, LocalDateTime.now())))
                    .blockLast(Duration.ofSeconds(10));

            // fetch all customers
            LOG.info("Accounts found with findAll():");
            LOG.info("-------------------------------");
          /*  repository.findAll().doOnNext(account -> {
                LOG.info(account.toString());
            }).blockLast(Duration.ofSeconds(10));*/

        };
    }
      /*      @Bean
    public CommandLineRunner demo(CustomerRepository repository) {

        return (args) -> {

            // save a few customers
            repository.saveAll(Arrays.asList(new Customer("Jack", "Bauer"),
                    new Customer("Chloe", "O'Brian"),
                    new Customer("Kim", "Bauer"),
                    new Customer("David", "Palmer"),
                    new Customer("Michelle", "Dessler")))
                    .blockLast(Duration.ofSeconds(10));

            // fetch all customers
            LOG.info("Customers found with findAll():");
            LOG.info("-------------------------------");
            repository.findAll().doOnNext(customer -> {
                LOG.info(customer.toString());
            }).blockLast(Duration.ofSeconds(10));

            LOG.info("");

            // fetch an individual customer by ID
            repository.findById(1L).doOnNext(customer -> {
                LOG.info("Customer found with findById(1L):");
                LOG.info("--------------------------------");
                LOG.info(customer.toString());
                LOG.info("");
            }).block(Duration.ofSeconds(10));


            // fetch customers by last name
            LOG.info("Customer found with findByLastName('Bauer'):");
            LOG.info("--------------------------------------------");
            repository.findByLastName("Bauer").doOnNext(bauer -> {
                LOG.info(bauer.toString());
            }).blockLast(Duration.ofSeconds(10));;
            LOG.info("");
        };
    }*/
}
