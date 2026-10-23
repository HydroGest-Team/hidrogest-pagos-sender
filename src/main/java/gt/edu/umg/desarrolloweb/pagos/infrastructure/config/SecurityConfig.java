package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Health público (sin detalles); métricas y {@code /admin} solo con Basic Auth del usuario admin.
 * El servicio no expone nada más.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AdminProperties admin) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/liveness",
                            "/actuator/health/readiness", "/actuator/info").permitAll();
                    if (admin.habilitado()) {
                        auth.requestMatchers("/actuator/**", "/admin/**").hasRole("ADMIN");
                    }
                    auth.anyRequest().denyAll();
                })
                .httpBasic(basic -> { })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService users(AdminProperties admin, PasswordEncoder encoder) {
        if (!admin.habilitado()) {
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(admin.usuario())
                .password(encoder.encode(admin.password()))
                .roles("ADMIN")
                .build());
    }
}
