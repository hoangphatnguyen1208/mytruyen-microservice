package online.mytruyen.catalog;

import online.mytruyen.catalog.messaging.LegacyCrawlPublisher;
import online.mytruyen.catalog.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static online.mytruyen.catalog.messaging.LegacyCrawlPublisher.Command.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:legacy-crawl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","management.health.rabbit.enabled=false"
})
class LegacyCrawlApiTests extends CatalogJwtTestSupport {
    @LocalServerPort int port;
    @MockitoBean LegacyCrawlPublisher publisher;
    final HttpClient http=HttpClient.newHttpClient();
    int post(String path,String body,String role) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/rabbitmq/"+path))
            .header("Content-Type","application/json");
        if (role!=null) request.header("Authorization","Bearer "+token(role));
        var response=http.send(request.POST(body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()==200) assertThat(response.body()).contains("\"success\":true","\"data\":null");
        return response.statusCode();
    }
    @Test void preservesAllLegacyRoutesAndBodyShapes() throws Exception {
        for (String path:List.of("genres","tags","book-statuses","all-books")) assertThat(post(path,null,"ROLE_ADMIN")).isEqualTo(200);
        assertThat(post("book","{\"book_id\":123}","ROLE_IMPORTER")).isEqualTo(200);
        assertThat(post("chapters","{\"book_id\":123}","ROLE_IMPORTER")).isEqualTo(200);
        verify(publisher).publish(GENRES,null); verify(publisher).publish(TAGS,null);
        verify(publisher).publish(STATUSES,null); verify(publisher).publish(ALL_BOOKS,null);
        verify(publisher).publish(BOOK,123L); verify(publisher).publish(CHAPTERS,123L);
    }
    @Test void rejectsAnonymousReaderAndInvalidBody() throws Exception {
        assertThat(post("book","{\"book_id\":123}",null)).isEqualTo(401);
        assertThat(post("book","{\"book_id\":123}","ROLE_USER")).isEqualTo(403);
        for (String body:List.of("{}","{\"book_id\":0}","{\"book_id\":-1}","{\"book_id\":1,\"queue\":\"other\"}"))
            assertThat(post("book",body,"ROLE_IMPORTER")).isEqualTo(400);
        verifyNoInteractions(publisher);
    }
    @Test void returnsUnavailableWhenPublisherCannotConfirm() throws Exception {
        doThrow(new ApiException(503,"Crawl task delivery not confirmed")).when(publisher).publish(BOOK,123L);
        assertThat(post("book","{\"book_id\":123}","ROLE_IMPORTER")).isEqualTo(503);
    }
}
