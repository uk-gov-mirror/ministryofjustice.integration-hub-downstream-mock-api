package uk.gov.justice.digital.hmpps.templatepackagename.integration

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

object TestTokens {
  const val ISSUER = "https://test-issuer.invalid"
  const val AUDIENCE = "downstream-benefit-checker"
  private val key = RSAKeyGenerator(2048).keyID("test-key").generate()
  private val wrongKey = RSAKeyGenerator(2048).keyID("other-key").generate()
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    createContext("/jwks") { exchange ->
      val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }
    start()
  }
  val jwksUrl = "http://127.0.0.1:${server.address.port}/jwks"

  fun token(
    issuer: String = ISSUER,
    audience: String = AUDIENCE,
    scope: String = "benefit-checks.assess",
    roles: List<String> = listOf("ORCHESTRATION_CLIENT"),
    expiresAt: Instant? = Instant.now().plusSeconds(300),
    notBefore: Instant = Instant.now().minusSeconds(5),
    wrongSignature: Boolean = false,
  ): String {
    val claims = JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("pilot-client")
      .issueTime(Date.from(Instant.now())).notBeforeTime(Date.from(notBefore))
      .claim("scope", scope).claim("roles", roles)
    expiresAt?.let { claims.expirationTime(Date.from(it)) }
    return SignedJWT(
      JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).type(JOSEObjectType.JWT).build(),
      claims.build(),
    ).apply { sign(RSASSASigner(if (wrongSignature) wrongKey else key)) }.serialize()
  }
}
