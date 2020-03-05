package org.breedinginsight.api.v1.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

import static io.micronaut.http.HttpRequest.*;
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
    String validProgram = "dfa47289-da43-471c-a944-77965b7af076";
    String invalidProgram = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String validUser = "bea6e697-0e14-44f1-96e8-523965bb33c8";
    String invalidUser = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String validRole = "6c6d1d9d-1f6d-47e4-8ac7-46ed1b78536e";
    String validSpecies = "6f4b7238-af95-4178-bae2-89bbfbb2e3b5";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    void setup() {

    }

    @AfterAll
    void teardown() {

    }

    //region Program User Tests
    @Test
    public void postProgramsUsersInvalidProgram() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "test");
        requestBody.addProperty("email", "test@test.com");
        requestBody.addProperty("roleIds", validRole);

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
        JsonArray roles = new JsonArray();
        roles.add(validRole);
        requestBody.add("roleIds", roles);

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
        JsonArray roles = new JsonArray();
        roles.add(validRole);
        requestBody.add("roleIds", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    public void deleteProgramsUsersNotExistingProgramId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+invalidProgram+"/users/"+invalidProgram).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void deleteProgramsUsersNotExistingUserId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgram+"/users/"+invalidUser).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void deleteProgramsUsersSuccess() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgram+"/users/"+validUser).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    public void createProgramSuccess() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Nick's Program");
        requestBody.addProperty("abbreviation", "NP");
        requestBody.addProperty("objective", "To breed stuff");
        requestBody.addProperty("documentationUrl", "www.nick.com");

        JsonObject species = new JsonObject();
        species.addProperty("id",validSpecies);
        species.addProperty("commonName", "Grape");
        requestBody.add("species", species);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    //endregion


}
