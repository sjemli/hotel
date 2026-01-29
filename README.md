# Room Reservation Service


Production-ready Spring Boot microservice for hotel room reservations.


## Features
- REST endpoint to confirm reservation with payment modes
- Credit card payment check via REST with Resilience4j
- Kafka consumer for bank transfer updates (idempotent)
- Scheduled cancellation of overdue bank transfer reservations
- DLQ for invalid Kafka messages
- Full test pyramid (unit, integration, E2E)


## Run
mvn spring-boot:run


Swagger: http://localhost:8080/swagger-ui.html


## Improvements
- Add ShedLock for multi-instance scheduler safety
- Micrometer metrics for job/confirmation
