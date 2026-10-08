package online.mytruyen.engagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI serviceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("MyTruyen Engagement API").version("v1"))
                .addServersItem(new Server().url("/"));
    }
}
