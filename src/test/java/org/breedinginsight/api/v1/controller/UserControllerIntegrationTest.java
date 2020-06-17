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

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.*;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.*;
import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.daos.SystemRoleDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemRoleEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.*;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.DSLContext;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import javax.inject.Inject;
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
    List<ProgramEntity> validPrograms;
    RoleEntity validRole;
    AuthenticatedUser actingUser;
    Integer numUsers;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @Inject
    private DSLContext dsl;
    @Inject
    BiUserDao biUserDao;
    @Inject
    SystemRoleDao systemRoleDao;
    @Inject
    RoleDao roleDao;
    @Inject
    ProgramDao programDao;

    private BiUserEntity testUser;
    private BiUserEntity otherTestUser;
    private SystemRoleEntity validSystemRole;
    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @BeforeAll
    void setup() throws Exception {

        // Insert our traits into the db
        var fp = FannyPack.fill("src/test/resources/sql/UserControllerIntegrationTest.sql");
        dsl.execute(fp.get("InsertProgram"));
        dsl.execute(fp.get("InsertUserProgramAssociations"));

        testUser = biUserDao.fetchByOrcid(TestTokenValidator.TEST_USER_ORCID).get(0);
        otherTestUser = biUserDao.fetchByOrcid(TestTokenValidator.OTHER_TEST_USER_ORCID).get(0);
        validSystemRole = systemRoleDao.findAll().get(0);
        validRole = roleDao.findAll().get(0);
        validPrograms = programDao.findAll();
        actingUser = getActingUser();

    }

    public AuthenticatedUser getActingUser() {
        UUID id = testUser.getId();
        List<String> systemRoles = new ArrayList<>();
        systemRoles.add(validRole.getDomain());
        return new AuthenticatedUser("test_user", systemRoles, id);
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
        assertEquals(testUser.getOrcid(), result.get("orcid").getAsString(), "Wrong orcid");
        assertEquals("test@test.com", result.get("email").getAsString(), "Wrong email");

        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");

        JsonArray resultPrograms = (JsonArray) result.get("activePrograms");
        assertEquals(true, resultPrograms != null, "Empty programs list was not returned.");
        assertEquals(true, resultPrograms.size() == 2, "Wrong number of programs.");
        
        Boolean programOneSeen = false;
        Boolean programTwoSeen = false;
        for (JsonElement resultProgram: resultPrograms) {
            JsonObject jsonProgram = resultProgram.getAsJsonObject();
            ProgramEntity programEntity;

            if (jsonProgram.get("id").getAsString().equals(validPrograms.get(0).getId().toString())){
                programOneSeen = true;
                programEntity = validPrograms.get(0);
            } else if (jsonProgram.get("id").getAsString().equals(validPrograms.get(1).getId().toString())) {
                programTwoSeen = true;
                programEntity = validPrograms.get(1);
            } else {
                throw new AssertionFailedError("Program does not match any programs in database");
            }

            assertEquals(programEntity.getName(), jsonProgram.get("name").getAsString(), "Program names do not match");
            assertEquals(programEntity.getActive(), jsonProgram.get("active").getAsBoolean(), "Program active fields do not match");
        }

        if (!programOneSeen || !programTwoSeen) {
            throw new AssertionFailedError("Both programs were not returned");
        }
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

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

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

    @Test
    @SneakyThrows
    @Order(1)
    void postUsersWithRolesSuccess() {
        String name = "Test User3";
        String email = "test3@test.com";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("email", email);
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRole.getId().toString());
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
            assertEquals(validSystemRole.getId().toString(), adminRole.get("id").getAsString(), "Inserted role id doesn't match what was passed.");

        } catch (IllegalStateException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    @Order(1)
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
    @Order(2)
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

        JsonArray resultPrograms = (JsonArray) exampleUser.get("activePrograms");
        assertEquals(true, resultPrograms != null, "Empty programs list was not returned.");

        numUsers = data.size();
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
    @Order(3)
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
    @Order(3)
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
    @Order(3)
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

        JsonObject requestBody = new JsonObject();
        JsonObject role = new JsonObject();
        role.addProperty("id", invalidUUID);
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString() + "/roles", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

    }

    @Test
    @SneakyThrows
    @Order(3)
    void putUserRolesOwnRoles() {

        JsonObject requestBody = new JsonObject();
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + testUser.getId().toString() + "/roles", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus(), "Unauthorized exception not returned.");
    }

    @Test
    @SneakyThrows
    @Order(3)
    void putUserRolesOtherUserSuccess() {

        JsonObject requestBody = new JsonObject();
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString() + "/roles", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 1, "Roles list has wrong number of entries.");
        JsonObject resultRole = (JsonObject) resultRoles.get(0);
        assertEquals(validSystemRole.getId().toString(), resultRole.get("id").getAsString(), "Wrong role id was returned.");
    }

    @Test
    @Order(4)
    public void getUserInfoRegisteredUser() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/userinfo").cookie(new NettyCookie("phylo-token", "other-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(otherTestUser.getName(), result.get("name").getAsString(), "Wrong name");
        assertEquals(otherTestUser.getOrcid(), result.get("orcid").getAsString(), "Wrong orcid");
        assertEquals(otherTestUser.getEmail(), result.get("email").getAsString(), "Wrong email");
        assertEquals(otherTestUser.getId().toString(), result.get("id").getAsString(), "Wrong id");

        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 1, "One role was not returned in response");
        JsonObject role = (JsonObject) resultRoles.get(0);
        assertEquals(validSystemRole.getId().toString(), role.get("id").getAsString(), "Role id was incorrect");
        assertEquals(validSystemRole.getDomain(), role.get("domain").getAsString(), "Role domain was incorrect");

        JsonArray resultPrograms = (JsonArray) result.get("activePrograms");
        assertEquals(true, resultPrograms != null, "Empty roles list was not returned.");
        assertEquals(2, resultPrograms.size(), "Wrong number of programs.");

        Boolean programOneSeen = false;
        Boolean programTwoSeen = false;
        for (JsonElement resultProgram: resultPrograms) {
            JsonObject jsonProgram = resultProgram.getAsJsonObject();
            ProgramEntity programEntity;

            if (jsonProgram.get("id").getAsString().equals(validPrograms.get(0).getId().toString())){
                programOneSeen = true;
                programEntity = validPrograms.get(0);
            } else if (jsonProgram.get("id").getAsString().equals(validPrograms.get(1).getId().toString())) {
                programTwoSeen = true;
                programEntity = validPrograms.get(1);
            } else {
                throw new AssertionFailedError("Program does not match any programs in database");
            }

            assertEquals(programEntity.getName(), jsonProgram.get("name").getAsString(), "Program names do not match");
            assertEquals(programEntity.getActive(), jsonProgram.get("active").getAsBoolean(), "Program active fields do not match");
        }

        if (!programOneSeen || !programTwoSeen) {
            throw new AssertionFailedError("Both programs were not returned");
        }
    }

    @Test
    @Order(4)
    public void putUserRolesNullSuccess() {

        JsonObject requestBody = new JsonObject();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString() + "/roles", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Unauthorized exception not returned.");
    }

    @Test
    @SneakyThrows
    @Order(4)
    void putUserRolesDuplicateRoles() {

        JsonObject requestBody = new JsonObject();
        JsonObject role = new JsonObject();
        role.addProperty("id", validSystemRole.getId().toString());
        JsonArray roles = new JsonArray();
        roles.add(role);
        roles.add(role);
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString() + "/roles", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 1, "Roles list did not have 1 entry in it.");
    }

    @Test
    @SneakyThrows
    @Order(4)
    void putUsersRolesEmptyRoles() {

        JsonObject requestBody = new JsonObject();
        JsonArray roles = new JsonArray();
        requestBody.add("systemRoles", roles);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/users/" + otherTestUser.getId().toString() + "/roles", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );


        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        // System roles should not be modified
        JsonArray resultRoles = (JsonArray) result.get("systemRoles");
        assertEquals(true, resultRoles != null, "Empty roles list was not returned.");
        assertEquals(true, resultRoles.size() == 0, "Roles list was not empty.");
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
    @SneakyThrows
    @Order(4)
    public void archiveUserActiveInProgram() {

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/users/" + testUserUUID).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }


    @Test
    @Order(4)
    public void archiveUsersNoActivePrograms() {

        // Add test user to that program

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/users/"+testUserUUID).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    public void archiveUsersNotExistingId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/users/e91f816b-b7aa-4e89-9d00-3e25fdc00e14").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(5)
    public void getUsersInactiveNotReturned() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonArray data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("data");

        // TODO: depends on db setup
        assertTrue(data.size() == numUsers - 1, "Wrong number users");

        for (JsonElement jsonUser : data.getAsJsonArray()){
            JsonObject exampleUser = (JsonObject) jsonUser;
            // Work around for the system user
            if (exampleUser.get("id") != null){
                assertNotEquals(testUserUUID, exampleUser.get("id").toString(), "Inactive user was returned");
            }
        }
    }

}
