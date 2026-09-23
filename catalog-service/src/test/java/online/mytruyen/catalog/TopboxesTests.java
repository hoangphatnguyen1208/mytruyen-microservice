package online.mytruyen.catalog;

import com.sun.net.httpserver.HttpServer;
import online.mytruyen.catalog.service.TopboxesService;
import online.mytruyen.catalog.exception.ApiException;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class TopboxesTests {
    HttpServer server;
    String query;
    int code=200;
    String body="{\"data\":[{\"name\":\"Upstream book\"}]}";
    long delay;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/topboxes",exchange->{
            query=exchange.getRequestURI().getRawQuery();
            try { if(delay>0) Thread.sleep(delay); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(code,bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    TopboxesService service(long timeout) {
        return new TopboxesService("http://127.0.0.1:"+server.getAddress().getPort()+"/topboxes",timeout,new ObjectMapper());
    }
    @Test void preservesUpstreamShapeAndEscapedFilter() {
        assertThat(service(2000).get(1,10).path("data").get(0).path("name").asText()).isEqualTo("Upstream book");
        assertThat(query).isEqualTo("filter%5Btopboxable.kind%5D=1&limit=10");
    }
    @Test void validatesInputBeforeCallingUpstream() {
        assertThatThrownBy(()->service(2000).get(-1,10)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(400));
        assertThatThrownBy(()->service(2000).get(1,101)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(400));
        assertThat(query).isNull();
    }
    @Test void rejectsUpstreamErrorsRedirectsAndMalformedJson() {
        code=503;
        assertThatThrownBy(()->service(2000).get(1,10)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(502));
        code=302;
        assertThatThrownBy(()->service(2000).get(1,10)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(502));
        code=200; body="<html>bad gateway</html>";
        assertThatThrownBy(()->service(2000).get(1,10)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(502));
    }
    @Test void enforcesDeadline() {
        delay=500;
        assertThatThrownBy(()->service(100).get(1,10)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(504));
    }
}
