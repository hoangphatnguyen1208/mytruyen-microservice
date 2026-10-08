package online.mytruyen.identity.config;

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
        return api -> {
            for (String path : java.util.List.of("/api/v1/auth/login", "/api/v1/auth/register",
                    "/api/v1/auth/login/access-token", "/api/v1/auth/refresh-token", "/api/v1/auth/logout")) {
                var item = api.getPaths().get(path);
                if (item != null && item.getPost() != null) {
                    item.getPost().setSecurity(java.util.List.of());
                }
            }
        };
    }

    @Bean
    OpenAPI serviceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("MyTruyen Identity API").version("v1"))
                .addServersItem(new Server().url("/"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
