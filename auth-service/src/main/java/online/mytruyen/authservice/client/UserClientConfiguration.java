package online.mytruyen.authservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import online.mytruyen.authservice.exception.UnauthorizedException;
import online.mytruyen.authservice.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserClientConfiguration {
    @Bean
    RequestInterceptor internalApiKeyInterceptor(
            @Value("${app.internal-api-key}") String apiKey
    ) {
        return template ->
                template.header("X-Internal-Api-Key", apiKey);
    }

    @Bean
    ErrorDecoder userClientErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new UnauthorizedException("Invalid email or password");
            case 409 -> new ConflictException("Email or username already exists");
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
