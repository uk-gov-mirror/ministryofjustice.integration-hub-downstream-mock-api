package uk.gov.justice.digital.hmpps.templatepackagename

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.http.HttpStatus.CREATED
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.util.UUID

private const val DOWNSTREAM_MOCK_ROLE = "ORCHESTRATION_CLIENT"

@RestController
@Validated
@PreAuthorize("hasRole('$DOWNSTREAM_MOCK_ROLE') and hasAuthority('SCOPE_benefit-checks.assess')")
@RequestMapping("/v1/benefit-checks", produces = ["application/json"])
@Tag(
  name = "Benefit checks",
  description = "Mock downstream provider endpoints that simulate a DWP-style benefit checker API.",
)
class BenefitAssessmentResource(
  private val benefitAssessmentService: BenefitAssessmentService,
) {

  @PostMapping("/assessments", consumes = ["application/json"])
  @ResponseStatus(CREATED)
  @Operation(
    summary = "Assess benefit eligibility",
    description = "Runs a deterministic mock eligibility assessment so the orchestration layer can exercise a realistic downstream journey.",
    security = [SecurityRequirement(name = "bearerAuth")],
    responses = [
      ApiResponse(responseCode = "201", description = "Assessment created"),
      ApiResponse(
        responseCode = "400",
        description = "Request validation failed",
        content = [Content(schema = Schema(implementation = uk.gov.justice.hmpps.kotlin.common.ErrorResponse::class))],
      ),
      ApiResponse(responseCode = "401", description = "Missing or invalid OAuth2 access token"),
      ApiResponse(responseCode = "403", description = "Authenticated caller does not have permission to use the API"),
    ],
  )
  fun assessBenefits(
    @Valid @RequestBody request: BenefitAssessmentRequest,
  ): BenefitAssessmentResponse = benefitAssessmentService.assess(request)
}

data class BenefitAssessmentRequest(
  @field:NotBlank(message = "firstName must be supplied")
  val firstName: String,
  @field:NotBlank(message = "lastName must be supplied")
  val lastName: String,
  @field:Pattern(
    regexp = "^[A-CEGHJ-PR-TW-Z]{2}\\d{6}[A-D]$",
    message = "nino must be a valid National Insurance number",
  )
  val nino: String,
  @field:NotNull(message = "dateOfBirth must be supplied")
  @field:Past(message = "dateOfBirth must be in the past")
  val dateOfBirth: LocalDate,
  @field:NotEmpty(message = "claimedBenefits must contain at least one benefit")
  val claimedBenefits: Set<ClaimedBenefit>,
  @field:PositiveOrZero(message = "annualIncome must be zero or greater")
  val annualIncome: BigDecimal,
  @field:PositiveOrZero(message = "savingsAmount must be zero or greater")
  val savingsAmount: BigDecimal,
  @field:PositiveOrZero(message = "housingCostsPerMonth must be zero or greater")
  val housingCostsPerMonth: BigDecimal,
  @field:PositiveOrZero(message = "dependantChildren must be zero or greater")
  @field:Max(value = 12, message = "dependantChildren must be 12 or fewer for the mock service")
  val dependantChildren: Int,
  val disabledApplicant: Boolean,
  val caringResponsibilities: Boolean,
  @field:Pattern(
    regexp = "^[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2}$",
    message = "postcode must be a valid UK postcode",
  )
  val postcode: String,
)

enum class ClaimedBenefit {
  UNIVERSAL_CREDIT,
  CHILD_BENEFIT,
  HOUSING_BENEFIT,
  PENSION_CREDIT,
  DISABILITY_BENEFIT,
  CARERS_ALLOWANCE,
}

data class BenefitAssessmentResponse(
  val assessmentId: UUID,
  val decision: AssessmentDecision,
  val matchedEntitlements: List<MatchedEntitlement>,
  val riskFlags: List<String>,
  val processedAt: OffsetDateTime,
  val decisionSummary: String,
)

enum class AssessmentDecision {
  ELIGIBLE,
  REFER_FOR_REVIEW,
  NOT_ELIGIBLE,
}

data class MatchedEntitlement(
  val code: String,
  val title: String,
  val reason: String,
)

@org.springframework.stereotype.Service
class BenefitAssessmentService {
  fun assess(request: BenefitAssessmentRequest): BenefitAssessmentResponse {
    val age = Period.between(request.dateOfBirth, LocalDate.now(ZoneOffset.UTC)).years
    val matchedEntitlements = buildList {
      if (request.claimedBenefits.contains(ClaimedBenefit.UNIVERSAL_CREDIT) && request.annualIncome <= BigDecimal("22000")) {
        add(
          MatchedEntitlement(
            code = "UC-HOUSING-SUPPORT",
            title = "Universal Credit Housing Support",
            reason = "Universal Credit claim with income at or below the mock threshold.",
          ),
        )
      }
      if (request.dependantChildren > 0 && request.annualIncome <= BigDecimal("40000")) {
        add(
          MatchedEntitlement(
            code = "CHILD-TOP-UP",
            title = "Child Support Top Up",
            reason = "Household has dependant children and remains within the mock income threshold.",
          ),
        )
      }
      if (request.disabledApplicant && request.savingsAmount <= BigDecimal("16000")) {
        add(
          MatchedEntitlement(
            code = "DISABILITY-SUPPORT",
            title = "Disability Living Support",
            reason = "Disability indicator present and savings below the mock cap.",
          ),
        )
      }
      if (request.caringResponsibilities) {
        add(
          MatchedEntitlement(
            code = "CARER-SUPPORT",
            title = "Carer Support Supplement",
            reason = "Caring responsibilities recorded in the request.",
          ),
        )
      }
      if (age >= 66 && request.annualIncome <= BigDecimal("30000")) {
        add(
          MatchedEntitlement(
            code = "PENSION-CREDIT-TOP-UP",
            title = "Pension Credit Top Up",
            reason = "Claimant is of pension age and income is within the mock threshold.",
          ),
        )
      }
    }

    val riskFlags = buildList {
      if (age < 18) add("UNDERAGE_CLAIMANT")
      if (request.savingsAmount > BigDecimal("16000")) add("HIGH_SAVINGS")
      if (request.annualIncome > BigDecimal("50000")) add("HIGH_INCOME")
      if (request.nino.endsWith("D")) add("IDENTITY_REVIEW_REQUIRED")
    }

    val decision = when {
      age < 18 -> AssessmentDecision.NOT_ELIGIBLE
      matchedEntitlements.isEmpty() -> AssessmentDecision.NOT_ELIGIBLE
      riskFlags.isNotEmpty() -> AssessmentDecision.REFER_FOR_REVIEW
      else -> AssessmentDecision.ELIGIBLE
    }

    val summary = when (decision) {
      AssessmentDecision.ELIGIBLE -> "Eligible for ${matchedEntitlements.size} mocked entitlement(s)."
      AssessmentDecision.REFER_FOR_REVIEW -> "Potential entitlement identified, but manual review is required."
      AssessmentDecision.NOT_ELIGIBLE -> "No mocked entitlements matched the supplied claim details."
    }

    return BenefitAssessmentResponse(
      assessmentId = UUID.nameUUIDFromBytes("${request.nino}:${request.dateOfBirth}:${request.postcode}".toByteArray()),
      decision = decision,
      matchedEntitlements = matchedEntitlements,
      riskFlags = riskFlags,
      processedAt = OffsetDateTime.now(ZoneOffset.UTC),
      decisionSummary = summary,
    )
  }
}
