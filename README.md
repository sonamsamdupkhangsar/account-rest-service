# email-rest-service

This is a Email Rest Service api built using Spring WebFlux. 
This is a reactive Java webservice api.


## Run locally

`mvn spring-boot:run -Dspring-boot.run.arguments="--EMAIL_HOST=<HOST> \
 --EMAIL_PORT=<PORT> \
 --EMAIL_USERNAME=<USERNAME> \
 --EMAIL_PASSWORD=<PASSWORD>"`
 
 
## Build Docker image

Build docker image using included Dockerfile.


`docker build -t imageregistry/email-rest-service:1.0 .` 

## Push Docker image to repository

`docker push imageregistry/email-rest-service:1.0`

## Deploy Docker image locally

`docker run -e EMAIL_HOST=<HOST> -e EMAIL_PORT=<PORT> \
 -e EMAIL_USERNAME=<EMAIL> -e EMAIL_PASSWORD=<PASSWORD> \
 --publish 8080:8080 imageregistry/email-rest-service:1.0`

Test email api using `curl`:

````
 curl -X POST http://localhost:8080/email -H 'Content-Type: application/json' \
 -d '{"from": "from@my.email", "to": "to@my.email", \
  "subject":"hello", "body": "welcome to planet Earth"}'
 ```` 

## Installation on Kubernetes
Use a Helm chart such as my one here @ [sonam-helm-chart](https://github.com/sonamsamdupkhangsar/sonam-helm-chart):

```helm install emailapi sonam/mychart -f values.yaml --version 0.1.11 --namespace=backend```

