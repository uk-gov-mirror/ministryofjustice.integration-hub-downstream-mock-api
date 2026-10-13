# Integration Hub Downstream Mock API

Standalone mock API that simulates a DWP-style benefit checker dependency for the Integration Hub end-to-end flow.

See the API Platform's
[pilot mock API end-to-end flow](https://github.com/ministryofjustice/integration-hub-api-platform/blob/main/docs/pilot-mock-api-flow.md)
for the complete journey from the upstream client through Integration Hub to
this downstream provider.

## What this service does

- Exposes an independently authenticated downstream endpoint at `POST /v1/benefit-checks/assessments`
- Uses deterministic business rules so orchestration flows can be tested repeatedly
- Protects the endpoint with OAuth2 JWT access tokens, an orchestration role and an assessment scope

## Authentication

The API accepts an OAuth2 JWT access token issued by the configured authorisation
server. Basic credentials are no longer accepted. Configure:

| Variable | Purpose |
| --- | --- |
| `DOWNSTREAM_MOCK_OAUTH2_ISSUER` | Exact approved token issuer |
| `DOWNSTREAM_MOCK_JWK_SET_URI` | Approved HTTPS signing-key endpoint |
| `DOWNSTREAM_MOCK_OAUTH2_AUDIENCE` | Audience dedicated to this provider API |

Tokens must have a valid RS256 signature, matching issuer and audience, an expiry
and subject, `roles: ["ORCHESTRATION_CLIENT"]` and the `benefit-checks.assess` scope.
Invalid tokens receive 401; valid tokens without the role or scope receive 403.
The service does not issue tokens. Obtain one using the provider's approved OAuth2
client registration; do not reuse a consumer token for the downstream boundary.

Health endpoints remain public for infrastructure probes. No local production
credential defaults or signing keys are provided.

## Running locally

```bash
GRADLE_USER_HOME=/tmp/integration-hub-downstream-mock-api-gradle ./gradlew bootRun --args='--spring.profiles.active=dev'
```

Swagger UI is available locally at:

- `http://localhost:8080/swagger-ui/index.html`

## Example request

```bash
curl -i \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/v1/benefit-checks/assessments \
  -d '{
    "firstName": "Alex",
    "lastName": "Morgan",
    "nino": "AA123456A",
    "dateOfBirth": "1990-01-01",
    "claimedBenefits": ["UNIVERSAL_CREDIT", "CHILD_BENEFIT"],
    "annualIncome": 21000,
    "savingsAmount": 1000,
    "housingCostsPerMonth": 950,
    "dependantChildren": 2,
    "disabledApplicant": true,
    "caringResponsibilities": false,
    "postcode": "SW1A 1AA"
  }'
```

## Tests

```bash
GRADLE_USER_HOME=/tmp/integration-hub-downstream-mock-api-gradle ./gradlew test
```

## Deployment direction

The development deployment targets the dedicated Modernisation Platform ECS service:

- ECR repository: `integration-hub-downstream-mock-api`
- ECS cluster and service: `integration-hub-downstream-mock-api`
- GitHub environment: `integration-hub-downstream-mock-api-development`
- AWS authentication: GitHub OIDC using the scoped application deployment role

Pull requests run the test suite. A push to `main` builds an immutable commit-SHA image, pushes it to ECR, registers a new task-definition revision and waits for the ECS service to become stable.

The temporary `aws-ecs-deployment` branch trigger also publishes an immutable commit-SHA image so the initial Terraform-managed service can be bootstrapped before this workflow reaches `main`.


## OAuth2 rollout and verification

The local test suite passed 24 tests, including requests signed with a test RSA key
served through a local JWKS endpoint. It covers valid access, invalid signatures,
wrong issuer/audience, expired or missing expiry, future validity, missing role or
scope, and rejection of the old Basic credentials. The test JWT helper bypass is
disabled so these requests use the application's actual decoder.

This change is not yet deployed or verified in non-production. Before merging
(the main workflow deploys automatically), register the approved OAuth2 client,
configure the three environment variables above and coordinate the platform's
OAuth2 downstream client deployment. Check health, an authorised assessment and
401/403 cases in development.

mTLS termination, trust stores, client certificates and DWP/HMRC-wide integration
coverage are not implemented by this application change. The pilot SOW remains
open until those controls and the full deployed journey are verified.
