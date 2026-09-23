package online.mytruyen.catalog.security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.DispatcherType;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, JwtFilter jwt) throws Exception {
        return http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/books", "/api/v1/books/**",
                    "/api/v1/chapters", "/api/v1/chapters/**",
                    "/api/v1/authors", "/api/v1/authors/**", "/api/v1/genres", "/api/v1/genres/**",
                    "/api/v1/tags", "/api/v1/tags/**", "/api/v1/book-statuses", "/api/v1/book-statuses/**").permitAll()
                .requestMatchers("/api/v1/**").hasRole("ADMIN").anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> JwtFilter.failure(res,401,"Authentication required"))
                .accessDeniedHandler((req,res,ex) -> JwtFilter.failure(res,403,"Admin access required")))
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean FilterRegistrationBean<JwtFilter> filterRegistration(JwtFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
