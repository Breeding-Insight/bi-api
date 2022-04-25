package org.breedinginsight.brapps.importer;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.MappingManager;
import org.breedinginsight.brapps.importer.services.processors.GermplasmProcessor;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.SpeciesService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpRequest.PUT;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GermplasmTemplateMap extends BrAPITest {

    private FannyPack fp;
    private Program validProgram;
    private String germplasmImportId;
    private BiUserEntity testUser;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    @Property(name = "brapi.server.core-url")
    private String BRAPI_URL;

    @Inject
    private SpeciesService speciesService;
    @Inject
    private UserDAO userDAO;
    @Inject
    private BreedingMethodDAO breedingMethodDAO;
    @Inject
    private DSLContext dsl;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @AfterAll
    public void finish() {
        super.stopContainers();
    }

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    // TODO: Tests to check
    // Name is required when saving a mapping
    // getMappings only returns saved mappings
    // getDetails allows you to get unsaved mappings
    // Mapping name is ignored when mapping isn't saved

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

    // Tests
    // Minimum import required fields, success
    // Female parent not exist db id, throw error
    // Female parent not exist entry number, throw error
    // Male parent not exist db id, throw error
    // Female parent not exist entry number, throw error
    // Bad breeding method, throw error
    // Some entry numbers, throw an error
    // Numerical entry numbers
    // Required fields missing, throw error
    // Missing required headers
    // Missing optional headers
    // No entry numbers, automatic generation
    // Full import, success
    // Preview, non-preview fields not shown
    // Male db, no female db id, pedigree string is null
    // Circular parent dependency
    // Name and description success
    // Name only success
    // Dup list name test
    // Missing required fields tests

    @Test
    @SneakyThrows
    @Order(1)
    public void minimalImportUserSpecifiedEntryNumbersSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/minimal_germplasm_import.csv");
        String listName = "MinimalList";
        String listDescription = null;
        Flowable<HttpResponse<String>> call = uploadDataFile(file, listName, listDescription, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        List<String> germplasmNames = new ArrayList<>();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            germplasmNames.add(germplasm.get("germplasmName").getAsString());
            assertEquals(Integer.toString(i+1), germplasm.getAsJsonObject("additionalInfo").get("importEntryNumber").getAsString(), "Wrong entry number");
        }

        // Check the germplasm list
        checkGermplasmList(Germplasm.constructGermplasmListName(listName, validProgram), listDescription, germplasmNames);
    }

    @Test
    @SneakyThrows
    @Order(2)
    public void fullImportPreviewSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/full_import.csv");
        Table fileData = Table.read().file("src/test/resources/files/germplasm_import/full_import.csv");
        String listName = "FullList";
        String listDescription = "A full import";

        Flowable<HttpResponse<String>> call = uploadDataFile(file, listName, listDescription, false);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        List<String> femaleParents = List.of("Germplasm 1", "Germplasm 2", "Full Germplasm 1");
        List<String> maleParents = List.of("Germplasm 2", "", "Full Germplasm 2");
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            checkBasicResponse(germplasm, fileData, i);

            // Check preview specific items
            // Germplasm name (display name)
            assertEquals(fileData.getString(i, "Name"), germplasm.get("germplasmName").getAsString());
            JsonObject additionalInfo = germplasm.getAsJsonObject("additionalInfo");
            // Created Date (not present)
            assertTrue(!additionalInfo.has("createdDate"), "createdDate is present, but should not be");
            // Accession Number (not present)
            assertTrue(!germplasm.has("accessionNumber"), "accessionNumber is present, but should not be");
            // Pedigree (display names)
            String pedigree = germplasm.get("pedigree").getAsString();
            String mother = !pedigree.isBlank() ? pedigree.split("/")[0] : null;
            String father = !pedigree.isBlank() && pedigree.split("/").length > 1 ? pedigree.split("/")[1] : null;
            assertEquals(femaleParents.get(i), mother, "Wrong mother");
            if (!maleParents.get(i).isBlank()) {
                assertEquals(maleParents.get(i), father, "Wrong father");
            } else {
                assertNull(father, "Wrong father");
            }

            // External Reference germplasm (not present)
            JsonArray externalReferences = germplasm.getAsJsonArray("externalReferences");
            for (JsonElement reference: externalReferences) {
                String referenceSource = reference.getAsJsonObject().get("referenceSource").getAsString();
                assertTrue(referenceSource != BRAPI_REFERENCE_SOURCE, "Germplasm UUID was present, but should not be");
            }
        }
    }

    @Test
    @SneakyThrows
    @Order(3)
    public void fullImportCommitSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/full_import.csv");
        Table fileData = Table.read().file("src/test/resources/files/germplasm_import/full_import.csv");

        String listName = "FullList";
        String listDescription = "A full import";
        Flowable<HttpResponse<String>> call = uploadDataFile(file, listName, listDescription, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        List<String> femaleParents = List.of("Germplasm 1", "Germplasm 2", "Full Germplasm 1");
        List<String> maleParents = List.of("Germplasm 2", "", "Full Germplasm 2");
        List<String> germplasmNames = new ArrayList<>();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            germplasmNames.add(germplasm.get("germplasmName").getAsString());
            checkBasicResponse(germplasm, fileData, i);

            // Check commit specific items
            // Germplasm name (display name)
            String expectedGermplasmName = String.format("%s [%s-%s]", fileData.getString(i, "Name"), validProgram.getKey(), germplasm.get("accessionNumber").getAsString());
            assertEquals(expectedGermplasmName, germplasm.get("germplasmName").getAsString());
            // Created Date
            JsonObject additionalInfo = germplasm.getAsJsonObject("additionalInfo");
            assertTrue(additionalInfo.has("createdDate"), "createdDate is missing");
            // Accession Number
            assertTrue(germplasm.has("accessionNumber"), "accessionNumber missing");
            // Pedigree (germplasm names)
            String pedigree = germplasm.get("pedigree").getAsString();
            String mother = !pedigree.isBlank() ? pedigree.split("/")[0] : null;
            String father = !pedigree.isBlank() && pedigree.split("/").length > 1 ? pedigree.split("/")[1] : null;
            String regexMatcher = "^(.*\\b) \\[([A-Z]{2,6})-(\\d+)\\]$";
            assertTrue(mother.matches(String.format(regexMatcher, femaleParents.get(i))), "Wrong mother");
            if (!maleParents.get(i).isBlank()) {
                assertTrue(father.matches(String.format(regexMatcher, maleParents.get(i))), "Wrong father");
            } else {
                assertNull(father, "Wrong father");
            }

            // External Reference germplasm
            JsonArray externalReferences = germplasm.getAsJsonArray("externalReferences");
            Boolean referenceFound = false;
            for (JsonElement reference: externalReferences) {
                String referenceSource = reference.getAsJsonObject().get("referenceSource").getAsString();
                if (referenceSource.equals(BRAPI_REFERENCE_SOURCE)) {
                    referenceFound = true;
                    break;
                }
            }
            assertTrue(referenceFound, "Germplasm UUID reference not found");
        }

        // Check the germplasm list
        checkGermplasmList(Germplasm.constructGermplasmListName(listName, validProgram), listDescription, germplasmNames);
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void duplicateNameMarksDuplicates() {
        File file = new File("src/test/resources/files/germplasm_import/duplicate_db_names.csv");

        String listName = "DupNamesList";
        String listDescription = "A full import";
        Flowable<HttpResponse<String>> call = uploadDataFile(file, listName, listDescription, false);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        // All should be marked duplicates
        List<Integer> duplicateIndex = List.of(0,1,2,4,5);
        for (int i = 0; i < previewRows.size(); i++) {
            String state = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").get("state").getAsString();
            if (duplicateIndex.contains(i)) {
                assertEquals(ImportObjectState.EXISTING.name(), state, "Wrong state returned");
            } else {
                assertEquals(ImportObjectState.NEW.name(), state, "Wrong state returned");
            }
        }
    }

    @Test
    @SneakyThrows
    public void NoFemaleParentBlankPedigreeStringSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/no_female_parent_blank_pedigree.csv");

        Flowable<HttpResponse<String>> call = uploadDataFile(file, "NoFemaleParentList", null, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            assertTrue(!germplasm.has("pedigree"), "Pedigree string is populated, but should be empty");
        }
    }

    @Test
    @SneakyThrows
    public void missingRequiredUserInput() {
        File file = new File("src/test/resources/files/germplasm_import/female_dbid_not_exist.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, null, null,true);
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(String.format(MappingManager.missingUserInput, "List Name"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void germplasmListNameDuplicateError() {
        File file = new File("src/test/resources/files/germplasm_import/full_import.csv");
        Table fileData = Table.read().file("src/test/resources/files/germplasm_import/full_import.csv");

        String listName = "FullList";
        String listDescription = "A full import";
        Flowable<HttpResponse<String>> call = uploadDataFile(file, listName, listDescription, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();
        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.listNameAlreadyExists, result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void femaleParentDbIdNotExistError() {
        File file = new File("src/test/resources/files/germplasm_import/female_dbid_not_exist.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
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
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
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
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
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
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
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
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray errorList = result
                .getAsJsonObject("progress")
                .getAsJsonArray("rowErrors");

        assertEquals(2, errorList.size());

        JsonObject firstError = errorList
                .get(0).getAsJsonObject()
                .getAsJsonArray("errors").get(0).getAsJsonObject();
        assertEquals("Invalid breeding method", firstError.get("errorMessage").getAsString());
        assertEquals("Breeding Method",         firstError.get("field").getAsString());
    }

    @Test
    @SneakyThrows
    public void someEntryNumbersError() {
        File file = new File("src/test/resources/files/germplasm_import/some_entry_numbers.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.missingEntryNumbersMsg, result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void duplicateEntryNumbersError() {
        File file = new File("src/test/resources/files/germplasm_import/duplicate_entry_numbers.csv");
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        List<String> dups = List.of("1", "3");
        assertEquals(String.format(GermplasmProcessor.duplicateEntryNoMsg, GermplasmProcessor.arrayOfStringFormatter.apply(dups)),
                result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void nonNumericEntryNumbersError() {
        File file = new File("src/test/resources/files/germplasm_import/nonnumerical_entry_numbers.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmImportId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(String.format(MappingManager.wrongDataTypeMsg, "Entry No"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void emptyRequiredFieldsError() {
        File file = new File("src/test/resources/files/germplasm_import/empty_required_fields.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmImportId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertTrue(rowErrors.size() == 2, "Wrong number of row errors returned");

        JsonObject rowError1 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError1.getAsJsonArray("errors");
        assertTrue(errors.size() == 1, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(422, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertEquals("Name", error.get("field").getAsString(), "Incorrect field name");
        assertEquals(String.format(MappingManager.blankRequiredField, "Name"), error.get("errorMessage").getAsString(), "Incorrect error message");

        JsonObject rowError2 = rowErrors.get(1).getAsJsonObject();
        JsonArray errors2 = rowError2.getAsJsonArray("errors");
        assertTrue(errors2.size() == 1, "Not enough errors were returned");
        JsonObject error2 = errors2.get(0).getAsJsonObject();
        assertEquals(422, error2.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertEquals("Source", error2.get("field").getAsString(), "Incorrect field name");
        assertEquals(String.format(MappingManager.blankRequiredField, "Source"), error2.get("errorMessage").getAsString(), "Incorrect error message");
    }

    @Test
    @SneakyThrows
    public void headerCaseInsensitive() {
        File file = new File("src/test/resources/files/germplasm_import/germplasm_column_casing.csv");
        String listName = "CaseInsensitiveList";
        String listDescription = null;
        Flowable<HttpResponse<String>> call = uploadDataFile(file, listName, listDescription, true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        List<String> germplasmNames = new ArrayList<>();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            germplasmNames.add(germplasm.get("germplasmName").getAsString());
            assertEquals(Integer.toString(i+1), germplasm.getAsJsonObject("additionalInfo").get("importEntryNumber").getAsString(), "Wrong entry number");
        }

        // Check the germplasm list
        checkGermplasmList(Germplasm.constructGermplasmListName(listName, validProgram), listDescription, germplasmNames);
    }

    @Test
    @SneakyThrows
    public void missingRequiredFieldHeaderError() {
        File file = new File("src/test/resources/files/germplasm_import/missing_required_header.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmImportId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(String.format(MappingManager.missingColumn, "Source"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void missingOptionalFieldHeaderError() {
        File file = new File("src/test/resources/files/germplasm_import/missing_optional_header.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmImportId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(String.format(MappingManager.missingColumn, "Entry No"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void circularParentDependencyError() {
        File file = new File("src/test/resources/files/germplasm_import/circular_parent_dependencies.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.circularDependency, result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void selfReferenceParentError() {
        File file = new File("src/test/resources/files/germplasm_import/self_ref_parent_dependencies.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();
        Flowable<HttpResponse<String>> call = uploadDataFile(file, "Bad List", null,true);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.circularDependency, result.getAsJsonObject("progress").get("message").getAsString());
    }

    public Flowable<HttpResponse<String>> uploadDataFile(File file, String listName, String listDescription, Boolean commit) {
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
        Map<String, String> listBody = new HashMap<>();
        listBody.put("germplasmListName", listName);
        listBody.put("germplasmListDescription", listDescription);
        Flowable<HttpResponse<String>> processCall = client.exchange(
                PUT(url, listBody)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        return processCall;
    }

    public HttpResponse<String> getUploadedFile(String importId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/import/mappings/%s/data/%s?mapping=true", validProgram.getId(), germplasmImportId, importId))
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


    public void checkBasicResponse(JsonObject germplasm, Table fileData, Integer i) {

        // Germplasm display name
        assertEquals(fileData.getString(i, "Name"), germplasm.get("defaultDisplayName").getAsString(), "Wrong display name");
        // Entry Number
        JsonObject additionalInfo = germplasm.getAsJsonObject("additionalInfo");
        assertEquals(fileData.getString(i, "Entry No"), additionalInfo.get("importEntryNumber").getAsString(), "Wrong entry number");
        // Created By User ID
        assertEquals(testUser.getId().toString(), additionalInfo.getAsJsonObject("createdBy").get("userId").getAsString(), "Wrong createdBy userId");
        // Created by User name
        assertEquals(testUser.getName(), additionalInfo.getAsJsonObject("createdBy").get("userName").getAsString(), "Wrong createdBy userId");
        // Breeding Method
        String breedingMethodName = fileData.getString(i, "Breeding Method");
        BreedingMethodEntity breedingMethod = breedingMethodDAO.findByNameOrAbbreviation(breedingMethodName).get(0);
        assertEquals(breedingMethod.getId().toString(), additionalInfo.get("breedingMethodId").getAsString(), "Wrong Breeding Method ID");
        assertEquals(breedingMethod.getName(), additionalInfo.get("breedingMethod").getAsString(), "Wrong Breeding Method name");
        // Seed source
        assertEquals(fileData.getString(i, "Source"), germplasm.get("seedSource").getAsString(), "Wrong seed source");
        // External Reference (user specified)
        // External Reference program
        JsonArray externalReferences = germplasm.getAsJsonArray("externalReferences");
        Map<String, String> expectedReferences = new HashMap<>();
        expectedReferences.put(String.format("%s/programs", BRAPI_REFERENCE_SOURCE), validProgram.getId().toString());
        expectedReferences.put(fileData.getString(i, "Source"), fileData.getString(i, "External UID"));
        Integer referencesFound = 0;
        for (JsonElement reference: externalReferences) {
            String referenceSource = reference.getAsJsonObject().get("referenceSource").getAsString();
            String referenceID = reference.getAsJsonObject().get("referenceID").getAsString();
            if (expectedReferences.containsKey(referenceSource)) {
                assertEquals(expectedReferences.get(referenceSource), referenceID);
                referencesFound += 1;
            }
        }
        assertEquals(expectedReferences.size(), referencesFound, "Not all expected references were returned");
    }

    public void checkGermplasmList(String listName, String listDescription, List<String> germplasmNames) {
        String url = String.format("%sbrapi/v2/lists", BRAPI_URL);
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        Boolean nameFound = false;
        Boolean descriptionFound = false;
        String listId = null;
        for (JsonElement listElement: data) {
            JsonObject listObject = listElement.getAsJsonObject();
            if (listObject.get("listName").getAsString().equals(listName)) {
                nameFound = true;
                listId = listObject.get("listDbId").getAsString();
                assertEquals(BrAPIListTypes.GERMPLASM.toString(), listObject.get("listType").getAsString(), "Wrong list type");
            }
            if (listObject.has("listDescription") && !listObject.get("listDescription").isJsonNull()
                    && listObject.get("listDescription").getAsString().equals(listDescription)) {
                descriptionFound = true;
            }
        }

        // Check
        assertTrue(nameFound, "List was not found in the brapi service");
        if (listDescription != null) {
            assertTrue(descriptionFound, "List description was not found in the brapi service.");
        }

        // Check that the germplasm names were saved
        if (listId != null) {
            String detailsUrl = String.format("%sbrapi/v2/lists/%s", BRAPI_URL, listId);
            Flowable<HttpResponse<String>> detailCall = client.exchange(
                    GET(detailsUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
            );
            HttpResponse<String> detailResponse = detailCall.blockingFirst();
            JsonObject detailResult = JsonParser.parseString(detailResponse.body()).getAsJsonObject().getAsJsonObject("result");
            JsonArray germplasmList = detailResult.getAsJsonArray("data");
            List<Boolean> found = new ArrayList<>();
            for (String germplasmName: germplasmNames) {
                for (JsonElement listElement: germplasmList) {
                    if (listElement.getAsString().equals(germplasmName)) {
                        found.add(true);
                        break;
                    }
                }
            }
            assertEquals(germplasmNames.size(), germplasmList.size(), "Wrong number of germplasm found in the list");
            assertTrue(!found.contains(false), "Some germplasm names were not found in saved list");
        }

    }

}
