package org.breedinginsight.api.v1.controller;

import com.google.gson.Gson;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Flowable;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.dao.db.tables.records.ProgramRecord;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.RoleService;
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
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramControllerIntegrationTest {

    Program validProgram;
    User validUser;
    Species validSpecies;
    Role validRole;

    String invalidProgram = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String invalidUser = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String invalidRole = "3ea369b8-138b-44d6-aeab-a3c25a17d556";
    String invalidSpecies = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    Gson gson = new Gson();

    @Inject
    UserService userService;
    @Inject
    ProgramService programService;
    @Inject
    SpeciesService speciesService;
    @Inject
    RoleService roleService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    void setup() throws Exception{
        // Get species for tests
        Species species = getTestSpecies();
        validSpecies = species;
        // Get role for tests
        Role role = getTestRole();
        validRole = role;

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

    public Role getTestRole() {
        List<Role> roles = roleService.getAll();
        return roles.get(0);
    }

    //region Program User Tests
    @Test
    public void postProgramsUsersInvalidProgram() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "test");
        requestBody.addProperty("email", "test@test.com");
        requestBody.addProperty("roleIds", validRole.getId().toString());

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
        roles.add(validRole.getId().toString());
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
    @Order(1)
    public void postProgramsUsersOnlyIdSuccess() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("id", validUser.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(validRole.getId().toString());
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
    @Order(2)
    public void deleteProgramsUsersSuccess() {
        String validProgramId = validProgram.getId().toString();
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/" + validProgramId + "/users/" + validUserId).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    //endregion

    public void checkValidProgram(Program program, JsonObject programJson){

        assertEquals(program.getName(), programJson.get("name").getAsString(), "Wrong name");
        assertEquals(program.getAbbreviation(), programJson.get("abbreviation").getAsString(), "Wrong abbreviation");
        assertEquals(program.getDocumentationUrl(), programJson.get("documentationUrl").getAsString(), "Wrong documentation url");
        assertEquals(program.getObjective(), programJson.get("objective").getAsString(), "Wrong objective");

        JsonObject species = programJson.getAsJsonObject("species");
        assertEquals(program.getSpecies().getId().toString(), species.get("id").getAsString(), "Wrong species");

        JsonObject createdByUser = programJson.getAsJsonObject("createdByUser");
        assertEquals(program.getCreatedByUser().getId().toString(), createdByUser.get("id").getAsString(), "Wrong created by user");

        JsonObject updatedByUser = programJson.getAsJsonObject("updatedByUser");
        assertEquals(program.getUpdatedByUser().getId().toString(), updatedByUser.get("id").getAsString(), "Wrong updated by user");
    }

    public void checkMinimalValidProgram(Program program, JsonObject programJson){
        assertEquals(program.getName(), programJson.get("name").getAsString(), "Wrong name");

        JsonObject species = programJson.getAsJsonObject("species");
        assertEquals(program.getSpecies().getId().toString(), species.get("id").getAsString(), "Wrong species");

        JsonObject createdByUser = programJson.getAsJsonObject("createdByUser");
        assertEquals(program.getCreatedByUser().getId().toString(), createdByUser.get("id").getAsString(), "Wrong created by user");

        JsonObject updatedByUser = programJson.getAsJsonObject("updatedByUser");
        assertEquals(program.getUpdatedByUser().getId().toString(), updatedByUser.get("id").getAsString(), "Wrong updated by user");
    }

    //region Program Tests
    @Test
    public void getProgramsSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertTrue(result.size() >= 1, "Wrong number of programs");

        JsonArray data = result.getAsJsonArray("data");
        JsonObject programResult = data.get(0).getAsJsonObject();

        checkValidProgram(validProgram, programResult);
    }

    @Test
    public void getProgramsSpecificInvalidId() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s", invalidProgram)).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void getProgramsSpecificSuccess(){

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s", validProgram.getId().toString()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidProgram(validProgram, result);
    }

    @Test
    public void postProgramsInvalidSpecies() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(UUID.fromString(invalidSpecies))
                .build();

        ProgramRequest invalidProgramRequest = ProgramRequest.builder()
                .name("Test program")
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", gson.toJson(invalidProgramRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void postProgramsMissingBody() {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postProgramsMissingSpecies() {

        ProgramRequest invalidProgramRequest = ProgramRequest.builder()
                .name("Test program")
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", gson.toJson(invalidProgramRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postProgramsMinimalBodySuccess() throws Exception{

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .build();

        ProgramRequest validRequest = ProgramRequest.builder()
                .name(validProgram.getName())
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String newProgramId = result.getAsJsonPrimitive("id").getAsString();

        Program program = Assertions.assertDoesNotThrow(() -> {
            Program createdProgram = programService.getById(UUID.fromString(newProgramId));
            return createdProgram;
        });

        checkMinimalValidProgram(program, result);

        try {
            programService.delete(program.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete program after test");
        }

    }

    @Test
    public void postProgramsFullBodySuccess() throws Exception {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .commonName(validSpecies.getCommonName())
                .build();

        ProgramRequest validRequest = ProgramRequest.builder()
                .name(validProgram.getName())
                .abbreviation("Test")
                .documentationUrl("localhost")
                .objective("Testing things")
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String newProgramId = result.getAsJsonPrimitive("id").getAsString();

        Program program = Assertions.assertDoesNotThrow(() -> {
            Program createdProgram = programService.getById(UUID.fromString(newProgramId));
            return createdProgram;
        });

        checkMinimalValidProgram(program, result);

        try {
            programService.delete(program.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete program after test");
        }
    }

    @Test
    public void putProgramsInvalidSpecies() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(UUID.fromString(invalidSpecies))
                .build();

        ProgramRequest invalidProgramRequest = ProgramRequest.builder()
                .name("Test program")
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", validProgram.getId()), gson.toJson(invalidProgramRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void putProgramsMissingSpecies() {

        ProgramRequest invalidProgramRequest = ProgramRequest.builder()
                .name("Test program")
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", validProgram.getId()), gson.toJson(invalidProgramRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void putProgramsInvalidId() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(UUID.fromString(invalidSpecies))
                .build();

        ProgramRequest invalidProgramRequest = ProgramRequest.builder()
                .name("Test program")
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", invalidProgram), gson.toJson(invalidProgramRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void putProgramsMissingName() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(UUID.fromString(invalidSpecies))
                .build();

        ProgramRequest invalidProgramRequest = ProgramRequest.builder()
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", validProgram.getId()), gson.toJson(invalidProgramRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void putProgramsMinimalBodySuccess() throws Exception {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .build();

        Program alteredProgram = validProgram;
        alteredProgram.setName("changed");
        ProgramRequest validRequest = ProgramRequest.builder()
                .name(alteredProgram.getName())
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", validProgram.getId()) , gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        checkMinimalValidProgram(alteredProgram, result);

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
