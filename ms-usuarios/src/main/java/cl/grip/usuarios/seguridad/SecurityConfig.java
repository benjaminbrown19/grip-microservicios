package cl.grip.usuarios.seguridad;

import cl.grip.usuarios.config.CorsFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, @Value("${grip.cors.origins}") String origenes) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(CorsFactory.desde(origenes)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**", "/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
