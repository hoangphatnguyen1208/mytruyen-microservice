package online.mytruyen.catalog.service;

import online.mytruyen.catalog.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;
import tools.jackson.core.JacksonException;
import java.net.*;
import java.net.http.*;
import java.io.IOException;
import java.time.Duration;

@Service
public class TopboxesService {
    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper json;
    public TopboxesService(@Value("${catalog.topboxes.url:https://backend.metruyencv.com/api/topboxes}") String endpoint,
            @Value("${catalog.topboxes.timeout-ms:5000}") long timeoutMs, ObjectMapper json) {
        this.endpoint=URI.create(endpoint);
        if (!("https".equals(this.endpoint.getScheme()) || "http".equals(this.endpoint.getScheme()))
                || this.endpoint.getHost()==null || this.endpoint.getRawQuery()!=null || this.endpoint.getFragment()!=null
                || this.endpoint.getUserInfo()!=null || timeoutMs<1 || timeoutMs>30000)
            throw new IllegalArgumentException("Invalid topboxes endpoint or timeout");
        this.timeout=Duration.ofMillis(timeoutMs); this.json=json;
        client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(timeoutMs,2000)))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public JsonNode get(int kind,int limit) {
        if (kind<0 || limit<5 || limit>50) throw new ApiException(400,"kind must be nonnegative and limit between 5 and 50");
        var uri=URI.create(endpoint+"?filter%5Btopboxable.kind%5D="+kind+"&limit="+limit);
        try {
            var request=HttpRequest.newBuilder(uri).timeout(timeout).header("Accept","application/json").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()==400 || response.statusCode()==422)
                throw new ApiException(400,"Topboxes parameters rejected by upstream");
            if (response.statusCode()<200 || response.statusCode()>=300) throw new ApiException(502,"Topboxes upstream unavailable");
            JsonNode body=json.readTree(response.body());
            if (body==null || (!body.isObject() && !body.isArray())) throw new ApiException(502,"Invalid topboxes response");
            return body;
        } catch (HttpTimeoutException e) { throw new ApiException(504,"Topboxes upstream timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(503,"Topboxes request interrupted"); }
        catch (IOException | JacksonException e) { throw new ApiException(502,"Topboxes upstream unavailable"); }
    }
}
