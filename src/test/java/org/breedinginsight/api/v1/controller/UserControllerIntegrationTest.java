package org.breedinginsight.api.v1.controller;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.*;
import io.micronaut.http.*;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.services.RoleService;
import org.breedinginsight.services.SystemRoleService;
import org.breedinginsight.services.UserService;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import javax.inject.Inject;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * Integration tests of UserController endpoints using test database and mocked Micronaut authentication
 */

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserControllerIntegrationTest {

    private String testUserUUID;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    Gson gson = new Gson();

    @Inject
    UserDAO userDAO;
    @Inject
    UserService userService;
    @Inject
    SystemRoleService systemRoleService;

    private User testUser;
    private User otherTestUser;
    private UUID validSystemRoleId;
    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @BeforeAll
    void setup() throws Exception {

        Optional<User> optionalUser = userService.getByOrcid(TestTokenValidator.TEST_USER_ORCID);
        testUser = optionalUser.get();
        testUser = userService.getByOrcid(TestTokenValidator.TEST_USER_ORCID);
        otherTestUser = userService.getByOrcid(TestTokenValidator.OTHER_TEST_USER_ORCID);
        validSystemRoleId = systemRoleService.getAll().get(0).getId();
    }

    @Test
    public void getUserInfoRegisteredUser() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/userinfo").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals("Test User", result.get("name").getAsString(), "Wrong name");

        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");
    }

    @Test
    public void getUserInfoUnregisteredUser() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/userinfo").cookie(new NettyCookie("phylo-token", "test-unregistered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }

    @Test
    @Order(1)
    public void getUsersAllExisting() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonArray data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("data");

        // TODO: depends on db setup
        assertTrue(data.size() >= 1, "Wrong number users");

        JsonObject exampleUser = (JsonObject) data.get(0);
        JsonArray resultRoles = (JsonArray) exampleUser.get("systemRoles");
        assertEquals(true, resultRoles != null, "Roles list was not returned.");
    }

    @Test
    public void getUsersExistingId() {

        // TODO: depends on db setup
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/" + testUser.getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(testUser.getId().toString(), result.get("id").getAsString(), "Wrong id");
        assertEquals("Test User", result.get("name").getAsString(), "Wrong name");
        assertEquals("test@test.com", result.get("email").getAsString(), "Wrong email");
        assertEquals("test@test.com", result.get("email").getAsString(), "Wrong email");

        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");
    }

    @Test
    public void getUsersNotExistingId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/e91f816b-b7aa-4444-9d00-3e25fdc00e15").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(2)
    public void postUsersNoRolesSuccess() {
        String name = "Test User2";
        String email = "test2@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        try {
            JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

            assertEquals(name, result.get("name").getAsString(), "Wrong name");
            assertEquals(email, result.get("email").getAsString(), "Wrong email");

            JsonArray resultRoles = (JsonArray) result.get("systemRoles");
            assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
            assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");

            testUserUUID = result.get("id").getAsString();

        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @SneakyThrows
    void postUsersWithRolesSuccess() {
        String name = "Test User3";
        String email = "test3@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRoleId.toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        try {
            JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

            assertEquals(name, result.get("name").getAsString(), "Wrong name");
            assertEquals(email, result.get("email").getAsString(), "Wrong email");

            JsonArray resultRoles = (JsonArray) result.get("systemRoles");
            assertEquals(true, resultRoles.size() == 1, "Wrong number of roles attached to user");
            JsonObject adminRole = (JsonObject) resultRoles.get(0);
            assertEquals(validSystemRoleId.toString(), adminRole.get("id").getAsString(), "Inserted role id doesn't match what was passed.");

        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @SneakyThrows
    void postUsersRolesNotFound() {

        String name = "Test User4";
        String email = "test4@test.com";
        String orcid = "0000-0000-0000-0000";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("orcid", orcid);
        JsonObject role = new JsonObject();
        role.addProperty("id", invalidUUID);
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());

    }

    @Test
    @Order(3)
    public void putUsersEmailAlreadyExists() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Test User2");
        requestBody.addProperty("email", "test@test.com");

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUserUUID, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    @Order(4)
    public void putUsersOwnEmailAlreadyExists() {
        String name = "Test Update 5";
        String email = "testedited@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUserUUID, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        try {
            JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

            assertEquals(name, result.get("name").getAsString(), "Wrong name");
            assertEquals(email, result.get("email").getAsString(), "Wrong email");

            JsonArray resultRoles = (JsonArray) result.get("systemRoles");
            assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
            assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");


        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @Order(5)
    public void putUsersEmptyBody() {

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUserUUID, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    @Order(6)
    public void putUsersEmptyName() {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", "emptyname@test.com");

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUserUUID, requestBody.toString())
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
    void putUsersRolesNotExist() {

        String name = "Test User4";
        String email = "test4@test.com";
        String orcid = "0000-0000-0000-0000";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("orcid", orcid);
        JsonObject role = new JsonObject();
        role.addProperty("id", invalidUUID);
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUserUUID, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "other-test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());

    }

    @Test
    @SneakyThrows
    @Order(7)
    void putUsersSuccessOwnRoles() {
        // Roles should not change
        String name = "Test User4";
        String email = "test4@test.com";
        String orcid = "0000-0000-0000-0000";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("orcid", orcid);
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRoleId.toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "other-test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(name, result.get("name").getAsString(), "Wrong name");
        assertEquals(email, result.get("email").getAsString(), "Wrong email");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");
    }

    @Test
    @SneakyThrows
    @Order(8)
    void putUsersSuccessOtherRolesSuccess() {
        // Roles should change
        String name = "Test User5";
        String email = "test5@test.com";
        String orcid = "0000-0000-0000-0000";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("orcid", orcid);
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRoleId.toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(name, result.get("name").getAsString(), "Wrong name");
        assertEquals(email, result.get("email").getAsString(), "Wrong email");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 1, "Roles list was not empty.");
        JsonObject resultRole = (JsonObject) resultRoles.get(0);
        assertEquals(validSystemRoleId.toString(), resultRole.get("id").getAsString(), "Wrong role id was returned.");
    }

    @Test
    @Order(9)
    public void putUsersNoRolesSuccess() {
        // Will not modify roles
        String name = "Test Update";
        String email = "testedited@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUserUUID, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        try {
            JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

            assertEquals(name, result.get("name").getAsString(), "Wrong name");
            assertEquals(email, result.get("email").getAsString(), "Wrong email");

            JsonArray resultRoles = (JsonArray) result.get("systemRoles");
            assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
            assertEquals(true, resultRoles.size() == 1, "Wrong number of roles was returned.");


        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @SneakyThrows
    @Order(10)
    void putUsersSuccessOtherRolesEmptyRoles() {
        // Roles should be deleted
        String name = "Test User5";
        String email = "test5@test.com";
        String orcid = "0000-0000-0000-0000";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("orcid", orcid);
        JsonArray roles = new JsonArray();
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(name, result.get("name").getAsString(), "Wrong name");
        assertEquals(email, result.get("email").getAsString(), "Wrong email");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");
    }

    @Test
    @SneakyThrows
    @Order(12)
    void putUsersSuccessOtherRolesDuplicateRoles() {
        // Roles should be unique set
        String name = "Test User5";
        String email = "test5@test.com";
        String orcid = "0000-0000-0000-0000";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        requestBody.addProperty("orcid", orcid);
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRoleId.toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(name, result.get("name").getAsString(), "Wrong name");
        assertEquals(email, result.get("email").getAsString(), "Wrong email");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 1, "More than one role was returned.");
    }

    @Test
    @Order(13)
    public void deleteUsersExisting() {

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/users/"+testUserUUID).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

    }

    @Test
    public void putUsersNotExistingId() {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Test User2");
        requestBody.addProperty("email", "test@test.com");

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/e91f816b-b7aa-4444-9d00-3e25fdc00e15", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void deleteUsersNotExistingId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/users/e91f816b-b7aa-4e89-9d00-3e25fdc00e14").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void postUsersMissingEmail() {
        String name = "Test User2";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postUsersMissingName() {
        String email = "test@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", requestBody.toString())
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postUsersEmptyBody() {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", "")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    public void postUsersEmailAlreadyExists() {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Test User");
        requestBody.addProperty("email", "test@test.com");

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/users", requestBody.toString())
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }


}
