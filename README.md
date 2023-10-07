# account-rest-service

This is a Account Rest Service api built using Spring WebFlux. 
This is a reactive Java webservice api.


## Run locally using profile
Use the following to run local profile which will pick up properties defined in the `application-local.yml` :


```
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Or you can do something like following too:

```
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8086 --jwt.issuer=sonam.us \
    --POSTGRES_USERNAME=test \
    --POSTGRES_PASSWORD=test \
    --POSTGRES_DBNAME=account2 \
    --POSTGRES_SERVICE=localhost:5432
    --DB_SSLMODE=disable
    --eureka.client.enabled=false"                      
```



## Build Docker image

Build docker image using included Dockerfile.


`docker build -t imageregistry/account-rest-service:1.0 .` 

## Push Docker image to repository

`docker push imageregistry/account-rest-service:1.0`

## Deploy Docker image locally

`docker run -e POSTGRES_USERNAME=dummy \
 -e POSTGRES_PASSWORD=dummy -e POSTGRES_DBNAME=account \
  -e POSTGRES_SERVICE=localhost:5432 \
 --publish 8080:8080 imageregistry/account-rest-service:1.0`


## Installation on Kubernetes
Use a Helm chart such as my one here @ [sonam-helm-chart](https://github.com/sonamsamdupkhangsar/sonam-helm-chart):

```
helm install project-api sonam/mychart -f values.yaml --version 0.1.12 --namespace=yournamespace
```

## Instruction for port-forwarding database pod
```
export PGMASTER=$(kubectl get pods -o jsonpath={.items..metadata.name} -l application=spilo,cluster-name=project-minimal-cluster,spilo-role=master -n yournamespace); 
echo $PGMASTER;
kubectl port-forward $PGMASTER 6432:5432 -n yournamespace;
```

### Login to database instruction
```
export PGPASSWORD=$(kubectl get secret <SECRET_NAME> -o 'jsonpath={.data.password}' -n yournamesapce | base64 -d);
echo $PGPASSWORD;
export PGSSLMODE=require;
psql -U <USER> -d projectdb -h localhost -p 6432
```

## User Account Workflow
endpoints: 
1. Create account
2. Activate account
3. Email activation link
4. Password reset 
5. Account active check
6. Get authenticationId
7. Validate Emailed Login Secret
8. Delete account associated with email


## Account active check workflow
```mermaid
flowchart TD
  User[user request] --> account-rest-service["account-rest-service"]
  account-rest-service --"account active?" --> accountRepository[(account db)]
```

## ActivateAccount
```mermaid
flowchart TD
  User[user request] -->Activate[/Activate Account/]--> account-rest-service
   
  subgraph account-rest-service    
  activateAccount --> authenticationIdExists{authenticationId exist?}  
  authenticationIdExists --"read from"--> accountDb[(account postgresdb)]  
  authenticationIdExists --> |Yes| passwordSecretCheck[Check PasswordSecret]
  authenticationIdExists --> |No| ReturnError[Return 400 error to request]  
  passwordSecretCheck --> passwordSecretValid{PasswordSecretExists and Valid?}  
  passwordSecretValid --> accountDb
  passwordSecretValid -->|Yes| setAccountActive   
  setAccountActive --> accountDb
  setAccountActive --"activate authentication"--> activateAuthentication[<a href='https://github.com/sonamsamdupkhangsar/authentication-rest-service'>authentication-rest-service</a>] 
  passwordSecretValid -->|No| ReturnError
  activateAuthentication --"activate user"--> activateUser[<a href='https://github.com/sonamsamdupkhangsar/user-rest-service'>user-rest-service</a>]
  end 
```  
  
## Email activation link
```mermaid
flowchart TD
  User[user-request] -->UserEmailActivationLink[/Email account activation link/]--> account-rest-service
  
  subgraph account-rest-service[emailActivationLink]
  validateAuthenticationIdExists["AuthenticationId exists?"]
  validateAuthenticationIdExists --"read from"--> accountDb[(account postgresdb)]
  validateAuthenticationIdExists -->|Yes| deleteAnySecretPassword["delete existing secretPassword"]
  deleteAnySecretPassword --"write to"--> accountDb
  validateAuthenticationIdExists -->|No| ReturnError[Return 400 error to request]
  deleteAnySecretPassword --> createNewSecretPassword["create new secretPassword"]
  createNewSecretPassword --"write to"--> accountDb
  createNewSecretPassword --> emailActivationLink[/email activation link/]
  emailActivationLink --> email-rest-service[<a href='https://github.com/sonamsamdupkhangsar/email-rest-service'>email-rest-service</>]    
  end              
