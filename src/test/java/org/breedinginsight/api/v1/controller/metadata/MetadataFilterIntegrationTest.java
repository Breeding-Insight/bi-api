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

package org.breedinginsight.api.v1.controller.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.v1.controller.UserController;
import org.breedinginsight.model.User;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataFilterIntegrationTest extends DatabaseTest {

    @Inject
    private UserController userController;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    // TODO: Remove this
    @MockBean(UserController.class)
    UserController userController() {
        return mock(UserController.class);
    }

    @AfterAll
    public void finish() {
        super.stopContainers();
    }

    @Test
    public void getSingleResponseNoMetadataSuccess() {
        // TODO: Find an endpoint for this

        // Check metadata is successfully populated when none is returned from controller
        Response mockedResponse = getResponseMock();

        when(userController.users(any(UUID.class))).thenReturn(HttpResponse.ok(mockedResponse));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/74a6ebfe-d114-419b-8bdc-2f7b52d26172").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Metadata was not populated");

        // Check our default page numbers are still set correctly
        assertEquals(1, data.getAsJsonPrimitive("totalPages").getAsInt(), "Default total pages is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("totalCount").getAsInt(), "Default total count is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("pageSize").getAsInt(), "Default page size is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("currentPage").getAsInt(), "Default current page is incorrect");
    }

    @Test
    public void getSingleResponseMetadataSuccess() {
        // TODO: Find an endpoint for this

        // Check that if a single result response has metadata, an error is not thrown
        Response mockedResponse = getResponseMock();

        Pagination mockedPagination = new Pagination(6,6,6,1);
        Metadata mockedMetadata = new Metadata();
        mockedMetadata.setPagination(mockedPagination);
        mockedResponse.setMetadata(mockedMetadata);

        when(userController.users(any(UUID.class))).thenReturn(HttpResponse.ok(mockedResponse));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/74a6ebfe-d114-419b-8bdc-2f7b52d26172").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Wrong number users");

        // Check our page numbers weren't altered by the filtered
        assertEquals(6, data.getAsJsonPrimitive("totalPages").getAsInt(), "Default total pages is incorrect");
        assertEquals(6, data.getAsJsonPrimitive("totalCount").getAsInt(), "Default total count is incorrect");
        assertEquals(6, data.getAsJsonPrimitive("pageSize").getAsInt(), "Default page size is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("currentPage").getAsInt(), "Default current page is incorrect");
    }

    @Test
    public void dataResponseMetadataFilterFailure() {
        // TODO: Not sure how to test this one

        // Check we get an exception when a data response is returned without a metadata field and filter annotation on
        Response mockedResponse = getDataResponseMock();

        when(userController.users(any(UUID.class))).thenReturn(HttpResponse.ok(mockedResponse));

        // This endpoint will never return a data response, but for testing purposes, we will have it return one to test
        // if an endpoint with the @FilterMetadata annotation ever did return a data response.
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/74a6ebfe-d114-419b-8bdc-2f7b52d26172").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());

    }

    @Test
    public void getDataResponseMetadataFilterSuccess() {
        // TODO: Just test on real users endpoint

        // Check we don't get an exception when a data response with proper metadata is returned
        Response mockedResponse = getDataResponseMock();

        Pagination mockedPagination = new Pagination(6,6,6,1);
        Metadata mockedMetadata = new Metadata();
        mockedMetadata.setPagination(mockedPagination);
        mockedResponse.setMetadata(mockedMetadata);

        when(userController.users(any(QueryParams.class))).thenReturn(HttpResponse.ok(mockedResponse));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Metadata was not populated");

        // Check our page numbers weren't altered by the filtered
        assertEquals(6, data.getAsJsonPrimitive("totalPages").getAsInt(), "Default total pages is incorrect");
        assertEquals(6, data.getAsJsonPrimitive("totalCount").getAsInt(), "Default total count is incorrect");
        assertEquals(6, data.getAsJsonPrimitive("pageSize").getAsInt(), "Default page size is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("currentPage").getAsInt(), "Default current page is incorrect");
    }

    @Test
    public void filterNotCalledNoAnnotation(){
        //TODO: Looks like mocking the bean is messing with ROUTE_MATCH. Figure out something else for these endpoints. Maybe try out health endpoint?

        // Check that the metadata filter does not add metadata when annotation is missing
        Response mockedResponse = getResponseMock();

        when(userController.users(any(QueryParams.class))).thenReturn(HttpResponse.ok(mockedResponse));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/health").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject metadata = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");

        assertTrue(metadata == null, "Metadata was populated, but shouldn't have been");

    }

    public Response<User> getResponseMock() {

        User mockedUser = User.builder()
            .name("Test User")
            .email("test@user.com")
            .id(UUID.randomUUID())
            .orcid("testorcid")
            .build();

        return new Response<>(mockedUser);
    }

    public Response<DataResponse<User>> getDataResponseMock() {

        User mockedUser = User.builder()
                .name("Test User")
                .email("test@user.com")
                .id(UUID.randomUUID())
                .orcid("testorcid")
                .build();
        List<User> users = new ArrayList<>();
        users.add(mockedUser);
        users.add(mockedUser);
        DataResponse<User> dataResponse = new DataResponse<User>().setData(users);
        Response mockedResponse = new Response().setResult(dataResponse);

        return mockedResponse;
    }
}
