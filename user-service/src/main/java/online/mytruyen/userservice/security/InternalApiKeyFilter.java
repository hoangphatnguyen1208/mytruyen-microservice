package online.mytruyen.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {
    private final String expectedApiKey;

    public InternalApiKeyFilter(
            @Value("${app.intern-api-key}") String expectedApiKey
    ) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    public boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/");
    }

    @Override
    public void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String suppliedApiKey = request.getHeader("X-Intern-Api-Key");

//        if (!MessageDigest.isEqual(
//                expectedApiKey.getBytes(StandardCharsets.UTF_8),
//                Objects.toString(suppliedApiKey, "").getBytes(StandardCharsets.UTF_8)
//        )) {
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }

        chain.doFilter(request, response);
    }
}