```

## Email User secret
```mermaid
flowchart TD
  User[user-request] -->EmailSecret[/Email Secret for Password reset/]--> account-rest-service
  
  subgraph account-rest-service[emailMySecret] 
  validateAuthenticationIdExistsAndTrue["AuthenticationIdExistsAndIsActive?"]
  validateAuthenticationIdExistsAndTrue --> accountDb[(account postgresdb)]
  validateAuthenticationIdExistsAndTrue -->|Yes| deleteAnySecretPassword["delete existing secretPassword"]
  deleteAnySecretPassword --> accountDb
  validateAuthenticationIdExistsAndTrue -->|No| ReturnError[Return 400 error to request]
  deleteAnySecretPassword --> createNewSecretPassword["create new secretPassword"]
  createNewSecretPassword --> accountDb
  createNewSecretPassword --> emailSecret["email secret"]
  emailSecret --> email-rest-service   
  end               
```


## Create account
```mermaid
flowchart TD
  User --"Create account"--> account-rest-service
  account-rest-service --> validateAuthenticationIdExistsAndTrue["AuthenticationIdExistsAndIsActive?"]
  validateAuthenticationIdExistsAndTrue --> accountDb[(account postgresdb)]
  validateAuthenticationIdExistsAndTrue -->|Yes| returnError[Return 400 error to request]  
  validateAuthenticationIdExistsAndTrue -->|No| existsByEmail{email already used?}
  existsByEmail -->|Yes| returnError
  existsByEmail -->|No| deleteAuthenticationIdActiveFalse["delete previous Authentication"]
  deleteAuthenticationIdActiveFalse --> save["create Authentication"]
  save --> accountDb
  save --> createPasswordSecret
  createPasswordSecret --> accountDb
  createPasswordSecret --> emailActivationLink["email activation link"]
  emailActivationLink --> email-rest-service
```


## Send authenticationId by email
```mermaid
flowchart TD
  User --"user requests to get authenticationId, maybe they forgot?"--> account-rest-service
  account-rest-service --> findByEmail{Account exists by email?}
  findByEmail --> accountDb[(account postgresdb)]
  findByEmail -->|Yes| accountIsActive{is account active?}  
  findByEmail -->|No| returnError[Return 400 error to request]
  accountIsActive -->|Yes| emailAuthenticationId[email authenticationid]
  emailAuthenticationId --> email-rest-service
  accountIsActive -->|No| returnError                  
```

## Validate email login secret
```mermaid
flowchart TD
  User --"validate email login secret"--> account-rest-service
  account-rest-service --> findByAuthenticationId["find by authenticationId"]
  findByAuthenticationId --> validatePasswordSecretMatches{DoespasswordSecret match?}
  validatePasswordSecretMatches --> accountDb[(accountDb postgresql)]
  validatePasswordSecretMatches -->|Yes, passwordSecret exists and matches| returnHttp200["return passwordsecret matches"]
  validatePasswordSecretMatches -->|No| returnError["Secret has expired or does not match"]           
```

## Delete account by email
```mermaid
flowchart TD
  User --"delete account by email"--> account-rest-service
  account-rest-service --> accountWithEmailExists{account with email exists?}
  accountWithEmailExists -->|No| returnError[Return 400 error to request]
  accountWithEmailExists -->|Yes| passwordSecretExists{passwordSecret Exists?}
  passwordSecretExists -->|Yes| passwordSecretExpired{passwordSecret has expired?}
  passwordSecretExists -->|No| returnError
  passwordSecretExpired -->|No| returnError
  passwordSecretExpired -->|Yes| accountActiveWithAuthenticationIdAndActive{is account active?}
  accountActiveWithAuthenticationIdAndActive -->|Yes| returnError
  accountActiveWithAuthenticationIdAndActive -->|No| deleteUser[request to delete user]
  deleteUser --> user-rest-service
  user-rest-service --> deleteAuthentication[delete authentication]
  deleteAuthentication --> authentication-rest-service
  authentication-rest-service --> deleteAccount[delete account]
  deleteAccount --> accountDb[(accountDb postgresql)]           
```

If Account is not getting activated, try resend email activation link
