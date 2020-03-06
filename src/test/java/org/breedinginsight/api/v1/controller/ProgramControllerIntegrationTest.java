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
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.records.ProgramRecord;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.checkerframework.common.value.qual.IntRangeFromNonNegative;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.util.List;
import java.util.UUID;

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
    Program validProgram;
    User validUser;
    Species validSpecies;

    String invalidProgram = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String invalidUser = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String validRole = "6c6d1d9d-1f6d-47e4-8ac7-46ed1b78536e";


    @Inject
    UserService userService;
    @Inject
    ProgramService programService;
    @Inject
    SpeciesService speciesService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    void setup() throws Exception{
        // Get species for tests
        Species species = getTestSpecies();
        validSpecies = species;
        // Insert and get user for tests
        try {
            validUser = insertAndFetchTestUser();
        } catch (Exception e){
            throw new Exception(e.toString());
        }
        // Insert and get program for tests
        try {
            validProgram = insertAndFetchTestProgram();
        } catch (Exception e){
            throw new Exception(e.toString());
        }

    }

    @AfterAll
    void teardown() throws Exception{
        // TODO: Get a delete function for program
        try {
            programService.delete(validProgram.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete test program");
        }

        // TODO: Delete user
        try {
            userService.delete(validUser.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete test user");
        }
    }

    public Program insertAndFetchTestProgram() throws Exception{
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .build();
        ProgramRequest programRequest = ProgramRequest.builder()
                .name("Test Program")
                .abbreviation("test")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .build();
        try {
            Program program = programService.create(programRequest, validUser);
            return program;
        } catch (DoesNotExistException e){
            throw new Exception("Unable to create test program");
        }
    }

    public User insertAndFetchTestUser() throws Exception{
        UserRequest userRequest = UserRequest.builder()
                .name("Test User")
                .email("test1@test.com")
                .build();
        try {
            User user = userService.create(userRequest);
            return user;
        } catch (AlreadyExistsException e) {
            throw new Exception("Failed to insert test user" + e.toString());
        }

    }

    public Species getTestSpecies() {
        List<Species> species = speciesService.getAll();
        return species.get(0);
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
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users", requestBody.toString())
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
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users", requestBody.toString())
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
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users", requestBody.toString())
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
        requestBody.addProperty("id", validUser.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(validRole);
        requestBody.add("roleIds", roles);
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users", requestBody.toString())
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
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgramId+"/users/"+invalidUser).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void deleteProgramsUsersSuccess() {
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgramId+"/users/"+validUser).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
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
        species.addProperty("id",validSpecies.getId().toString());
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

    //region Program Tests
    @Test
    public void postProgramsInvalidSpecies() {

    }

    @Test
    public void postProgramsMissingBody() {

    }

    @Test
    public void postProgramsMissingSpecies() {

    }

    @Test
    public void postProgramsMinimalBodySuccess() {

    }

    @Test
    public void postProgramsFullBodySuccess(){

    }

    @Test
    public void putProgramsInvalidSpecies() {

    }

    @Test
    public void putProgramsMissingSpecies() {

    }

    @Test
    public void putProgramsInvalidId() {

    }

    @Test
    public void putProgramsMissingName() {

    }

    @Test
    public void putProgramsMinimalBodySuccess() {

    }

    @Test
    public void putProgramsFullBodySuccess() {

    }

    @Test
    public void archiveProgramsInvalidId() {

    }

    @Test
    public void archiveProgramsSuccess() {

    }
    //endregion
}
