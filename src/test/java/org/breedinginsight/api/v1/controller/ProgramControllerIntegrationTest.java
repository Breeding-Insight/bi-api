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
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.breedinginsight.services.*;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
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
    User testUser;

    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    String invalidProgram = invalidUUID;
    String invalidUser = invalidUUID;
    String invalidRole = invalidUUID;
    String invalidSpecies = invalidUUID;

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
    ProgramUserService programUserService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    void setup() throws Exception {

        testUser = userService.getByOrcid(TestTokenValidator.TEST_USER_ORCID);
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
        try {
            programService.delete(validProgram.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete test program");
        }

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
            Program program = programService.create(programRequest, testUser);
            return program;
        } catch (UnprocessableEntityException e){
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
        JsonObject requestBody = validProgramUserRequest();

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
    void postProgramsUsersInvalidUserId() {
        JsonObject requestBody = invalidUserProgramUserRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users/", requestBody.toString()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    public void postProgramsUsersDuplicateUser() {
        JsonObject requestBody = new JsonObject();

        JsonObject user = new JsonObject();
        user.addProperty("name", "test");
        user.addProperty("email", "test@test.com");

        JsonArray roles = new JsonArray();
        JsonObject role = new JsonObject();
        role.addProperty("id", validRole.getId().toString());
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);
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
    public void postProgramsUsersDuplicateRoles() {
        String validProgramId = validProgram.getId().toString();
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", validUser.getId().toString());
        JsonObject role = new JsonObject();
        role.addProperty("id", validRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);

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
    public void postProgramsUsersInvalidRole() {
        String validProgramId = validProgram.getId().toString();
        JsonObject requestBody = invalidRoleProgramUserRequest();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void putProgramsUsersDuplicateRoles() {
        String validProgramId = validProgram.getId().toString();
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", validUser.getId().toString());
        JsonObject role = new JsonObject();
        role.addProperty("id", validRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);

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
    public void putProgramsUsersInvalidRole() {
        String validProgramId = validProgram.getId().toString();
        JsonObject requestBody = invalidRoleProgramUserRequest();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    public JsonObject validProgramUserRequest() {
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", validUser.getId().toString());
        JsonObject role = new JsonObject();
        role.addProperty("id", validRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);
        return requestBody;
    }

    public JsonObject invalidRoleProgramUserRequest() {
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", validUser.getId().toString());
        JsonObject role = new JsonObject();
        role.addProperty("id", invalidRole);
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);
        return requestBody;
    }

    public JsonObject invalidUserProgramUserRequest() {
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", invalidUser);
        JsonObject role = new JsonObject();
        role.addProperty("id", validRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);
        return requestBody;
    }

    @Test
    @Order(1)
    public void postProgramsUsersOnlyIdSuccess() {
        JsonObject requestBody = validProgramUserRequest();
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
    @Order(2)
    void getProgramsUsersSuccess() {
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), 1, "Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        JsonObject programUser = data.get(0).getAsJsonObject();
        JsonObject user = programUser.getAsJsonObject("user");
        assertEquals(user.get("id").getAsString(),validUser.getId().toString(), "Wrong user id");
        assertEquals(user.get("name").getAsString(),validUser.getName(), "Wrong name");
        assertEquals(user.get("email").getAsString(),validUser.getEmail(), "Wrong email");
        JsonArray roles = programUser.getAsJsonArray("roles");
        JsonObject role = roles.get(0).getAsJsonObject();
        assertEquals(role.get("id").getAsString(),validRole.getId().toString(), "Wrong role id");
        assertEquals(role.get("domain").getAsString(),validRole.getDomain(), "Wrong domain");
    }

    @Test
    @Order(3)
    void getProgramsUsersSingleSuccess() {
        String validProgramId = validProgram.getId().toString();
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users/"+validUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), 1, "Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonObject user = result.getAsJsonObject("user");
        assertEquals(user.get("id").getAsString(),validUser.getId().toString(), "Wrong user id");
        assertEquals(user.get("name").getAsString(),validUser.getName(), "Wrong name");
        assertEquals(user.get("email").getAsString(),validUser.getEmail(), "Wrong email");
        JsonArray roles = result.getAsJsonArray("roles");
        JsonObject role = roles.get(0).getAsJsonObject();
        assertEquals(role.get("id").getAsString(),validRole.getId().toString(), "Wrong role id");
        assertEquals(role.get("domain").getAsString(),validRole.getDomain(), "Wrong domain");
    }

    @Test
    @Order(4)
    public void putProgramsUsersOnlyIdSuccess() {
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", validUser.getId().toString());
        JsonObject role = new JsonObject();
        role.addProperty("id", validRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("user", user);
        requestBody.add("roles", roles);
        String validProgramId = validProgram.getId().toString();
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/users/"+validUserId, requestBody.toString())
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
    @Order(5)
    public void deleteProgramsUsersSuccess() {
        String validProgramId = validProgram.getId().toString();
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/" + validProgramId + "/users/" + validUserId).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    void getProgramsUsersInvalidProgramId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+invalidProgram+"/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void getProgramsUsersNoUsers() {
        String validProgramId = validProgram.getId().toString();
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), 0, "Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(result.size(),0, "Wrong size");

    }

    @Test
    void getProgramsUsersSingleInvalidProgramId() {
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+invalidProgram+"/users/"+validUserId).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void getProgramsUsersSingleInvalidUserId() {
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users/"+invalidUser).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putProgramsUsersInvalidProgramId() {
        JsonObject requestBody = validProgramUserRequest();
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+invalidProgram+"/users/"+validUserId, requestBody.toString()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putProgramsUsersInvalidUserId() {
        JsonObject requestBody = validProgramUserRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/users/"+invalidUser, requestBody.toString()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putProgramsUsersMissingBody() {
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/users/"+invalidUser, "").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
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

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
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
    @SneakyThrows
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

        programService.delete(program.getId());
    }

    @Test
    @SneakyThrows
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

        programService.delete(program.getId());

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

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
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

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .commonName(validSpecies.getCommonName())
                .build();

        Program alteredProgram = validProgram;
        alteredProgram.setName("changed");
        alteredProgram.setAbbreviation("changed abbreviation");
        alteredProgram.setObjective("changed objective");
        alteredProgram.setDocumentationUrl("changed doc url");

        ProgramRequest validRequest = ProgramRequest.builder()
                .name(alteredProgram.getName())
                .abbreviation(alteredProgram.getAbbreviation())
                .documentationUrl(alteredProgram.getDocumentationUrl())
                .objective(alteredProgram.getObjective())
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", alteredProgram.getId()), gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        checkMinimalValidProgram(alteredProgram, result);
    }

    @Test
    public void archiveProgramsInvalidId() {

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(String.format("/programs/archive/%s", invalidProgram))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());

        Program program = Assertions.assertDoesNotThrow(() -> {
            Program createdProgram = programService.getById(validProgram.getId());
            return createdProgram;
        });
        assertEquals(true, program.getActive(), "Inactive flag not set in database");
    }

    @Test
    @SneakyThrows
    public void archiveProgramsSuccess() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .build();

        ProgramRequest validRequest = ProgramRequest.builder()
                .name(validProgram.getName())
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> createCall = client.exchange(
                POST("/programs", gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = createCall.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String newProgramId = result.getAsJsonPrimitive("id").getAsString();

        Flowable<HttpResponse<String>> archiveCall = client.exchange(
                DELETE(String.format("/programs/archive/%s", newProgramId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> archiveResponse = archiveCall.blockingFirst();
        assertEquals(HttpStatus.OK, archiveResponse.getStatus());

        Program program = Assertions.assertDoesNotThrow(() -> {
            Program createdProgram = programService.getById(UUID.fromString(newProgramId));
            return createdProgram;
        });
        assertEquals(false, program.getActive(), "Inactive flag not set in database");

        programService.delete(UUID.fromString(newProgramId));
    }
    //endregion
}
