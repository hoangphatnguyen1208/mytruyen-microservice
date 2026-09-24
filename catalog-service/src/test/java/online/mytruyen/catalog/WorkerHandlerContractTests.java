package online.mytruyen.catalog;

import online.mytruyen.catalog.messaging.LegacyCrawlPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static online.mytruyen.catalog.messaging.LegacyCrawlPublisher.Command.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Actual Go handlers -> actual Spring HTTP/JPA APIs; only source and RabbitMQ are mocked.
@EnabledIfEnvironmentVariable(named="RUN_WORKER_CONTRACT_TESTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:worker-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa","spring.datasource.password=","management.health.rabbit.enabled=false",
    "catalog.legacy-worker.enabled=true","catalog.legacy-worker.queue=test-only",
    "catalog.legacy-worker.status-map={\"9\":\"compat-status\"}"
})
class WorkerHandlerContractTests extends CatalogJwtTestSupport {
    @LocalServerPort int port;
    @MockitoBean LegacyCrawlPublisher publisher;
    @Test void originalGoHandlersWorkAgainstCompatibilityApi() throws Exception {
        var process=new ProcessBuilder("go","test","./task","-run","^TestCatalogCompatibilityContract$","-count=1","-v")
            .directory(Path.of("..","worker").toFile()).redirectErrorStream(true);
        process.environment().put("WORKER_CONTRACT_URL","http://localhost:"+port+"/api/v1");
        process.environment().put("WORKER_CONTRACT_JWT",token("ROLE_IMPORTER"));
        var running=process.start();
        // Stream into memory asynchronously so the child cannot block on a full output pipe.
        var output=java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try { return new String(running.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        try {
            assertThat(running.waitFor(90,TimeUnit.SECONDS)).as("Go contract timed out").isTrue();
            assertThat(running.exitValue()).as(output.get(5,TimeUnit.SECONDS)).isZero();
        } finally { if (running.isAlive()) running.destroyForcibly(); }
        verify(publisher,atLeast(2)).publish(CHAPTERS,777L);
        verify(publisher,atLeastOnce()).publish(CHAPTERS,778L);
        verify(publisher,atLeastOnce()).publish(BOOK,777L);
        verify(publisher,atLeastOnce()).publish(BOOK,778L);
    }
}
