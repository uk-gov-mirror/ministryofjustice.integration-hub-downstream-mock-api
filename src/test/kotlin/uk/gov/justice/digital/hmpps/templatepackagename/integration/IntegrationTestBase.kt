package uk.gov.justice.digital.hmpps.templatepackagename.integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  internal fun setAuthorisation(): (HttpHeaders) -> Unit = { headers ->
    headers.setBearerAuth(TestTokens.token())
  }

  companion object {
    @JvmStatic
    @DynamicPropertySource
    fun oauthProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { TestTokens.ISSUER }
      registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") { TestTokens.jwksUrl }
      registry.add("downstream-mock.oauth2.audience") { TestTokens.AUDIENCE }
    }
  }
}
