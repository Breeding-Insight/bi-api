package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentQuery;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.model.Program;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SubEntityDatasetLockIntegrationTest extends BrAPITest {

    private Program program;
    private String experimentId;

    @Inject
    private BrAPITestUtils brAPITestUtils;

    @Inject
    BrAPITrialService brAPIITrialService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>) (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() throws Exception {
        var setup = brAPITestUtils.setupTestProgram(super.getBrapiDsl(), gson);
        program = setup.getV1();
        experimentId = brAPIITrialService.getTrialsByProgramId(program.getId()).stream()
                .map(BrAPITrial::getTrialDbId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void concurrentDatasetCreateReturnsSingleSuccessAndConflict() throws Exception {
        // Use a fresh name to avoid interference with other runs
        String datasetName = "LockTest-" + UUID.randomUUID();
        JsonObject request = new JsonObject();
        request.addProperty("name", datasetName);
        request.addProperty("repeatedMeasures", 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<HttpStatus> call = () -> {
            start.await(1, TimeUnit.SECONDS);
            try {
                Flowable<HttpResponse<String>> response = client.exchange(
                        HttpRequest.POST(String.format("/programs/%s/experiments/%s/dataset", program.getId(), experimentId), request.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bearerAuth("test-registered-user"),
                        String.class
                );
                return response.blockingFirst().getStatus();
            } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
                return e.getStatus();
            }
        };

        Future<HttpStatus> first = executor.submit(call);
        Future<HttpStatus> second = executor.submit(call);
        start.countDown();

        HttpStatus status1 = first.get(10, TimeUnit.SECONDS);
        HttpStatus status2 = second.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        List<HttpStatus> statuses = List.of(status1, status2);
        assertTrue(statuses.contains(HttpStatus.OK));
        assertTrue(statuses.contains(HttpStatus.CONFLICT));

        // Confirm only one dataset with that name exists
        Flowable<HttpResponse<String>> datasetsCall = client.exchange(
                HttpRequest.GET(String.format("/programs/%s/experiments/%s/datasets", program.getId(), experimentId))
                        .bearerAuth("test-registered-user"),
                String.class
        );
        HttpResponse<String> datasetsResponse = datasetsCall.blockingFirst();
        assertEquals(HttpStatus.OK, datasetsResponse.getStatus());
        JsonObject parsed = JsonParser.parseString(Objects.requireNonNull(datasetsResponse.body())).getAsJsonObject();
        JsonArray resultArray = parsed.has("result") && parsed.get("result").isJsonArray()
                ? parsed.getAsJsonArray("result")
                : null;
        long matching = 0;
        assertNotEquals(null, resultArray);
        for (int i = 0; i < resultArray.size(); i++) {
            String name = resultArray.get(i).getAsJsonObject().get("name").getAsString();
            if (name.equalsIgnoreCase(datasetName)) {
                matching++;
            }
        }
        assertEquals(1, matching);
    }
}
