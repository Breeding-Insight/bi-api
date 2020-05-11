package org.breedinginsight.api.v1.controller;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.breedinginsight.api.model.v1.request.*;
import org.breedinginsight.model.*;
import org.breedinginsight.services.*;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    ProgramLocation validLocation;
    Country validCountry;
    EnvironmentType validEnvironment;
    Accessibility validAccessibility;
    Topography validTopography;

    User testUser;

    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    String invalidProgram = invalidUUID;
    String invalidUser = invalidUUID;
    String invalidRole = invalidUUID;
    String invalidSpecies = invalidUUID;
    String invalidLocation= invalidUUID;
    String invalidCountry = invalidUUID;
    String invalidEnvironment = invalidUUID;
    String invalidAccessibility = invalidUUID;
    String invalidTopography = invalidUUID;

    Gson gson = new Gson();
    ListAppender<ILoggingEvent> loggingEventListAppender;

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
    ProgramLocationService programLocationService;
    @Inject
    CountryService countryService;
    @Inject
    AccessibilityService accessibilityService;
    @Inject
    EnvironmentTypeService environmentTypeService;
    @Inject
    TopographyService topographyService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    void setup() throws Exception {

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
        // Insert and get program for tests
        try {
            validProgram = insertAndFetchTestProgram();
        } catch (Exception e){
            throw new Exception(e.toString());
        }
        // Insert and get location for tests
        try {
            validLocation = insertAndFetchTestLocation();
        } catch (Exception e){
            throw new Exception(e.toString());
        }

    }

    @AfterAll
    void teardown() throws Exception{

        try {
            programLocationService.delete(validLocation.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete test location");
        }

        try {
            programService.delete(validProgram.getId());
        } catch (DoesNotExistException e){
            throw new Exception("Unable to delete test program");
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

        try {
            ProgramLocation location = programLocationService.create(testUser, validProgram.getId(), locationRequest);
            return location;
        } catch (UnprocessableEntityException e){
            throw new Exception("Unable to create test location");
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
        return roles.get(0);
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

    //region Program Location Tests
    @Test
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
    public void putProgramsLocationsInvalidCountry() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject country = new JsonObject();
        country.addProperty("id", invalidCountry);
        requestBody.add("country", country);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    public void putProgramsLocationsInvalidEnvironmentType() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject environmentType = new JsonObject();
        environmentType.addProperty("id", invalidEnvironment);
        requestBody.add("environmentType", environmentType);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    public void putProgramsLocationsInvalidTopography() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject topography = new JsonObject();
        topography.addProperty("id", invalidTopography);
        requestBody.add("topography", topography);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    public void putProgramsLocationsInvalidAccessibility() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", "Field 1");
        JsonObject accessibility = new JsonObject();
        accessibility.addProperty("id", invalidAccessibility);
        requestBody.add("accessibility", accessibility);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId().toString()+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
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
    public void putProgramsLocationsCoordinatesLineStringFailed() {
        JsonObject requestBody = validProgramLocationCoordinateLineStringRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+validLocation.getId().toString(), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    public void putProgramsLocationsCoordinatesInvalidLatLonFailed() {
        JsonObject requestBody = validProgramLocationCoordinatePointBadLatRequest();
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgramId+"/locations/"+validLocation.getId().toString(), requestBody.toString())
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
    public void putProgramsLocationsCoordinatesPointSuccess() {

        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = validProgramLocationCoordinatePointRequest();
        String validProgramId = validProgram.getId().toString();

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
    public void putProgramsLocationsCoordinatesPolygonSuccess() {
        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = validProgramLocationCoordinatePolygonRequest();
        String validProgramId = validProgram.getId().toString();

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
    public void putProgramsLocationsOnlyNameSuccess() {
        ProgramLocation location = insertAndFetchTestLocation();
        String locationId = location.getId().toString();

        JsonObject requestBody = validProgramLocationRequest();
        String validProgramId = validProgram.getId().toString();

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
    @Order(1)
    public void getProgramsLocationsSuccess() {
        String validProgramId = validProgram.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/locations")
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
    public void getProgramsLocationsSingleSuccess() {
        String validProgramId = validProgram.getId().toString();
        String validLocationId = validLocation.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgramId+"/locations/"+validLocationId)
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
    public void archiveProgramsLocationsNotExistingLocationId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgram.getId().toString()+"/locations/"+invalidLocation)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void archiveProgramsLocations() {

        ProgramLocation location = insertAndFetchTestLocation();
        String validProgramId = validProgram.getId().toString();
        String locationId = location.getId().toString();

        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgramId+"/locations/"+locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String>  response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // GET single to make sure active flag is changed
        call = client.exchange(
                GET("/programs/"+validProgramId+"/locations/"+locationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(false, result.get("active").getAsBoolean(), "active should be false");

        // GET all to make sure it doesn't show in list
        call = client.exchange(
                GET("/programs/"+validProgramId+"/locations")
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
    @SneakyThrows
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

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray rolesRx = result.getAsJsonArray("roles");

        assertEquals(rolesRx.size(), 1, "Wrong number of roles");

        JsonObject roleRx = rolesRx.get(0).getAsJsonObject();

        assertEquals(roleRx.get("id").getAsString(),validRole.getId().toString(), "Wrong role id");
        assertEquals(roleRx.get("domain").getAsString(),validRole.getDomain(), "Wrong domain");

        programUserService.removeProgramUser(validProgram.getId(), validUser.getId());

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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @SneakyThrows
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

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray rolesRx = result.getAsJsonArray("roles");

        assertEquals(rolesRx.size(), 1, "Wrong number of roles");

        JsonObject roleRx = rolesRx.get(0).getAsJsonObject();

        assertEquals(roleRx.get("id").getAsString(),validRole.getId().toString(), "Wrong role id");
        assertEquals(roleRx.get("domain").getAsString(),validRole.getDomain(), "Wrong domain");

        programUserService.removeProgramUser(validProgram.getId(), validUser.getId());
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
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
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
    @SneakyThrows
    void getProgramsUsersMultipleUsersSingleRoleSuccess() {

        // create a second user to test with
        // don't have test database setup yet so doing it this way for now
        String validProgramId = validProgram.getId().toString();

        UserIdRequest userRequest = UserIdRequest.builder().name("Test2").email("test2@test.com").build();
        RoleRequest roleRequest = RoleRequest.builder().id(validRole.getId()).build();
        ArrayList<RoleRequest> rolesList = new ArrayList<>();
        rolesList.add(roleRequest);

        ProgramUserRequest request = ProgramUserRequest.builder().user(userRequest).roles(rolesList).build();
        ProgramUser test2 = programUserService.addProgramUser(testUser, validProgram.getId(), request);

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
        JsonObject programUser = data.get(1).getAsJsonObject();
        JsonObject user = programUser.getAsJsonObject("user");
        assertEquals(validUser.getId().toString(), user.get("id").getAsString(), "Wrong user id");
        assertEquals(validUser.getName(), user.get("name").getAsString(), "Wrong name");
        assertEquals(validUser.getEmail(), user.get("email").getAsString(), "Wrong email");
        JsonArray roles = programUser.getAsJsonArray("roles");
        JsonObject role = roles.get(0).getAsJsonObject();
        assertEquals(validRole.getId().toString(), role.get("id").getAsString(), "Wrong role id");
        assertEquals(validRole.getDomain(), role.get("domain").getAsString(), "Wrong domain");

        JsonObject programUser2 = data.get(0).getAsJsonObject();
        JsonObject user2 = programUser2.getAsJsonObject("user");
        assertEquals(test2.getUser().getId().toString(), user2.get("id").getAsString(), "Wrong user id");
        assertEquals(test2.getUser().getName(), user2.get("name").getAsString(), "Wrong name");
        assertEquals(test2.getUser().getEmail(), user2.get("email").getAsString(),"Wrong email");
        JsonArray roles2 = programUser2.getAsJsonArray("roles");
        JsonObject role2 = roles2.get(0).getAsJsonObject();
        assertEquals(validRole.getId().toString(), role2.get("id").getAsString(), "Wrong role id");
        assertEquals(validRole.getDomain(), role2.get("domain").getAsString(), "Wrong domain");

        // remove user from program and delete user from system
        programUserService.removeProgramUser(validProgram.getId(), test2.getUser().getId());
        userService.delete(test2.getUser().getId());
    }

    @Test
    @Order(5)
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
    @Order(6)
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

        Optional<Program> createdProgram = programService.getById(UUID.fromString(newProgramId));
        assertTrue(createdProgram.isPresent(), "Created program was not found");
        Program program = createdProgram.get();

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

        Optional<Program> createdProgram = programService.getById(UUID.fromString(newProgramId));
        assertTrue(createdProgram.isPresent(), "Created program was not found");
        Program program = createdProgram.get();

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

        programService.delete(UUID.fromString(newProgramId));
    }
    //endregion
}
