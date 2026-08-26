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

package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.client.v2.typeAdapters.PaginationTypeAdapter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIPagination;
import org.brapi.v2.model.core.BrAPIService;
import org.brapi.v2.model.core.BrAPIService.MethodsEnum;
import org.brapi.v2.model.core.BrAPIServerInfo;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIV2ControllerIntegrationTest extends BrAPITest {

    @Inject
    @Client("/")
    RxHttpClient biClient;

    @Property(name = "micronaut.bi.api.version")
    String biApiVersion;

    private FannyPack fp;

    private Gson GSON = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .registerTypeAdapter(BrAPIPagination.class, new PaginationTypeAdapter())
                                         .create();

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private UserDAO userDAO;

    private ProgramEntity validProgram;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        fp = FannyPack.fill("src/test/resources/sql/BrapiObservationVariablesControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Insert system roles
        User testUser = userDAO.getUserByOAuthId(TestTokenValidator.TEST_USER_ORCID)
                               .get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"),
                    testUser.getId()
                            .toString());

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));
        dsl.execute(fp.get("InsertTestProgramUser"));
        dsl.execute(fp.get("InsertOtherTestProgramUser"));

        // Retrieve our new data
        validProgram = programDao.findAll()
                                 .stream()
                                 .filter(programEntity -> programEntity.getName()
                                                                       .equals("Test Program"))
                                 .findFirst()
                                 .get();

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"),
                    testUser.getId()
                            .toString(),
                    validProgram.getId()
                                .toString());

        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        super.getBrapiDsl()
             .execute(brapiFp.get("InsertSpecies"));
    }

    @Test
    public void testRootServerInfo() {
        Flowable<HttpResponse<String>> call = biClient.exchange(GET("/v1/brapi/v2/serverinfo"), String.class);

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body(), "Response body is empty");

        JsonObject result = JsonParser.parseString(response.body())
                                      .getAsJsonObject()
                                      .getAsJsonObject("result");
        BrAPIServerInfo serverInfo = GSON.fromJson(result, BrAPIServerInfo.class);

        assertEquals("Breeding Insight", serverInfo.getOrganizationName());
        assertEquals("DeltaBreed", serverInfo.getServerName());
        assertEquals("bidevteam@cornell.edu", serverInfo.getContactEmail());
        assertEquals("https://breedinginsight.org", serverInfo.getOrganizationURL());
        assertEquals("DeltaBreed provides server information and program discovery at this root. Program-scoped BrAPI calls use https://app.breedinginsight.net/v1/programs/{programId}/brapi/v2", serverInfo.getServerDescription());
        assertEquals("Cornell University, Ithaca, NY, USA", serverInfo.getLocation());
        assertEquals("https://brapi.org/specification", serverInfo.getDocumentationURL());

        assertEquals(Map.ofEntries(
                Map.entry("serverinfo", Set.of(MethodsEnum.GET)),
                Map.entry("programs", Set.of(MethodsEnum.GET, MethodsEnum.POST)),
                Map.entry("programs/{programDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT))
        ), getMethodsByService(serverInfo));
        assertAllCallsHaveVersions(serverInfo, Set.of("2.0", "2.1"));
    }

    @Test
    public void testProgramServerInfoOnlyListsExplicitOverrides() {
        String path = String.format("%s/programs/%s/brapi/v2/serverinfo", biApiVersion, validProgram.getId());
        Flowable<HttpResponse<String>> call = biClient.exchange(GET(path), String.class);

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body(), "Response body is empty");

        JsonObject result = JsonParser.parseString(response.body())
                                      .getAsJsonObject()
                                      .getAsJsonObject("result");
        BrAPIServerInfo serverInfo = GSON.fromJson(result, BrAPIServerInfo.class);

        assertEquals(Map.ofEntries(
                Map.entry("serverinfo", Set.of(MethodsEnum.GET)),
                Map.entry("commoncropnames", Set.of(MethodsEnum.GET)),
                Map.entry("lists", Set.of(MethodsEnum.GET)),
                Map.entry("lists/{listDbId}", Set.of(MethodsEnum.DELETE)),
                Map.entry("programs", Set.of(MethodsEnum.GET, MethodsEnum.POST)),
                Map.entry("programs/{programDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("studies", Set.of(MethodsEnum.GET, MethodsEnum.POST)),
                Map.entry("studies/{studyDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("trials", Set.of(MethodsEnum.GET, MethodsEnum.POST)),
                Map.entry("trials/{trialDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("germplasm", Set.of(MethodsEnum.GET)),
                Map.entry("germplasm/{germplasmDbId}", Set.of(MethodsEnum.GET)),
                Map.entry("search/germplasm", Set.of(MethodsEnum.POST)),
                Map.entry("search/germplasm/{searchResultId}", Set.of(MethodsEnum.GET)),
                Map.entry("images", Set.of(MethodsEnum.GET, MethodsEnum.POST)),
                Map.entry("images/{imageDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("images/{imageDbId}/imagecontent", Set.of(MethodsEnum.PUT)),
                Map.entry("observationlevels", Set.of(MethodsEnum.GET)),
                Map.entry("observationunits", Set.of(MethodsEnum.GET, MethodsEnum.POST, MethodsEnum.PUT)),
                Map.entry("observationunits/{observationUnitDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("observationunits/table", Set.of(MethodsEnum.GET)),
                Map.entry("variables", Set.of(MethodsEnum.GET, MethodsEnum.POST)),
                Map.entry("variables/{observationVariableDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("observations", Set.of(MethodsEnum.GET, MethodsEnum.POST, MethodsEnum.PUT)),
                Map.entry("observations/{observationDbId}", Set.of(MethodsEnum.GET, MethodsEnum.PUT)),
                Map.entry("observations/table", Set.of(MethodsEnum.GET)),
                Map.entry("germplasm/{germplasmDbId}/pedigree", Set.of(MethodsEnum.GET)),
                Map.entry("germplasm/{germplasmDbId}/progeny", Set.of(MethodsEnum.GET)),
                Map.entry("pedigree", Set.of(MethodsEnum.GET, MethodsEnum.POST, MethodsEnum.PUT))
        ), getMethodsByService(serverInfo));

        serverInfo.getCalls().forEach(service -> {
            Set<String> expectedVersions;
            if (service.getService().equals("germplasm/{germplasmDbId}/pedigree") ||
                    service.getService().equals("germplasm/{germplasmDbId}/progeny")) {
                expectedVersions = Set.of("2.0");
            } else if (service.getService().equals("pedigree")) {
                expectedVersions = Set.of("2.1");
            } else {
                expectedVersions = Set.of("2.0", "2.1");
            }
            assertEquals(expectedVersions, new HashSet<>(service.getVersions()), service.getService());
        });
    }

    @Test
    @SneakyThrows
    public void testPostVariablesNotFound() {
        BrAPIObservationVariable variable = generateVariable();

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/variables",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(variable))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = postCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void testPutVariablesNotFound() {
        BrAPIObservationVariable variable = generateVariable();

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/variables",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(variable))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = postCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = HttpMethod.class, names = {"GET", "POST", "PUT"})
    public void testCatchallRequiresSystemAdmin(HttpMethod method) {
        String path = String.format("%s/programs/%s/brapi/v2/unsupported", biApiVersion, validProgram.getId());
        HttpRequest<?> request = HttpRequest.create(method, path)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bearerAuth("other-registered-user");

        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class,
                                                                          () -> biClient.exchange(request, String.class)
                                                                                        .blockingFirst());

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }



    private BrAPIObservationVariable generateVariable() {
        var random = UUID.randomUUID()
                         .toString();
        return new BrAPIObservationVariable().observationVariableName("test" + random)
                                             .commonCropName("Grape")
                                             .externalReferences(Collections.singletonList(new BrAPIExternalReference().referenceID("abc123")
                                                                                                                       .referenceId("abc123")
                                                                                                                       .referenceSource("breedinginsight.org")))
                                             .trait(new BrAPITrait().traitClass("Agronomic")
                                                                    .traitName("test trait" + random))
                                             .method(new BrAPIMethod().methodName("test method" + random)
                                                                      .methodClass("Measurement"))
                                             .scale(new BrAPIScale().scaleName("test scale" + random)
                                                                    .dataType(BrAPITraitDataType.NUMERICAL));
    }

    private Map<String, Set<MethodsEnum>> getMethodsByService(BrAPIServerInfo serverInfo) {
        return serverInfo.getCalls().stream()
                         .collect(Collectors.toMap(BrAPIService::getService,
                                                   service -> new HashSet<>(service.getMethods())));
    }

    private void assertAllCallsHaveVersions(BrAPIServerInfo serverInfo, Set<String> versions) {
        serverInfo.getCalls().forEach(service ->
                assertEquals(versions, new HashSet<>(service.getVersions()), service.getService()));
    }
}
