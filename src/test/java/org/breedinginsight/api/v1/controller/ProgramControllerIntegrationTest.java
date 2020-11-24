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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.*;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.ProgramUserDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.*;
import org.breedinginsight.utilities.email.EmailUtil;
import org.geojson.Feature;
import org.geojson.Point;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import javax.inject.Named;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramControllerIntegrationTest extends BrAPITest {

    private FannyPack fp;
    private FannyPack brapiFp;
    private FannyPack securityFp;

    private ProgramEntity validProgram;
    private ProgramEntity otherProgram;
    private User validUser;
    private Species validSpecies;
    private Role validRole;

    private ProgramLocation validLocation;
    private Country validCountry;
    private EnvironmentType validEnvironment;
    private Accessibility validAccessibility;
    private Topography validTopography;

    private User testUser;
    private User otherUser;
    private AuthenticatedUser actingUser;

    private String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private String invalidProgram = invalidUUID;
    private String invalidUser = invalidUUID;
    private String invalidRole = invalidUUID;
    private String invalidSpecies = invalidUUID;
    private String invalidLocation= invalidUUID;
    private String invalidCountry = invalidUUID;
    private String invalidEnvironment = invalidUUID;
    private String invalidAccessibility = invalidUUID;
    private String invalidTopography = invalidUUID;

    private Gson gson = new Gson();
    private ObjectMapper objMapper = new ObjectMapper();
    private ListAppender<ILoggingEvent> loggingEventListAppender;

    @Inject
    private UserService userService;
    @Inject
    private ProgramService programService;
    @Inject
    private SpeciesService speciesService;
    @Inject
    private RoleService roleService;
    @Inject
    private ProgramUserService programUserService;
    @Inject
    private ProgramLocationService programLocationService;
    @Inject
    private CountryService countryService;
    @Inject
    private AccessibilityService accessibilityService;
    @Inject
    private EnvironmentTypeService environmentTypeService;
    @Inject
    private TopographyService topographyService;
    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private ProgramUserDAO programUserDAO;
    @Inject
    private UserDAO userDAO;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    // Micronaut is naming this mock under 'orcid' for some reason.
    @Named("")
    @MockBean(bean = EmailUtil.class)
    EmailUtil emailUtil() { return mock(EmailUtil.class); }

    @BeforeAll
    void setup() throws Exception {

        // Skip our emails
        doNothing().when(emailUtil()).sendEmail(any(String.class), any(String.class), any(String.class));

        brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        fp = FannyPack.fill("src/test/resources/sql/ProgramControllerIntegrationTest.sql");
        securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Insert system roles
        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        otherUser = userDAO.getUserByOrcId(TestTokenValidator.OTHER_TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        Optional<User> optionalUser = userService.getByOrcid(TestTokenValidator.TEST_USER_ORCID);
        testUser = optionalUser.get();

        // Get species for tests
        Species species = getTestSpecies();
        validSpecies = species;
        // Get role for tests
        validRole = getTestRole();
        validCountry = getTestCountry();
        validAccessibility = getTestAccessibility();
        validEnvironment = getTestEnvironment();
        validTopography = getTestTopography();

        // Insert and get user for tests
        try {
            validUser = fetchTestUser();
        } catch (Exception e){
            throw new Exception(e.toString());
        }

        actingUser = getActingUser();

        // Insert and get program for tests
        dsl.execute(fp.get("InsertOtherProgram"));
        otherProgram = programDao.fetchByName("Other Test Program").get(0);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), otherProgram.getId().toString());

        // Insert and get location for tests
        try {
            validLocation = insertAndFetchTestLocation();
        } catch (Exception e){
            throw new Exception(e.toString());
        }

    }

    public ProgramLocation insertAndFetchTestLocation() throws Exception {

        CountryRequest countryRequest = CountryRequest.builder()
                .id(validCountry.getId())
                .build();
        NameIdRequest accessibilityRequest = NameIdRequest.builder()
                .id(validAccessibility.getId())
                .build();
        NameIdRequest environmentRequest = NameIdRequest.builder()
                .id(validEnvironment.getId())
                .build();
        NameIdRequest topographyRequest = NameIdRequest.builder()
                .id(validTopography.getId())
                .build();

        Feature coordinates = new Feature();
        Point point = new Point(-76.506042, 42.417373, 123);
        coordinates.setGeometry(point);

        ProgramLocationRequest locationRequest = ProgramLocationRequest.builder()
                .country(countryRequest)
                .accessibility(accessibilityRequest)
                .environmentType(environmentRequest)
                .topography(topographyRequest)
                .name("Test Location")
                .abbreviation("TL")
                .coordinates(coordinates)
                .coordinateDescription("Test Point")
                .coordinateUncertainty(BigDecimal.ZERO)
                .documentationUrl("http://www.test.com")
                .exposure("Test")
                .slope(BigDecimal.ZERO)
                .build();

        String json;
        try {
             json = objMapper.writeValueAsString(locationRequest);
        } catch (JsonProcessingException e) {
            throw new Exception("Problem parsing geojson coordinates");
        }

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+otherProgram.getId().toString()+"/locations", json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String locationId = result.get("id").getAsString();

        Optional<ProgramLocation> location = programLocationService.getById(otherProgram.getId(), UUID.fromString(locationId));
        return location.orElseThrow(() -> new Exception("Unable to get test location"));
    }

    public User fetchTestUser() throws Exception{

        Optional<User> user = userService.getByOrcid(TestTokenValidator.TEST_USER_ORCID);
        if (!user.isPresent()){
            throw new Exception("Failed to insert test user");
        }
        return user.get();
    }

    public Species getTestSpecies() {
        List<Species> species = speciesService.getAll();
        return species.get(0);
    }

    public Role getTestRole() {
        List<Role> roles = roleService.getAll();
        return roles.stream().filter(role -> role.getDomain().equals("breeder")).collect(Collectors.toList()).get(0);
    }

    public Country getTestCountry() {
        List<Country> countries = countryService.getAll();
        return countries.get(0);
    }

    public EnvironmentType getTestEnvironment() {
        List<EnvironmentType> environments = environmentTypeService.getAll();
        return environments.get(0);
    }

    public Accessibility getTestAccessibility() {
        List<Accessibility> accessibilities = accessibilityService.getAll();
        return accessibilities.get(0);
    }

    public Topography getTestTopography() {
        List<Topography> topographies = topographyService.getAll();
        return topographies.get(0);
    }

    public AuthenticatedUser getActingUser() {
        UUID id = validUser.getId();
        List<String> systemRoles = new ArrayList<>();
        systemRoles.add(validRole.getDomain());
        return new AuthenticatedUser("test_user", systemRoles, id, new ArrayList<>());
    }

    //region Program Tests
    public void checkValidProgram(ProgramEntity program, JsonObject programJson){

        assertEquals(program.getName(), programJson.get("name").getAsString(), "Wrong name");
        assertEquals(program.getAbbreviation(), programJson.get("abbreviation").getAsString(), "Wrong abbreviation");
        assertEquals(program.getDocumentationUrl(), programJson.get("documentationUrl").getAsString(), "Wrong documentation url");
        assertEquals(program.getObjective(), programJson.get("objective").getAsString(), "Wrong objective");

        JsonObject species = programJson.getAsJsonObject("species");
        assertEquals(program.getSpeciesId().toString(), species.get("id").getAsString(), "Wrong species");

        JsonObject createdByUser = programJson.getAsJsonObject("createdByUser");
        assertEquals(program.getCreatedBy().toString(), createdByUser.get("id").getAsString(), "Wrong created by user");

        JsonObject updatedByUser = programJson.getAsJsonObject("updatedByUser");
        assertEquals(program.getUpdatedBy().toString(), updatedByUser.get("id").getAsString(), "Wrong updated by user");
    }

    public void checkMinimalValidProgram(ProgramEntity program, JsonObject programJson){
        assertEquals(program.getName(), programJson.get("name").getAsString(), "Wrong name");

        JsonObject species = programJson.getAsJsonObject("species");
        assertEquals(program.getSpeciesId().toString(), species.get("id").getAsString(), "Wrong species");

        JsonObject createdByUser = programJson.getAsJsonObject("createdByUser");
        assertEquals(program.getCreatedBy().toString(), createdByUser.get("id").getAsString(), "Wrong created by user");

        JsonObject updatedByUser = programJson.getAsJsonObject("updatedByUser");
        assertEquals(program.getUpdatedBy().toString(), updatedByUser.get("id").getAsString(), "Wrong updated by user");
    }

    @Test
    @Order(1)
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

        checkValidProgram(otherProgram, programResult);
    }

    @Test
    @SneakyThrows
    @Order(2)
    public void postProgramsFullBodySuccess() throws Exception {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .commonName(validSpecies.getCommonName())
                .build();

        Program program = Program.builder()
                .name("Test Program")
                .abbreviation("Test")
                .documentationUrl("localhost")
                .objective("Testing things")
                .species(validSpecies)
                .speciesId(validSpecies.getId())
                .build();

        ProgramRequest validRequest = ProgramRequest.builder()
                .name("Test Program")
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
        validProgram = programDao.fetchById(UUID.fromString(result.get("id").getAsString())).get(0);

        checkMinimalValidProgram(validProgram, result);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId().toString());
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

        Optional<Program> createdProgram = programService.getById(UUID.fromString(newProgramId));
        assertTrue(createdProgram.isPresent(), "Created program was not found");
        Program program = createdProgram.get();

        checkMinimalValidProgram(validProgram, result);

        dsl.execute(fp.get("DeleteProgram"), program.getId().toString(), program.getId().toString(), program.getId().toString());
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
    @Order(3)
    public void putProgramsMinimalBodySuccess() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .build();

        validProgram.setName("changed");
        ProgramRequest validRequest = ProgramRequest.builder()
                .name(validProgram.getName())
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

        checkMinimalValidProgram(validProgram, result);
    }

    @Test
    @Order(4)
    public void putProgramsFullBodySuccess() {

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(validSpecies.getId())
                .commonName(validSpecies.getCommonName())
                .build();

        validProgram.setName("changed");
        validProgram.setAbbreviation("changed abbreviation");
        validProgram.setObjective("changed objective");
        validProgram.setDocumentationUrl("changed doc url");

        ProgramRequest validRequest = ProgramRequest.builder()
                .name(validProgram.getName())
                .abbreviation(validProgram.getAbbreviation())
                .documentationUrl(validProgram.getDocumentationUrl())
                .objective(validProgram.getObjective())
                .species(speciesRequest)
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(String.format("/programs/%s", validProgram.getId()), gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        checkMinimalValidProgram(validProgram, result);
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

        Optional<Program> createdProgram = programService.getById(validProgram.getId());
        assertTrue(createdProgram.isPresent(), "Created program was not found");
        Program program = createdProgram.get();

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

        Optional<Program> createdProgram = programService.getById(UUID.fromString(newProgramId));
        assertTrue(createdProgram.isPresent(), "Created program was not found");
        Program program = createdProgram.get();

        assertEquals(false, program.getActive(), "Inactive flag not set in database");

        dsl.execute(fp.get("DeleteProgram"), newProgramId, newProgramId, newProgramId);
    }

    @Test
    @Order(5)
    public void getProgramQuery() {
        dsl.execute(fp.get("InsertManyPrograms"));
        List<ProgramEntity> allPrograms = programDao.findAll();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs?page=2&pageSize=10&sortField=name&sortOrder=DESC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        assertEquals(10, data.size(), "Wrong page size");
        TestUtils.checkStringSorting(data, "name", SortOrder.DESC);

        JsonObject pagination = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");
        assertEquals((int) Math.ceil(allPrograms.size()/10.0), pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(allPrograms.size(), pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(2, pagination.get("currentPage").getAsInt(), "Wrong current page");
    }

    @Test
    @Order(6)
    public void searchPrograms() {

        List<ProgramEntity> allPrograms = programDao.findAll();
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilters(new ArrayList<>());
        searchRequest.getFilters().add(new FilterRequest("name", "program1"));

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/search?page=1&pageSize=20&sortField=name&sortOrder=ASC", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        // Expect 11, program1, program10->program19
        assertEquals(11, data.size(), "Wrong page size");
        TestUtils.checkStringSorting(data, "name", SortOrder.ASC);

        JsonObject pagination = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");
        assertEquals(1, pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(11, pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");
    }

    //endregion

    //region Program Location Tests
    @Test
    @Order(4)
    public void postProgramsLocationsInvalidProgram() {
        JsonObject requestBody = validProgramLocationRequest();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+invalidProgram+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(4)
    public void postProgramsLocationsInvalidCountry() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject country = new JsonObject();
        country.addProperty("id", invalidCountry);
        requestBody.add("country", country);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId().toString()+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void postProgramsLocationsInvalidEnvironmentType() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject environmentType = new JsonObject();
        environmentType.addProperty("id", invalidEnvironment);
        requestBody.add("environmentType", environmentType);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId().toString()+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void postProgramsLocationsInvalidTopography() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject topography = new JsonObject();
        topography.addProperty("id", invalidTopography);
        requestBody.add("topography", topography);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId().toString()+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void postProgramsLocationsInvalidAccessibility() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject accessibility = new JsonObject();
        accessibility.addProperty("id", invalidAccessibility);
        requestBody.add("accessibility", accessibility);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId().toString()+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void postProgramsLocationsMissingBody() {
        JsonObject requestBody = new JsonObject();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    public JsonObject validProgramLocationRequest() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        return requestBody;
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void postProgramsLocationsIgnoresBodyProgramId() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        requestBody.addProperty("programId", invalidLocation);
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(requestBody.get("name").getAsString(), result.get("name").getAsString(),"Wrong name");
        assertEquals(validProgramId, result.get("programId").getAsString(), "programId does not match path programId");

        String locationId = result.get("id").getAsString();
        programLocationService.delete(UUID.fromString(locationId));
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void postProgramsLocationsOnlyNameSuccess() {
        JsonObject requestBody = validProgramLocationRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(requestBody.get("name").getAsString(), result.get("name").getAsString(),"Wrong name");
        String locationId = result.get("id").getAsString();

        // Check that the coordinates is [NULL] in DB
        Optional<ProgramLocation> programLocation = programLocationService.getById(UUID.fromString(validProgramId), UUID.fromString(locationId));
        assertNull(programLocation.get().getCoordinates());

        programLocationService.delete(UUID.fromString(locationId));
    }

    public JsonObject validProgramLocationCoordinatePointRequest() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject coordinates = new JsonObject();
        JsonObject geometry = new JsonObject();
        JsonArray coordinatesArray = new JsonArray();
        coordinatesArray.add(-76.506042);
        coordinatesArray.add(42.417373);
        coordinatesArray.add(123);
        geometry.add("coordinates", coordinatesArray);
        geometry.addProperty("type", "Point");
        coordinates.add("geometry", geometry);
        coordinates.addProperty("type", "Feature");
        requestBody.add("coordinates", coordinates);
        return requestBody;
    }

    public JsonObject validProgramLocationCoordinatePolygonRequest() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject coordinates = new JsonObject();
        JsonObject geometry = new JsonObject();
        JsonArray coordinatesArray = new JsonArray();
        JsonArray polyArray = new JsonArray();
        JsonArray coordinate1 = new JsonArray();
        coordinate1.add(-76.5);
        coordinate1.add(42.4);
        JsonArray coordinate2 = new JsonArray();
        coordinate2.add(-76.6);
        coordinate2.add(42.5);
        JsonArray coordinate3 = new JsonArray();
        coordinate3.add(-76.4);
        coordinate3.add(42.3);
        polyArray.add(coordinate1);
        polyArray.add(coordinate2);
        polyArray.add(coordinate3);
        polyArray.add(coordinate1);
        coordinatesArray.add(polyArray);
        geometry.add("coordinates", coordinatesArray);
        geometry.addProperty("type", "Polygon");
        coordinates.add("geometry", geometry);
        coordinates.addProperty("type", "Feature");
        requestBody.add("coordinates", coordinates);
        return requestBody;
    }

    public JsonObject validProgramLocationCoordinateLineStringRequest() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject coordinates = new JsonObject();
        JsonObject geometry = new JsonObject();
        JsonArray coordinatesArray = new JsonArray();
        JsonArray coordinate1 = new JsonArray();
        coordinate1.add(-76.5);
        coordinate1.add(42.4);
        JsonArray coordinate2 = new JsonArray();
        coordinate2.add(-76.6);
        coordinate2.add(42.5);
        coordinatesArray.add(coordinate1);
        coordinatesArray.add(coordinate2);
        geometry.add("coordinates", coordinatesArray);
        geometry.addProperty("type", "LineString");
        coordinates.add("geometry", geometry);
        coordinates.addProperty("type", "Feature");
        requestBody.add("coordinates", coordinates);
        return requestBody;
    }

    public JsonObject validProgramLocationCoordinatePointBadLatRequest() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject coordinates = new JsonObject();
        JsonObject geometry = new JsonObject();
        JsonArray coordinatesArray = new JsonArray();
        coordinatesArray.add(-76.506042);
        coordinatesArray.add(100.417373);
        coordinatesArray.add(123);
        geometry.add("coordinates", coordinatesArray);
        geometry.addProperty("type", "Point");
        coordinates.add("geometry", geometry);
        coordinates.addProperty("type", "Feature");
        requestBody.add("coordinates", coordinates);
        return requestBody;
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void postProgramsLocationsCoordinatesPointSuccess() {
        JsonObject requestBody = validProgramLocationCoordinatePointRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");


        String locationId = result.get("id").getAsString();
        programLocationService.delete(UUID.fromString(locationId));
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void postProgramsLocationsCoordinatesPolygonSuccess() {
        JsonObject requestBody = validProgramLocationCoordinatePolygonRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");


        String locationId = result.get("id").getAsString();
        programLocationService.delete(UUID.fromString(locationId));
    }

    @Test
    @Order(4)
    public void postProgramsLocationsCoordinatesLineStringFailed() {
        JsonObject requestBody = validProgramLocationCoordinateLineStringRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void postProgramsLocationsCoordinatesInvalidLatLonFailed() {
        JsonObject requestBody = validProgramLocationCoordinatePointBadLatRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgramId+"/locations", requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

    }

    @Test
    @Order(4)
    public void putProgramsLocationsInvalidLocation() {
        JsonObject requestBody = validProgramLocationRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+invalidLocation, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsInvalidCountry() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject country = new JsonObject();
        country.addProperty("id", invalidCountry);
        requestBody.add("country", country);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsInvalidEnvironmentType() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject environmentType = new JsonObject();
        environmentType.addProperty("id", invalidEnvironment);
        requestBody.add("environmentType", environmentType);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsInvalidTopography() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject topography = new JsonObject();
        topography.addProperty("id", invalidTopography);
        requestBody.add("topography", topography);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsInvalidAccessibility() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject accessibility = new JsonObject();
        accessibility.addProperty("id", invalidAccessibility);
        requestBody.add("accessibility", accessibility);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsMissingBody() {
        JsonObject requestBody = new JsonObject();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsCoordinatesLineStringFailed() {
        JsonObject requestBody = validProgramLocationCoordinateLineStringRequest();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    public void putProgramsLocationsCoordinatesInvalidLatLonFailed() {
        JsonObject requestBody = validProgramLocationCoordinatePointBadLatRequest();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
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
    @Order(4)
    public void putProgramsLocationsCoordinatesPointSuccess() {
        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = validProgramLocationCoordinatePointRequest();
        String validProgramId = otherProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+locationId, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        programLocationService.delete(UUID.fromString(locationId));
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void putProgramsLocationsCoordinatesPolygonSuccess() {
        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = validProgramLocationCoordinatePolygonRequest();
        String validProgramId = otherProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+locationId, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        programLocationService.delete(UUID.fromString(locationId));

    }

    @Test
    @SneakyThrows
    @Order(4)
    public void putProgramsLocationsIgnoresBodyProgramId() {
        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        requestBody.addProperty("programId", invalidLocation);
        String validProgramId = otherProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+locationId, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(requestBody.get("name").getAsString(), result.get("name").getAsString(),"Wrong name");
        assertEquals(validProgramId, result.get("programId").getAsString(), "programId does not match path programId");

        programLocationService.delete(UUID.fromString(locationId));
    }


    @Test
    @SneakyThrows
    @Order(4)
    public void putProgramsLocationsOnlyNameSuccess() {
        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = validProgramLocationRequest();
        String validProgramId = otherProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+locationId, requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(1, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(),"Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals("Field 1", result.get("name").getAsString(), "Wrong name");

        // Check that the coordinates is [NULL] in DB
        Optional<ProgramLocation> programLocation = programLocationService.getById(UUID.fromString(validProgramId), UUID.fromString(locationId));
        assertNull(programLocation.get().getCoordinates());

        programLocationService.delete(UUID.fromString(locationId));
    }

    @SneakyThrows
    public void checkValidLocation(JsonObject programLocation) {

        JsonObject country = programLocation.getAsJsonObject("country");
        assertEquals(validLocation.getCountry().getId().toString(), country.get("id").getAsString(), "Wrong country id");
        assertEquals(validLocation.getCountry().getName(), country.get("name").getAsString(), "Wrong country name");
        assertEquals(validLocation.getCountry().getAlpha_2Code(), country.get("alpha2Code").getAsString(), "Wrong country alpha 2 code");
        assertEquals(validLocation.getCountry().getAlpha_3Code(), country.get("alpha3Code").getAsString(), "Wrong country alpha 3 code");
        JsonObject accessibility = programLocation.getAsJsonObject("accessibility");
        assertEquals(validLocation.getAccessibility().getId().toString(), accessibility.get("id").getAsString(), "Wrong accessibility id");
        assertEquals(validLocation.getAccessibility().getName(), accessibility.get("name").getAsString(), "Wrong accessibility name");
        JsonObject environment = programLocation.getAsJsonObject("environmentType");
        assertEquals(validLocation.getEnvironmentType().getId().toString(), environment.get("id").getAsString(), "Wrong environment type id");
        assertEquals(validLocation.getEnvironmentType().getName(), environment.get("name").getAsString(), "Wrong environment type name");
        JsonObject topography = programLocation.getAsJsonObject("topography");
        assertEquals(validLocation.getTopography().getId().toString(), topography.get("id").getAsString(), "Wrong topography id");
        assertEquals(validLocation.getTopography().getName(), topography.get("name").getAsString(), "Wrong topography type name");
        assertEquals(validLocation.getId().toString(), programLocation.get("id").getAsString(), "Wrong location id");
        assertEquals(validLocation.getProgramId().toString(), programLocation.get("programId").getAsString(), "Wrong program id");
        assertEquals(validLocation.getName(), programLocation.get("name").getAsString(), "Wrong name");
        assertEquals(validLocation.getAbbreviation(), programLocation.get("abbreviation").getAsString(), "Wrong abbreviation");

        assertEquals(validLocation.getCoordinateDescription(), programLocation.get("coordinateDescription").getAsString(), "Wrong coordinate description");
        assertEquals(validLocation.getCoordinateUncertainty(), programLocation.get("coordinateUncertainty").getAsBigDecimal(), "Wrong coordinate uncertainty");
        assertEquals(validLocation.getDocumentationUrl(), programLocation.get("documentationUrl").getAsString(), "Wrong documentation url");
        assertEquals(validLocation.getExposure(), programLocation.get("exposure").getAsString(), "Wrong exposure");
        assertEquals(validLocation.getSlope(), programLocation.get("slope").getAsBigDecimal(), "Wrong slope");

        JsonObject coords = programLocation.get("coordinates").getAsJsonObject();
        ObjectMapper objMapper = new ObjectMapper();
        Feature feature = objMapper.readValue(new Gson().toJson(coords), Feature.class);
        assertEquals(validLocation.getCoordinatesJson(), feature, "Wrong coordinates");

    }

    @Test
    @Order(4)
    public void getProgramsLocationsSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+otherProgram.getId().toString()+"/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(1, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), "Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        JsonObject programLocation = data.get(0).getAsJsonObject();
        checkValidLocation(programLocation);
    }

    @Test
    @Order(4)
    public void getProgramsLocationsSingleSuccess() {
        String validLocationId = validLocation.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+otherProgram.getId().toString()+"/locations/"+validLocationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(1, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(),"Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidLocation(result);
    }

    @Test
    @Order(4)
    public void getProgramsLocationsSingleInvalidId() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgram.getId().toString()+"/locations/"+invalidLocation)
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
    public void getProgramsLocationsSingleWrongProgramId() {

        String programId = validProgram.getId().toString();
        String validLocationId = validLocation.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+programId+"/locations/"+validLocationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(4)
    public void getProgramsLocationsInvalidProgramId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+invalidProgram+"/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }



    @Test
    @Order(4)
    public void archiveProgramsLocationsNotExistingLocationId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+otherProgram.getId().toString()+"/locations/"+invalidLocation)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(4)
    public void archiveProgramsLocationsNotExistingProgramId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+invalidProgram+"/locations/"+validLocation.getId().toString())
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
    public void archiveProgramsLocationsWrongProgramId() {
        String programId = validProgram.getId().toString();
        String validLocationId = validLocation.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+programId+"/locations/"+validLocationId)
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
    public void archiveProgramsLocations() {

        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+otherProgram.getId().toString()+"/locations/"+locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String>  response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // GET single to make sure active flag is changed
        call = client.exchange(
                GET("/programs/"+otherProgram.getId().toString()+"/locations/"+locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(false, result.get("active").getAsBoolean(), "active should be false");

        // GET all to make sure it doesn't show in list
        call = client.exchange(
                GET("/programs/"+otherProgram.getId().toString()+"/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        // will have validLocation only for other tests but not the location added in this test
        assertEquals(1, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), "Should only be one location for valid");

        programLocationService.delete(UUID.fromString(locationId));
    }

    //endregion

    //region Program User Tests
    @Test
    @Order(5)
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
    @Order(5)
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
    @Order(5)
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
    @Order(5)
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
    @Order(5)
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
    @SneakyThrows
    @Order(5)
    public void postProgramsUsersDuplicateRoles() {
        String validProgramId = otherProgram.getId().toString();
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", otherUser.getId().toString());
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

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray rolesRx = result.getAsJsonArray("roles");

        assertEquals(rolesRx.size(), 1, "Wrong number of roles");

        JsonObject roleRx = rolesRx.get(0).getAsJsonObject();

        assertEquals(roleRx.get("id").getAsString(),validRole.getId().toString(), "Wrong role id");
        assertEquals(roleRx.get("domain").getAsString(),validRole.getDomain(), "Wrong domain");
    }

    @Test
    @Order(5)
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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(5)
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
    @SneakyThrows
    @Order(6)
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
                PUT("/programs/"+validProgramId+"/users/" + validUser.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray rolesRx = result.getAsJsonArray("roles");

        assertEquals(rolesRx.size(), 1, "Wrong number of roles");

        JsonObject roleRx = rolesRx.get(0).getAsJsonObject();

        assertEquals(roleRx.get("id").getAsString(),validRole.getId().toString(), "Wrong role id");
        assertEquals(roleRx.get("domain").getAsString(),validRole.getDomain(), "Wrong domain");
    }

    @Test
    @Order(6)
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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    public JsonObject validProgramUserRequest() {
        JsonObject requestBody = new JsonObject();
        JsonObject user = new JsonObject();
        user.addProperty("id", otherUser.getId().toString());
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
    @Order(6)
    void getProgramsUsersSuccess() {
        String validProgramId = otherProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(2, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), "Wrong totalCount");

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
        JsonObject program = programUser.getAsJsonObject("program");
        assertEquals(otherProgram.getId().toString(), program.get("id").getAsString(), "Wrong program id");
        assertEquals(otherProgram.getName(), program.get("name").getAsString(), "Wrong program name");
        assertEquals(otherProgram.getAbbreviation(), program.get("abbreviation").getAsString(), "Wrong program abbreviation");
        assertEquals(otherProgram.getObjective(), program.get("objective").getAsString(), "Wrong program objective");
    }

    @Test
    @Order(6)
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
        JsonObject program = result.getAsJsonObject("program");
        assertEquals(validProgram.getId().toString(), program.get("id").getAsString(), "Wrong program id");
        assertEquals(validProgram.getName(), program.get("name").getAsString(), "Wrong program name");
        assertEquals(validProgram.getAbbreviation(), program.get("abbreviation").getAsString(), "Wrong program abbreviation");
        assertEquals(validProgram.getObjective(), program.get("objective").getAsString(), "Wrong program objective");
    }

    @Test
    @Order(6)
    public void archiveProgramsUsersNotExistingProgramId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+invalidProgram+"/users/"+invalidProgram).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(6)
    public void archiveProgramsUsersNotExistingUserId() {
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
    @Order(7)
    @SneakyThrows
    void getProgramsUsersMultipleUsersSingleRoleSuccess() {

        // create a second user to test with
        // don't have test database setup yet so doing it this way for now
        String validProgramId = otherProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(2, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), "Wrong totalCount");

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        // may be brittle relying on the ordering
        //JsonObject programUser = data.get(1).getAsJsonObject();
        Boolean validUserSeen = false;
        Boolean test2Seen = false;
        for (JsonElement jsonProgramUser: data.getAsJsonArray()) {
            JsonObject programUser = (JsonObject) jsonProgramUser;
            JsonObject user = programUser.getAsJsonObject("user");
            User checkUser;
            if (user.get("id").getAsString().equals(validUser.getId().toString())){
                validUserSeen = true;
                checkUser = validUser;
            } else if (user.get("id").getAsString().equals(otherUser.getId().toString())) {
                test2Seen = true;
                checkUser = otherUser;
            } else {
                throw new AssertionFailedError("Unexpected user was returned.");
            }

            assertEquals(checkUser.getId().toString(), user.get("id").getAsString(), "Wrong user id");
            assertEquals(checkUser.getName(), user.get("name").getAsString(), "Wrong name");
            assertEquals(checkUser.getEmail(), user.get("email").getAsString(), "Wrong email");
            JsonArray roles = programUser.getAsJsonArray("roles");
            JsonObject role = roles.get(0).getAsJsonObject();
            assertEquals(validRole.getId().toString(), role.get("id").getAsString(), "Wrong role id");
            assertEquals(validRole.getDomain(), role.get("domain").getAsString(), "Wrong domain");
        }

        if (!validUserSeen || !test2Seen){
            throw new AssertionFailedError("Both users were not returned");
        }
    }

    @Test
    @Order(7)
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
    @SneakyThrows
    @Order(8)
    public void archiveProgramsUsersSuccess() {
        String validProgramId = otherProgram.getId().toString();
        String validUserId = validUser.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/" + validProgramId + "/users/" + validUserId).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Remove program user for following tests since endpoint only archives it.
        programUserService.removeProgramUser(validProgram.getId(), validUser.getId());
    }

    @Test
    @SneakyThrows
    @Order(9)
    public void getUsersInactiveNotReturned() {
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(1, meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), "Wrong totalCount");
    }

    @Test
    @Order(9)
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
    @Order(9)
    void getProgramsUsersNoUsers() {
        String validProgramId = otherProgram.getId().toString();
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        assertEquals(meta.getAsJsonObject("pagination").get("totalCount").getAsInt(), 1, "Wrong totalCount");

        JsonArray result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(result.size(),1, "Wrong size");

    }

    @Test
    @Order(9)
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
    @Order(9)
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
    @Order(9)
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
    @Order(9)
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
    @Order(9)
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

    @Test
    @Order(10)
    @SneakyThrows
    public void getProgramUsersQuery() {
        FannyPack userFp = FannyPack.fill("src/test/resources/sql/UserControllerIntegrationTest.sql");
        dsl.execute(userFp.get("InsertProgram"));
        dsl.execute(userFp.get("InsertManyUsers"));
        dsl.execute(fp.get("InsertManyProgramUsers"),
                validProgram.getId().toString(), validProgram.getId().toString(),
                validProgram.getId().toString(), validProgram.getId().toString());
        List<ProgramUser> allProgramUsers = programUserService.getProgramUsers(validProgram.getId());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/users?page=1&pageSize=30&sortField=roles&sortOrder=ASC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        assertEquals(allProgramUsers.size(), data.size(), "Wrong page size");
        TestUtils.checkStringListSorting(getRoles(data), SortOrder.ASC);

        JsonObject pagination = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");
        assertEquals((int) Math.ceil(allProgramUsers.size()/30.0), pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(allProgramUsers.size(), pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");
    }

    @Test
    @Order(11)
    public void searchProgramUsers() {

        List<ProgramUser> allProgramUsers = programUserDAO.getAllProgramUsers();
        SearchRequest searchRequest = new SearchRequest();

        searchRequest.setFilters(new ArrayList<>());
        searchRequest.getFilters().add(new FilterRequest("roles", "breed"));

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/users/search?page=1&pageSize=20&sortField=roles&sortOrder=ASC", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        // Expect 12, user12, user20->user29 + 1 original users
        assertEquals(13, data.size(), "Wrong page size");
        TestUtils.checkStringListSorting(getRoles(data), SortOrder.ASC);

        JsonObject pagination = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");
        assertEquals(1, pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(13, pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");
    }

    @Test
    public void getObservationLevels() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + otherProgram.getId() + "/observation_level")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        assertEquals(1, data.size(), "Wrong number of levels returned");
    }

    private List<List<String>> getRoles(JsonArray data) {
        List<List<String>> roleResults = new ArrayList<>();
        for (JsonElement el : data) {
            JsonObject programUser = (JsonObject) el;
            JsonArray roles = programUser.get("roles").getAsJsonArray();
            List<String> userRoles = new ArrayList<>();
            for (JsonElement roleEl : roles) {
                JsonObject role = (JsonObject) roleEl;
                userRoles.add(role.get("domain").getAsString());
            }
            Collections.sort(userRoles);
            roleResults.add(userRoles);
        }
        return roleResults;
    }

    //endregion
}
