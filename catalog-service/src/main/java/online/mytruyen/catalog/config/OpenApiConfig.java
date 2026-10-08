package online.mytruyen.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    org.springdoc.core.customizers.OpenApiCustomizer publicEndpointSecurity() {
        return api -> api.getPaths().forEach((path, item) -> {
            boolean publicRead = java.util.List.of("/api/v1/books", "/api/v1/chapters",
                    "/api/v1/stats", "/api/v1/authors", "/api/v1/genres", "/api/v1/tags",
                    "/api/v1/book-statuses").stream()
                    .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"))
                    || path.equals("/api/v1/{kind}") || path.equals("/api/v1/{kind}/{slug}");
            if (publicRead && item.getGet() != null) {
                item.getGet().setSecurity(java.util.List.of());
            }
        });
    }
    @Bean
    OpenAPI serviceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("MyTruyen Catalog API").version("v1"))
                .addServersItem(new Server().url("/"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
