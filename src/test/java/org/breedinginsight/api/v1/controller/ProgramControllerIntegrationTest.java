package org.breedinginsight.api.v1.controller;

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
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramControllerIntegrationTest {

    // TODO: change this
    // INSERT INTO program
    //(species_id, name, abbreviation, objective, documentation_url)
    //VALUES
    //('56849650-7f0e-46c3-bf9c-f86e814d4ecb', 'Nick Grapes', 'NG', 'Breed grapes', 'grapes.com');
    String validProgram = "458bd904-9746-4ec8-b048-28907925999c";
    String invalidProgram = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String validUser = "74a6ebfe-d114-419b-8bdc-2f7b52d26172";
    String validRole = "5077b080-ba7f-4402-b059-3dfb907d40a6";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    void setup() {

    }

    @AfterAll
    void cleanup() {

    }

    @Test
    public void postProgramsUsersInvalidProgram() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "test");
        requestBody.addProperty("email", "test@test.com");
        requestBody.addProperty("roleId", validRole);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+invalidProgram+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void postProgramsUsersDuplicateUser() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "test");
        requestBody.addProperty("email", "test@test.com");
        requestBody.addProperty("roleId", validRole);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    public void postProgramsUsersMissingBody() {
        JsonObject requestBody = new JsonObject();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postProgramsUsersOnlyName() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "test");

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postProgramsUsersOnlyIdSuccess() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("id", validUser);
        requestBody.addProperty("roleId", validRole);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

    }


}
