package org.breedinginsight.brapps.importer;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.processors.GermplasmProcessor;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.SpeciesService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpRequest.PUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GermplasmTemplateMap extends BrAPITest {

    private FannyPack fp;
    private Program validProgram;
    private String germplasmImportId;
    private BiUserEntity testUser;

    @Inject
    private SpeciesService speciesService;
    @Inject
    private UserDAO userDAO;
    @Inject
    private DSLContext dsl;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    public void setup() {
        fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

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
        validProgram = insertAndFetchTestProgram(program);

        // Get germplasm system import
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=germplasmtemplatemap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        germplasmImportId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId());
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());
    }

    public Program insertAndFetchTestProgram(ProgramRequest programRequest) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/", gson.toJson(programRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        Program program = gson.fromJson(result, Program.class);
        return program;
    }

    // Done
    // Minimum import required fields, success
    // Female parent not exist db id, throw error
    // Female parent not exist entry number, throw error
    // Male parent not exist db id, throw error
    // Female parent not exist entry number, throw error
    // Bad breeding method, throw error

    // TODO
    // No entry numbers, automatic generation
    // Some entry numbers, throw an error
    // Required fields missing, throw error
    // Missing required headers
    // Full import, success
    // Preview, non-preview fields not shown
    // Male db, no female db id, pedigree string is null

    @Test
    @SneakyThrows
    @Order(1)
    public void minimalImportSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/minimal_germplasm_import.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());
    }

    @Test
    @SneakyThrows
    public void femaleParentDbIdNotExistError() {
        File file = new File("src/test/resources/files/germplasm_import/female_dbid_not_exist.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        List<String> missingDbIds = List.of("1000", "1001", "1002");
        assertEquals(String.format(GermplasmProcessor.missingParentalDbIdsMsg, GermplasmProcessor.arrayOfStringFormatter.apply(missingDbIds)),
                result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void femaleParentEntryNumberNotExistError() {
        File file = new File("src/test/resources/files/germplasm_import/female_entry_number_not_exist.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        List<String> missingDbIds = List.of("1", "2", "3");
        assertEquals(String.format(GermplasmProcessor.missingParentalEntryNoMsg, GermplasmProcessor.arrayOfStringFormatter.apply(missingDbIds)),
                result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void maleParentDbIdNotExistError() {
        File file = new File("src/test/resources/files/germplasm_import/male_dbid_not_exist.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        List<String> missingDbIds = List.of("100", "101", "102");
        assertEquals(String.format(GermplasmProcessor.missingParentalDbIdsMsg, GermplasmProcessor.arrayOfStringFormatter.apply(missingDbIds)),
                result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void maleParentEntryNumberNotExistError() {
        File file = new File("src/test/resources/files/germplasm_import/male_entry_number_not_exist.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        List<String> missingDbIds = List.of("1", "2", "3");
        assertEquals(String.format(GermplasmProcessor.missingParentalEntryNoMsg, GermplasmProcessor.arrayOfStringFormatter.apply(missingDbIds)),
                result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void badBreedingMethods() {
        File file = new File("src/test/resources/files/germplasm_import/bad_breeding_methods.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        List<String> badBreedingMethods = List.of("BAD", "BAD1");
        assertEquals(String.format(GermplasmProcessor.badBreedMethodsMsg, GermplasmProcessor.arrayOfStringFormatter.apply(badBreedingMethods)),
                result.getAsJsonObject("progress").get("message").getAsString());

    }

    public Flowable<HttpResponse<String>> uploadDataFile(File file, Boolean commit) {
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmImportId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String importId = result.get("importId").getAsString();

        // Process data
        String url = String.format("/programs/%s/import/mappings/%s/data/%s/%s", validProgram.getId(), germplasmImportId, importId, commit ? "commit" : "preview");
        Flowable<HttpResponse<String>> processCall = client.exchange(
                PUT(url, new HashMap<>())
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        return processCall;
    }

    public HttpResponse<String> getUploadedFile(String importId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/import/mappings/%s/data/%s", validProgram.getId(), germplasmImportId, importId))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        if (response.getStatus().equals(HttpStatus.ACCEPTED)) {
            Thread.sleep(1000);
            return getUploadedFile(importId);
        } else {
            return response;
        }


    }
}
