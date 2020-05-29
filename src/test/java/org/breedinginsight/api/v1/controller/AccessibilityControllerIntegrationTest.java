package org.breedinginsight.api.v1.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AccessibilityControllerIntegrationTest {

    private String validAccessibilityId;
    private String validAccessibilityName;
    private String invalidAccessibilityId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @Test
    @Order(1)
    // Expects at least one valid accessibility in the database to pass
    void getAccessibilitiesSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                                                              GET("/accessibility_options")
                                                              .contentType(MediaType.APPLICATION_JSON)
                                                              .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
                                                              );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        JsonObject accessibility = data.get(0).getAsJsonObject();

        assertNotEquals(accessibility.get("id").getAsString(),null, "Missing accessibility id");
        assertNotEquals(accessibility.get("name").getAsString(), null, "Missing accessibility name");

        validAccessibilityId = accessibility.get("id").getAsString();
        validAccessibilityName = accessibility.get("name").getAsString();
    }

    @Test
    @Order(2)
    void getAccessibilitiesSingleSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                                                              GET("/accessibility_options/"+validAccessibilityId)
                                                              .contentType(MediaType.APPLICATION_JSON)
                                                              .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
                                                              );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(result.get("id").getAsString(), validAccessibilityId, "Wrong accessibility id");
        assertEquals(result.get("name").getAsString(), validAccessibilityName, "Wrong accessibility name");
    }

    @Test
    void getAccessibilitiesSingleInvalid() {

        Flowable<HttpResponse<String>> call = client.exchange(
                                                              GET("/accessibilities/"+invalidAccessibilityId)
                                                              .contentType(MediaType.APPLICATION_JSON)
                                                              .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
                                                              );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
                HttpResponse<String> response = call.blockingFirst();
            });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

}
