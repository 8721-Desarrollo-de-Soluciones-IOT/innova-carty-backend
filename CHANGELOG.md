# Changelog

## [0.3.0] - Sprint 2

### Added
- Spring Boot API with Supabase, organized by bounded context (iam, catalog, monitoring, payment, shopping, devices).
- ESP32 gateway with `X-Device-Key`: cart state (RFID tags, weight, battery, zone), events and status polling (TS01).
- Shopping sessions as purchase orders (TS02), shopper budget (US04), live total (US05) and RFID matching with the catalog (US06).
- Simulated Yape / Plin / card payments (US08) and the payment webhook with `X-Webhook-Secret` (TS03).
- OpenAPI documentation in Swagger UI.
- Unit tests (JUnit 5, Mockito) and BDD acceptance tests (Cucumber).

### Fixed
- The first changes of a new shopping session were not saved.
- A paid order is closed when the cart restarts.
