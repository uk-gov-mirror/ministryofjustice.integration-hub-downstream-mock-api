package uk.gov.justice.digital.hmpps.templatepackagename.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfiguration(
  @param:Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuer: String,
  @param:Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") private val jwks: String,
  @param:Value("\${downstream-mock.oauth2.audience}") private val audience: String,
) {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
    .csrf { it.disable() }
    .formLogin { it.disable() }
    .httpBasic { it.disable() }
    .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(authenticationConverter()) } }
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    .authorizeHttpRequests {
      it.requestMatchers("/health/**", "/info", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**").permitAll()
      it.anyRequest().authenticated()
    }
    .build()

  @Bean
  fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwks).build().also {
    it.setJwtValidator(
      DelegatingOAuth2TokenValidator(
        JwtValidators.createDefaultWithIssuer(issuer),
        OAuth2TokenValidator<Jwt> { jwt ->
          if (jwt.audience.contains(audience) && jwt.expiresAt != null && !jwt.subject.isNullOrBlank()) {
            OAuth2TokenValidatorResult.success()
          } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Required access token claims are missing or invalid", null))
          }
        },
      ),
    )
  }

  private fun authenticationConverter(): JwtAuthenticationConverter {
    val roles = JwtGrantedAuthoritiesConverter().apply {
      setAuthoritiesClaimName("roles")
      setAuthorityPrefix("ROLE_")
    }
    val scopes = JwtGrantedAuthoritiesConverter()
    return JwtAuthenticationConverter().apply {
      setJwtGrantedAuthoritiesConverter { jwt -> scopes.convert(jwt) + roles.convert(jwt) }
    }
  }
}
