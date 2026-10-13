package uk.gov.justice.digital.hmpps.templatepackagename.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.time.LocalDate

class ExampleResourceTest : IntegrationTestBase() {

  @Nested
  @DisplayName("POST /v1/benefit-checks/assessments")
  inner class BenefitAssessmentEndpoint {

    @Test
    fun `should return unauthorized if no credentials`() {
      webTestClient.post()
        .uri("/v1/benefit-checks/assessments")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `should return unauthorized if token is malformed`() {
      webTestClient.post()
        .uri("/v1/benefit-checks/assessments")
        .headers { it.setBearerAuth("invalid-token") }
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `should validate request payload`() {
      webTestClient.post()
        .uri("/v1/benefit-checks/assessments")
        .headers(setAuthorisation())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validRequest().replace("AA123456A", "BAD-NINO"))
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return an assessment response`() {
      webTestClient.post()
        .uri("/v1/benefit-checks/assessments")
        .headers(setAuthorisation())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody()
        .jsonPath("decision").isEqualTo("ELIGIBLE")
        .jsonPath("matchedEntitlements.length()").isEqualTo(3)
        .jsonPath("riskFlags.length()").isEqualTo(0)
        .jsonPath("assessmentId").value<String> {
          assertThat(it).isNotBlank()
        }
    }

    @Test
    fun `should flag claims for manual review when savings are high`() {
      val request = """
        {
          "firstName": "Jordan",
          "lastName": "Taylor",
          "nino": "AA123456A",
          "dateOfBirth": "${LocalDate.now().minusYears(40)}",
          "claimedBenefits": ["UNIVERSAL_CREDIT"],
          "annualIncome": ${BigDecimal("18000")},
          "savingsAmount": ${BigDecimal("20000")},
          "housingCostsPerMonth": ${BigDecimal("900")},
          "dependantChildren": 0,
          "disabledApplicant": false,
          "caringResponsibilities": false,
          "postcode": "SW1A 1AA"
        }
      """.trimIndent()

      webTestClient.post()
        .uri("/v1/benefit-checks/assessments")
        .headers(setAuthorisation())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody()
        .jsonPath("decision").isEqualTo("REFER_FOR_REVIEW")
        .jsonPath("riskFlags[0]").isEqualTo("HIGH_SAVINGS")
    }
  }
}

private fun validRequest(): String = """
  {
    "firstName": "Alex",
    "lastName": "Morgan",
    "nino": "AA123456A",
    "dateOfBirth": "${LocalDate.now().minusYears(35)}",
    "claimedBenefits": ["UNIVERSAL_CREDIT", "CHILD_BENEFIT"],
    "annualIncome": 21000,
    "savingsAmount": 1000,
    "housingCostsPerMonth": 950,
    "dependantChildren": 2,
    "disabledApplicant": true,
    "caringResponsibilities": false,
    "postcode": "SW1A 1AA"
  }
""".trimIndent()
