package org.breedinginsight.api.v1.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.annotation.Nonnull;
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
public class MetadataFilterIntegrationTest {

    @Inject
    UserController userController;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @MockBean(UserController.class)
    UserController userController() {
        return mock(UserController.class);
    }

    @Test
    public void getSingleResponseNoMetadataSuccess() {

        // Check metadata is successfully populated when none is returned from controller
        User mockedUser = new User();
        mockedUser.setName("Test User")
                .setEmail("test@user.com")
                .setId(UUID.randomUUID())
                .setOrcid("testorcid");

        when(userController.users(any(UUID.class))).thenReturn(HttpResponse.ok(new Response<>(mockedUser)));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/74a6ebfe-d114-419b-8bdc-2f7b52d26172").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Metadata was not populated");
    }

    @Test
    public void getSingleResponseMetadataSuccess() {

        // Check that if a single result response has metadata, an error is not thrown
        User mockedUser = new User();
        mockedUser.setName("Test User")
                .setEmail("test@user.com")
                .setId(UUID.randomUUID())
                .setOrcid("testorcid");
        Pagination mockedPagination = new Pagination(1,1,1,0);
        Metadata mockedMetadata = new Metadata();
        mockedMetadata.setPagination(mockedPagination);

        when(userController.users(any(UUID.class))).thenReturn(HttpResponse.ok(new Response<>(mockedMetadata, mockedUser)));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/74a6ebfe-d114-419b-8bdc-2f7b52d26172").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        // TODO: depends on db setup
        assertTrue(data != null, "Wrong number users");
    }

    @Test
    public void getDataResponseMetadataFilterFailure() {

        // Check we get an exception when a data response is returned without a metadata field
        User mockedUser = new User();
        mockedUser.setName("Test User")
                .setEmail("test@user.com")
                .setId(UUID.randomUUID())
                .setOrcid("testorcid");
        List<User> users = new ArrayList<>();
        users.add(mockedUser);
        users.add(mockedUser);
        DataResponse<User> dataResponse = new DataResponse<User>().setData(users);
        Response mockedResponse = new Response().setResult(dataResponse);

        when(userController.users()).thenReturn(HttpResponse.ok(mockedResponse));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());

    }

    @Test
    public void getDataResponseMetadataFilterSuccess() {

        // Check we don't get an exception when a data response with proper metadata is returned
        User mockedUser = new User();
        mockedUser.setName("Test User")
                .setEmail("test@user.com")
                .setId(UUID.randomUUID())
                .setOrcid("testorcid");
        List<User> users = new ArrayList<>();
        users.add(mockedUser);
        users.add(mockedUser);

        DataResponse<User> dataResponse = new DataResponse<User>().setData(users);
        Response mockedResponse = new Response().setResult(dataResponse);

        Pagination mockedPagination = new Pagination(1,1,1,0);
        Metadata mockedMetadata = new Metadata();
        mockedMetadata.setPagination(mockedPagination);
        mockedResponse.setMetadata(mockedMetadata);

        when(userController.users()).thenReturn(HttpResponse.ok(mockedResponse));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Metadata was not populated");

    }


}
