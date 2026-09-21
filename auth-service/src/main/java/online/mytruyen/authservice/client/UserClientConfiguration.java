package online.mytruyen.authservice.client;

import feign.RequestInterceptor;
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
}
