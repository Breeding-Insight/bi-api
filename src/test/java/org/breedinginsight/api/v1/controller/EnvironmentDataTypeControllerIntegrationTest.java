/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.breedinginsight.DatabaseTest;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EnvironmentDataTypeControllerIntegrationTest extends DatabaseTest {

    private String validEnvironmentTypeId;
    private String validEnvironmentTypeName;
    private String invalidEnvironmentTypeId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @AfterAll
    public void finish() { super.stopContainers(); }

    @Test
    @Order(1)
    // Expects at least one valid environmentType in the database to pass
    void getEnvironmentTypesSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                                                              GET("/environment-data-types")
                                                              .contentType(MediaType.APPLICATION_JSON)
                                                              .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
                                                              );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        JsonObject environmentType = data.get(0).getAsJsonObject();

        assertNotEquals(environmentType.get("id").getAsString(),null, "Missing environment type id");
        assertNotEquals(environmentType.get("name").getAsString(), null, "Missing environment type name");

        validEnvironmentTypeId = environmentType.get("id").getAsString();
        validEnvironmentTypeName = environmentType.get("name").getAsString();
    }

    @Test
    @Order(2)
    void getEnvironmentTypesSingleSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                                                              GET("/environment-data-types/"+validEnvironmentTypeId)
                                                              .contentType(MediaType.APPLICATION_JSON)
                                                              .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
                                                              );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(result.get("id").getAsString(), validEnvironmentTypeId, "Wrong environment type id");
        assertEquals(result.get("name").getAsString(), validEnvironmentTypeName, "Wrong environment type name");
    }

    @Test
    void getEnvironmentTypesSingleInvalid() {

        Flowable<HttpResponse<String>> call = client.exchange(
                                                              GET("/environment-data-types/"+invalidEnvironmentTypeId)
                                                              .contentType(MediaType.APPLICATION_JSON)
                                                              .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
                                                              );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
                HttpResponse<String> response = call.blockingFirst();
            });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

}
