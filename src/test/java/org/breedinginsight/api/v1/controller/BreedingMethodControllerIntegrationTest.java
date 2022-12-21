package org.breedinginsight.api.v1.controller;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.daos.impl.ProgramDAOImpl;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpRequest.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BreedingMethodControllerIntegrationTest extends BrAPITest {

    private FannyPack securityFp;
    private FannyPack brapiFp;
    private User testUser;

    @Inject
    private UserDAO userDAO;

    @Inject
    private ProgramDAO programDAO;

    @Inject
    private SpeciesDAO speciesDAO;

    @Inject
    private DSLContext dsl;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;


    @Inject
    private BrAPIListDAO brAPIListDAO;

    @Inject
    private ProgramService programService;

    @Inject
    private BrAPIGermplasmDAO germplasmDAO;

    @Inject
    private BrAPIGermplasmService germplasmService;

    @MockBean(BrAPIGermplasmDAO.class)
    BrAPIGermplasmService brAPIGermplasmService() {
        return spy(new BrAPIGermplasmService(brAPIListDAO, programService, germplasmDAO);
    }

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                                                 (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                                         .create();

    @BeforeAll
    void setup() throws Exception {
        // Create two programs with fanny pack
        securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());


    }

    private Program createProgram(String name, String abbv, String key) {
        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                                                      .commonName(validSpecies.getCommonName())
                                                      .id(validSpecies.getId())
                                                      .build();
        ProgramRequest programRequest1 = ProgramRequest.builder()
                                                       .name(name)
                                                       .abbreviation(abbv)
                                                       .documentationUrl("localhost:8080")
                                                       .objective("To test things")
                                                       .species(speciesRequest)
                                                       .key(key)
                                                       .build();


        TestUtils.insertAndFetchTestProgram(gson, client, programRequest1);

        // Get main program
        List<Program> programs = programDAO.getProgramByKey(key);
        Program program = programs.get(0);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId().toString());

        return program;
    }

    @Test
    public void fetchMethodsForNewProgram() {
        Program program = createProgram("newProgramBM", "BM", "NEWBM");
        String systemMethodsUrl = "/breeding-methods";
        Flowable<HttpResponse<String>> systemCall = client.exchange(
                GET(systemMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> systemResponse = systemCall.blockingFirst();
        assertEquals(HttpStatus.OK, systemResponse.getStatus());

        JsonObject systemResult = JsonParser.parseString(systemResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> systemMethods = gson.fromJson(systemResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        //fetch program breeding methods, and ensure that list matches list of system methods
        String programMethodsUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> programCall = client.exchange(
                GET(programMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> programResponse = programCall.blockingFirst();
        assertEquals(HttpStatus.OK, programResponse.getStatus());

        JsonObject programResult = JsonParser.parseString(programResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> programMethods = gson.fromJson(programResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        assertThat(programMethods.size(), equalTo(systemMethods.size()));

        assertThat(programMethods.stream()
                                               .map(ProgramBreedingMethodEntity::getId)
                                               .collect(Collectors.toList()),
                                 equalTo(systemMethods.stream()
                                                                          .map(ProgramBreedingMethodEntity::getId)
                                                                          .collect(Collectors.toList())));
    }

    @Test
    public void enableSystemMethods() {
        Program program = createProgram("enableProgramBM", "EBM", "ENBM");
        String systemMethodsUrl = "/breeding-methods";
        Flowable<HttpResponse<String>> systemCall = client.exchange(
                GET(systemMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> systemResponse = systemCall.blockingFirst();
        assertEquals(HttpStatus.OK, systemResponse.getStatus());

        JsonObject systemResult = JsonParser.parseString(systemResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> systemMethods = gson.fromJson(systemResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        List<UUID> enabledSystemMethods = new ArrayList<>();
        enabledSystemMethods.add(systemMethods.get(0).getId());

        String enableUrl = String.format("/programs/%s/breeding-methods/enable", program.getId());
        Flowable<HttpResponse<String>> enableCall = client.exchange(
                PUT(enableUrl, enabledSystemMethods).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> enableResponse = enableCall.blockingFirst();
        assertEquals(HttpStatus.OK, enableResponse.getStatus());

        //fetch program breeding methods, and ensure that list matches list of system methods
        String programMethodsUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> programCall = client.exchange(
                GET(programMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> programResponse = programCall.blockingFirst();
        assertEquals(HttpStatus.OK, programResponse.getStatus());

        JsonObject programResult = JsonParser.parseString(programResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> programMethods = gson.fromJson(programResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        assertThat(programMethods.size(), equalTo(1));
        assertThat(programMethods.get(0).getId(), equalTo(enabledSystemMethods.get(0)));
    }

    @Test
    public void createProgramMethod() {
        Program program = createProgram("createProgramBM", "CBM", "CRBM");
        String systemMethodsUrl = "/breeding-methods";
        Flowable<HttpResponse<String>> systemCall = client.exchange(
                GET(systemMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        ProgramBreedingMethodEntity method = ProgramBreedingMethodEntity.builder()
                                                                        .programId(program.getId())
                                                                        .name("test created method")
                                                                        .abbreviation("TCM")
                                                                        .category("Cross")
                                                                        .description("new method creation")
                                                                        .geneticDiversity("Generative (+)")
                                                                        .build();

        String createUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> createCall = client.exchange(
                POST(createUrl, method).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> createdResponse = createCall.blockingFirst();
        assertEquals(HttpStatus.OK, createdResponse.getStatus());

        JsonObject createdResult = JsonParser.parseString(createdResponse.body()).getAsJsonObject().getAsJsonObject("result");
        ProgramBreedingMethodEntity createdMethod = gson.fromJson(createdResult, ProgramBreedingMethodEntity.class);

        assertThat(createdMethod.getId(), is(notNullValue()));
        assertThat(createdMethod.getProgramId(), equalTo(program.getId()));
        assertThat(createdMethod.getName(), equalTo(method.getName()));
        assertThat(createdMethod.getAbbreviation(), equalTo(method.getAbbreviation()));
        assertThat(createdMethod.getCategory(), equalTo(method.getCategory()));
        assertThat(createdMethod.getDescription(), equalTo(method.getDescription()));
        assertThat(createdMethod.getGeneticDiversity(), equalTo(method.getGeneticDiversity()));

        //fetch program breeding methods, and ensure that list matches list of system methods
        String programMethodsUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> programCall = client.exchange(
                GET(programMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> programResponse = programCall.blockingFirst();
        assertEquals(HttpStatus.OK, programResponse.getStatus());

        JsonObject programResult = JsonParser.parseString(programResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> programMethods = gson.fromJson(programResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        assertThat(programMethods.size(), greaterThan(0));
        assertThat(programMethods.stream().map(ProgramBreedingMethodEntity::getId).collect(Collectors.toList()), hasItem(createdMethod.getId()));
    }

    @Test
    public void editProgramMethod() {
        Program program = createProgram("editProgramBM", "EBM", "EDBM");
        String systemMethodsUrl = "/breeding-methods";
        Flowable<HttpResponse<String>> systemCall = client.exchange(
                GET(systemMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        ProgramBreedingMethodEntity method = ProgramBreedingMethodEntity.builder()
                                                                        .programId(program.getId())
                                                                        .name("test edit method")
                                                                        .abbreviation("TEM")
                                                                        .category("Cross")
                                                                        .description("new method creation")
                                                                        .geneticDiversity("Generative (+)")
                                                                        .build();

        String createUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> createCall = client.exchange(
                POST(createUrl, method).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> createdResponse = createCall.blockingFirst();
        assertEquals(HttpStatus.OK, createdResponse.getStatus());

        JsonObject createdResult = JsonParser.parseString(createdResponse.body()).getAsJsonObject().getAsJsonObject("result");
        ProgramBreedingMethodEntity createdMethod = gson.fromJson(createdResult, ProgramBreedingMethodEntity.class);

        assertThat(createdMethod.getId(), is(notNullValue()));

        createdMethod.setName("Edited name");
        createdMethod.setAbbreviation("TEEM");
        createdMethod.setCategory("Unknown");
        createdMethod.setDescription("Edited description");
        createdMethod.setGeneticDiversity("Maintenance (0)");

        String updateUrl = String.format("/programs/%s/breeding-methods/%s", program.getId(), createdMethod.getId());
        Flowable<HttpResponse<String>> editCall = client.exchange(
                PUT(updateUrl, createdMethod).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> editResponse = editCall.blockingFirst();
        assertEquals(HttpStatus.OK, editResponse.getStatus());

        JsonObject editResult = JsonParser.parseString(editResponse.body()).getAsJsonObject().getAsJsonObject("result");
        ProgramBreedingMethodEntity editedMethod = gson.fromJson(editResult, ProgramBreedingMethodEntity.class);

        assertThat(editedMethod.getId(), equalTo(createdMethod.getId()));
        assertThat(editedMethod.getProgramId(), equalTo(program.getId()));
        assertThat(editedMethod.getName(), equalTo(createdMethod.getName()));
        assertThat(editedMethod.getAbbreviation(), equalTo(createdMethod.getAbbreviation()));
        assertThat(editedMethod.getCategory(), equalTo(createdMethod.getCategory()));
        assertThat(editedMethod.getDescription(), equalTo(createdMethod.getDescription()));
        assertThat(editedMethod.getGeneticDiversity(), equalTo(createdMethod.getGeneticDiversity()));
    }

    @Test
    public void deleteProgramMethod() {
        Program program = createProgram("deleteProgramBM", "DBM", "DELBM");
        String systemMethodsUrl = "/breeding-methods";
        Flowable<HttpResponse<String>> systemCall = client.exchange(
                GET(systemMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        ProgramBreedingMethodEntity method = ProgramBreedingMethodEntity.builder()
                                                                        .programId(program.getId())
                                                                        .name("test delete method")
                                                                        .abbreviation("TDM")
                                                                        .category("Cross")
                                                                        .description("method deletion")
                                                                        .geneticDiversity("Generative (+)")
                                                                        .build();

        String createUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> createCall = client.exchange(
                POST(createUrl, method).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> createdResponse = createCall.blockingFirst();
        assertEquals(HttpStatus.OK, createdResponse.getStatus());

        JsonObject createdResult = JsonParser.parseString(createdResponse.body()).getAsJsonObject().getAsJsonObject("result");
        ProgramBreedingMethodEntity createdMethod = gson.fromJson(createdResult, ProgramBreedingMethodEntity.class);

        assertThat(createdMethod.getId(), is(notNullValue()));

        String deleteUrl = String.format("/programs/%s/breeding-methods/%s", program.getId(), createdMethod.getId());
        Flowable<HttpResponse<String>> deleteCall = client.exchange(
                DELETE(deleteUrl).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> deleteResponse = deleteCall.blockingFirst();
        assertEquals(HttpStatus.OK, deleteResponse.getStatus());

        //fetch program breeding methods, and ensure that list does not contain the method just deleted
        String programMethodsUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> programCall = client.exchange(
                GET(programMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> programResponse = programCall.blockingFirst();
        assertEquals(HttpStatus.OK, programResponse.getStatus());

        JsonObject programResult = JsonParser.parseString(programResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> programMethods = gson.fromJson(programResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        assertThat(programMethods.size(), greaterThan(0));
        assertThat(programMethods.stream().map(ProgramBreedingMethodEntity::getId).collect(Collectors.toList()), not(hasItem(createdMethod.getId())));
    }

    @Test
    public void tryDeleteProgramMethodInUse() throws ApiException {
        Program program = createProgram("tryDeleteProgramBM", "TDBM", "TRYDBM");
        String systemMethodsUrl = "/breeding-methods";
        Flowable<HttpResponse<String>> systemCall = client.exchange(
                GET(systemMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        ProgramBreedingMethodEntity method = ProgramBreedingMethodEntity.builder()
                                                                        .programId(program.getId())
                                                                        .name("try delete method")
                                                                        .abbreviation("TRYDM")
                                                                        .category("Cross")
                                                                        .description("try method deletion")
                                                                        .geneticDiversity("Generative (+)")
                                                                        .build();

        String createUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> createCall = client.exchange(
                POST(createUrl, method).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> createdResponse = createCall.blockingFirst();
        assertEquals(HttpStatus.OK, createdResponse.getStatus());

        JsonObject createdResult = JsonParser.parseString(createdResponse.body()).getAsJsonObject().getAsJsonObject("result");
        ProgramBreedingMethodEntity createdMethod = gson.fromJson(createdResult, ProgramBreedingMethodEntity.class);

        assertThat(createdMethod.getId(), is(notNullValue()));

        BrAPIGermplasm germplasm = new BrAPIGermplasm()
                                       .breedingMethodDbId(createdMethod.getId().toString())
                .germplasmName("test germ")
                .accessionNumber("1");

        doReturn(List.of(germplasm)).when(germplasmService).getGermplasm(Mockito.any(UUID.class));

        String deleteUrl = String.format("/programs/%s/breeding-methods/%s", program.getId(), createdMethod.getId());
        Flowable<HttpResponse<String>> deleteCall = client.exchange(
                DELETE(deleteUrl).cookie(new NettyCookie("phylo-token", "test-registered-user"))
                , String.class);

        HttpResponse<String> deleteResponse = deleteCall.blockingFirst();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, deleteResponse.getStatus());

        //fetch program breeding methods, and ensure that list does contain the method just deleted
        String programMethodsUrl = String.format("/programs/%s/breeding-methods", program.getId());
        Flowable<HttpResponse<String>> programCall = client.exchange(
                GET(programMethodsUrl).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> programResponse = programCall.blockingFirst();
        assertEquals(HttpStatus.OK, programResponse.getStatus());

        JsonObject programResult = JsonParser.parseString(programResponse.body()).getAsJsonObject().getAsJsonObject("result");
        List<ProgramBreedingMethodEntity> programMethods = gson.fromJson(programResult.getAsJsonArray("data"), new TypeToken<List<ProgramBreedingMethodEntity>>(){}.getType());

        assertThat(programMethods.size(), greaterThan(0));
        assertThat(programMethods.stream().map(ProgramBreedingMethodEntity::getId).collect(Collectors.toList()), not(hasItem(createdMethod.getId())));
    }

    @Test
    public void tryDisableSystemMethodInUse() {
        Assertions.fail();
    }
}
