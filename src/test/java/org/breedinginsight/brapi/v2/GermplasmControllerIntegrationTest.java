package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapi.v2.model.response.mappers.GermplasmQueryMapper;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.utilities.response.mappers.FilterField;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GermplasmControllerIntegrationTest extends BrAPITest {

    private FannyPack fp;
    private Program validProgram;
    private Program otherValidProgram;
    private String germplasmImportId;
    private BiUserEntity testUser;

    @Inject
    private SpeciesService speciesService;
    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;
    @Inject
    private UserDAO userDAO;
    @Inject
    private DSLContext dsl;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();
    GermplasmQueryMapper germplasmQueryMapper = new GermplasmQueryMapper();

    private final String germplasmListName = "Program List";
    private final String germplasmListDesc = "Program List";

    @Inject
    private BrAPIGermplasmService germplasmService;
    @Inject
    private BrAPIListDAO listDAO;

    @AfterAll
    public void finish() { super.stopContainers(); }

    @BeforeAll
    @SneakyThrows
    public void setup() {
        fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Set up BrAPI
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        // Species
        Species validSpecies = speciesService.getAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        // Insert program
        ProgramRequest program = ProgramRequest.builder()
                .name("Test Program")
                .species(speciesRequest)
                .key("TEST")
                .build();
        validProgram = TestUtils.insertAndFetchTestProgram(gson, client, program);

        // Insert other program
        ProgramRequest otherProgram = ProgramRequest.builder()
                .name("Other Test Program")
                .species(speciesRequest)
                .key("OTEST")
                .build();
        otherValidProgram = TestUtils.insertAndFetchTestProgram(gson, client, otherProgram);

        // Get germplasm system import
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=germplasmtemplatemap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        germplasmImportId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Roles for program
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId());
        // Roles for other program
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), otherValidProgram.getId());

        // Insert other program germplasm
        File otherFile = new File("src/test/resources/files/germplasm_import/minimal_germplasm_import.csv");
        Map<String, String> otherListInfo = Map.ofEntries(
                Map.entry("germplasmListName", germplasmListName),
                Map.entry("germplasmListDescription", germplasmListDesc)
        );
        TestUtils.uploadDataFile(client, otherValidProgram.getId(), germplasmImportId, otherListInfo, otherFile);

        // Insert program germplasm
        File file = new File("src/test/resources/files/germplasm_import/full_import.csv");
        Map<String, String> germplasmListInfo = Map.ofEntries(
                Map.entry("germplasmListName", germplasmListName),
                Map.entry("germplasmListDescription", germplasmListDesc)
        );
        Map<String, String> germplasmListInfo1 = Map.ofEntries(
                Map.entry("germplasmListName", "Program List1"),
                Map.entry("germplasmListDescription", "Program List1")
        );
        TestUtils.uploadDataFile(client, validProgram.getId(), germplasmImportId, germplasmListInfo1, otherFile);
        TestUtils.uploadDataFile(client, validProgram.getId(), germplasmImportId, germplasmListInfo, file);
    }

    @Test
    @SneakyThrows
    public void getAllSuccess() {
        // Only program germplasm are returned
        // Names are updated
        // TODO: Parents are updated
        // Breeding method is assigned
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/germplasm",validProgram.getId().toString()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(6, data.size(), "Wrong number of germplasm were returned");
        for (JsonElement jsonGermplasm: data) {
            JsonObject exampleGermplasm = jsonGermplasm.getAsJsonObject();
            assertEquals(exampleGermplasm.get("defaultDisplayName").getAsString(), exampleGermplasm.get("germplasmName").getAsString(), "Germplasm name was not set to display name");
            if (exampleGermplasm.get("additionalInfo").getAsJsonObject().has("breedingMethodId")){
                assertEquals(exampleGermplasm.getAsJsonObject("additionalInfo").get("breedingMethodId").getAsString(), exampleGermplasm.get("breedingMethodDbId").getAsString(), "Breeding method id was not set from additional info");
            }
            if (exampleGermplasm.has("pedigree")) {
                String pedigree = exampleGermplasm.get("pedigree").getAsString();
                assertDoesNotThrow(() -> Integer.parseInt(pedigree.split("/")[0]));
                if (pedigree.split("/").length > 1) {
                    assertDoesNotThrow(() -> Integer.parseInt(pedigree.split("/")[1]));
                }
            }

        }
    }

    @Test
    @SneakyThrows
    public void getPaginatedSuccess() {
        // Pagination
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/germplasm?page=0&pageSize=1",validProgram.getId().toString()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(1, data.size(), "Wrong number of germplasm were returned");
    }

    @Test
    @SneakyThrows
    public void getAllGermplasmListsSuccess() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/lists",validProgram.getId().toString()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(2, data.size(), "Wrong number of germplasm lists were returned");
        List<String> listNames = List.of(germplasmListName, "Program List1");
        List<String> listDescription = List.of(germplasmListDesc, "Program List1");
        for (JsonElement element: data) {
            JsonObject exampleGermplasmList = element.getAsJsonObject();
            if (listNames.contains(exampleGermplasmList.get("listName").getAsString())) {
                Integer ind = listNames.indexOf(exampleGermplasmList.get("listName").getAsString());
                assertEquals(exampleGermplasmList.get("listName").getAsString(), listNames.get(ind), "Germplasm list name incorrect");
                assertEquals(exampleGermplasmList.get("listDescription").getAsString(), listDescription.get(ind), "Germplasm list description incorrect");
                if (ind == 0) {
                    assertEquals(exampleGermplasmList.get("listSize").getAsInt(), 3, "Germplasm list displays incorrect total entries ");
                }
            } else {
                throw new AssertionFailedError("List name not found");
            }
        }
    }

    @Test
    @SneakyThrows
    public void filterGermplasmNameSuccess() {

        SearchRequest searchRequest = constructSearchRequest(
                List.of("germplasmName", "synonyms"),
                List.of("Full", "test1"));
        JsonArray data = callFilterGermplasm(searchRequest);

        assertEquals(1, data.size(), "Wrong number of germplasm were returned");
        JsonObject germplasm = data.get(0).getAsJsonObject();
        assertEquals("Full Germplasm 1", germplasm.get("germplasmName").getAsString());
    }

    @Test
    @SneakyThrows
    public void filterGermplasmBreedingMethodSuccess() {

        SearchRequest searchRequest = constructSearchRequest(
                List.of("breedingMethod", "germplasmName"),
                List.of("Aneupoly", "Germplasm 1"));
        JsonArray data = callFilterGermplasm(searchRequest);

        assertEquals(1, data.size(), "Wrong number of germplasm were returned");
        JsonObject germplasm = data.get(0).getAsJsonObject();
        assertEquals("Full Germplasm 1", germplasm.get("germplasmName").getAsString());
    }

    @Test
    @SneakyThrows
    public void filterGermplasmSourceSuccess() {

        SearchRequest searchRequest = constructSearchRequest(
                List.of("source"),
                List.of("cultivate"));
        JsonArray data = callFilterGermplasm(searchRequest);

        assertEquals(1, data.size(), "Wrong number of germplasm were returned");
        JsonObject germplasm = data.get(0).getAsJsonObject();
        assertEquals("Germplasm 2", germplasm.get("germplasmName").getAsString());
    }

    @Test
    @SneakyThrows
    public void filterGermplasmFemaleParentGIDSuccess() {

        SearchRequest searchRequest = constructSearchRequest(
                List.of("femaleParentGID"),
                List.of("2"));
        JsonArray data = callFilterGermplasm(searchRequest);

        assertEquals(1, data.size(), "Wrong number of germplasm were returned");
        JsonObject germplasm = data.get(0).getAsJsonObject();
        assertEquals("Full Germplasm 2", germplasm.get("germplasmName").getAsString());
    }

    @Test
    @SneakyThrows
    public void filterGermplasmMaleParentGIDSuccess() {

        SearchRequest searchRequest = constructSearchRequest(
                List.of("maleParentGID"),
                List.of("2"));
        JsonArray data = callFilterGermplasm(searchRequest);

        assertEquals(1, data.size(), "Wrong number of germplasm were returned");
        JsonObject germplasm = data.get(0).getAsJsonObject();
        assertEquals("Full Germplasm 1", germplasm.get("germplasmName").getAsString());
    }

    @Test
    @SneakyThrows
    public void filterGermplasmOneMatchOneNoMatch() {

        SearchRequest searchRequest = constructSearchRequest(
                List.of("germplasmName", "synonyms"),
                List.of("No Match", "test1"));
        JsonArray data = callFilterGermplasm(searchRequest);

        assertEquals(0, data.size(), "Wrong number of germplasm were returned");
    }

    public JsonArray callFilterGermplasm(SearchRequest searchRequest) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(String.format("/programs/%s/brapi/v2/germplasm/search",validProgram.getId().toString()), gson.toJson(searchRequest))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        return result.getAsJsonArray("data");
    }

    public SearchRequest constructSearchRequest(List<String> fields, List<String> values) {
        SearchRequest searchRequest = new SearchRequest();
        List<FilterRequest> filters = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            filters.add(new FilterRequest(fields.get(i), values.get(i)));
        }
        searchRequest.setFilters(filters);
        return searchRequest;
    }
}
