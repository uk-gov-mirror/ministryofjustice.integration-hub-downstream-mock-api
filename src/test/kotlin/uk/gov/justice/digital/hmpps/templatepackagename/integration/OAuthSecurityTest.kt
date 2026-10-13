package uk.gov.justice.digital.hmpps.templatepackagename.integration

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.time.Instant

class OAuthSecurityTest : IntegrationTestBase() {
  private fun request(token: String) = webTestClient.post()
    .uri("/v1/benefit-checks/assessments")
    .headers { it.setBearerAuth(token) }
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue("""{"firstName":"Alex","lastName":"Morgan","nino":"AA123456A","dateOfBirth":"1990-01-01","claimedBenefits":["UNIVERSAL_CREDIT"],"annualIncome":21000,"savingsAmount":1000,"housingCostsPerMonth":950,"dependantChildren":2,"disabledApplicant":true,"caringResponsibilities":false,"postcode":"SW1A 1AA"}""")
    .exchange()

  @Test
  fun `reject invalid token trust and time claims`() {
    listOf(
      TestTokens.token(issuer = "https://other.invalid"),
      TestTokens.token(audience = "another-api"),
      TestTokens.token(expiresAt = Instant.now().minusSeconds(120)),
      TestTokens.token(expiresAt = null),
      TestTokens.token(notBefore = Instant.now().plusSeconds(120)),
      TestTokens.token(wrongSignature = true),
    ).forEach { request(it).expectStatus().isUnauthorized }
  }

  @Test
  fun `require both role and scope`() {
    request(TestTokens.token(roles = emptyList())).expectStatus().isForbidden
    request(TestTokens.token(scope = "another.scope")).expectStatus().isForbidden
    request(TestTokens.token()).expectStatus().isCreated
  }

  @Test
  fun `basic credentials no longer authenticate`() {
    webTestClient.post().uri("/v1/benefit-checks/assessments")
      .headers { it.setBasicAuth("orchestration-client", "orchestration-secret") }
      .exchange().expectStatus().isUnauthorized
  }
}
