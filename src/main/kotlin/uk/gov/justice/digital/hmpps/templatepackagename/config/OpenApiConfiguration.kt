package uk.gov.justice.digital.hmpps.templatepackagename.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version!!

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://benefit-checker-mock-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("https://benefit-checker-mock-preprod.hmpps.service.justice.gov.uk").description("Pre-Production"),
        Server().url("https://benefit-checker-mock.hmpps.service.justice.gov.uk").description("Production"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .tags(
      listOf(),
    )
    .info(
      Info().title("Benefit Checker Mock API").version(version)
        .description("Mock downstream provider API used to simulate a DWP-style benefit checker journey through the Integration Hub platform.")
        .contact(Contact().name("Integration Hub Team").email("integration-hub@justice.gov.uk")),
    )
    .components(
      io.swagger.v3.oas.models.Components()
        .addSecuritySchemes("bearerAuth", SecurityScheme().addBearerAuthRequirement("ORCHESTRATION_CLIENT")),
    )
    .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
}

private fun SecurityScheme.addBearerAuthRequirement(role: String): SecurityScheme = type(SecurityScheme.Type.HTTP)
  .scheme("bearer")
  .bearerFormat("JWT")
  .`in`(SecurityScheme.In.HEADER)
  .name("Authorization")
  .description("OAuth2 access token with the `$role` role and `benefit-checks.assess` scope.")
