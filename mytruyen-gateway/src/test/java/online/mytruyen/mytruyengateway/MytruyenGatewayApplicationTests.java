package online.mytruyen.mytruyengateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class MytruyenGatewayApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.cloud.gateway.route.RouteDefinitionLocator routes;

    @Test
    void routesImportToCatalog() {
        var catalog=routes.getRouteDefinitions().filter(r -> r.getId().equals("catalog-service")).blockFirst();
        org.assertj.core.api.Assertions.assertThat(catalog).isNotNull();
        org.assertj.core.api.Assertions.assertThat(catalog.getPredicates().stream()
            .filter(p -> p.getName().equals("Path")).flatMap(p -> p.getArgs().values().stream()).toList())
            .contains("/api/v1/internal/import/**","/api/v1/rabbitmq/**","/api/v1/worker/**");
    }

    @Test
    void contextLoads() {
    }

}
