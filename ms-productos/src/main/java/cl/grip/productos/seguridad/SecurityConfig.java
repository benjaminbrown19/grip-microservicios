package cl.grip.productos.seguridad;

import cl.grip.productos.config.CorsFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, JwtValidador jwt,
                                 @Value("${grip.cors.origins}") String origenes) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(CorsFactory.desde(origenes)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos/**").permitAll()
                        // Endpoint interno: solo lo usa ms-ordenes dentro de la red de Docker.
                        // El gateway NO lo publica, por lo que no es accesible desde Internet.
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/v1/productos/**").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtFilter(jwt), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Autenticacion requerida"))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Acceso denegado")))
                .build();
    }
}
