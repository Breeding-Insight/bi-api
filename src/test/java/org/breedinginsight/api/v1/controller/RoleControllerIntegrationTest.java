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
public class RoleControllerIntegrationTest {

    private String validRoleId;
    private String validRoleDomain;
    private String invalidRoleId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @Test
    @Order(1)
    // Expects at least one valid role in the database to pass
    void getRolesSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        JsonObject role = data.get(0).getAsJsonObject();

        assertNotEquals(role.get("id").getAsString(),null, "Missing role id");
        assertNotEquals(role.get("domain").getAsString(), null, "Missing role domain");

        validRoleId = role.get("id").getAsString();
        validRoleDomain = role.get("domain").getAsString();
    }

    @Test
    @Order(2)
    void getRolesSingleSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/roles/"+validRoleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(result.get("id").getAsString(), validRoleId, "Wrong role id");
        assertEquals(result.get("domain").getAsString(), validRoleDomain, "Wrong domain");
    }

    @Test
    void getRolesSingleInvalid() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/roles/"+invalidRoleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

}
