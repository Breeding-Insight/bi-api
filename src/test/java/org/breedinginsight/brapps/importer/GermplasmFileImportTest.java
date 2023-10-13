package org.breedinginsight.brapps.importer;

import com.google.gson.*;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.imports.germplasm.GermplasmImportService;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.GermplasmProcessor;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.utilities.Utilities;
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
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GermplasmFileImportTest extends BrAPITest {

    private static final String GERM_LIST_NAME = "germplasmListName";
    private static final String GERM_LIST_DESC = "germplasmListDescription";
    private Program validProgram;
    private String germplasmMappingId;
    private BiUserEntity testUser;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    @Property(name = "brapi.server.core-url")
    private String BRAPI_URL;

    @Inject
    private GermplasmImportService importService;
    @Inject
    private SpeciesService speciesService;
    @Inject
    private UserDAO userDAO;
    @Inject
    private BreedingMethodDAO breedingMethodDAO;
    @Inject
    private DSLContext dsl;

    private ImportTestUtils importTestUtils;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                                               .create();

    @BeforeAll
    public void setup() {
        importTestUtils = new ImportTestUtils();
        Map<String, Object> setupObjects = importTestUtils.setup(client, gson, dsl, speciesService, userDAO, super.getBrapiDsl(), "GermplasmTemplateMap");
        validProgram = (Program) setupObjects.get("program");
        germplasmMappingId = (String) setupObjects.get("mappingId");
        testUser = (BiUserEntity) setupObjects.get("testUser");
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, listName), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        List<String> germplasmNames = new ArrayList<>();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            germplasmNames.add(germplasm.get("germplasmName").getAsString());
            int finalI = i;
            gson.fromJson(germplasm.getAsJsonObject("additionalInfo").getAsJsonObject("listEntryNumbers"), Map.class).forEach((listId, entryNumber) -> assertEquals(Integer.toString(finalI +1), entryNumber, "Wrong entry number"));
        }

        // Check the germplasm list
        checkGermplasmList(Germplasm.constructGermplasmListName(listName, validProgram), null, germplasmNames);
    }

    @Test
    @SneakyThrows
    @Order(2)
    public void fullImportPreviewSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/full_import.csv");
        Table fileData = Table.read().file("src/test/resources/files/germplasm_import/full_import.csv");
        String listName = "FullList";
        String listDescription = "A full import";

        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, listName, GERM_LIST_DESC, listDescription), false, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
            assertFalse(additionalInfo.has(BrAPIAdditionalInfoFields.CREATED_DATE), "createdDate is present, but should not be");
            // Accession Number (not present)
            assertFalse(germplasm.has("accessionNumber"), "accessionNumber is present, but should not be");
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
                assertNotSame(referenceSource, BRAPI_REFERENCE_SOURCE, "Germplasm UUID was present, but should not be");
            }

            // Synonyms
            JsonArray synonyms = germplasm.getAsJsonArray("synonyms");
            for (JsonElement synonym: synonyms) {
                String synonymName = synonym.getAsJsonObject().get("synonym").getAsString();
                assertNotNull(synonymName);
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, listName, GERM_LIST_DESC, listDescription), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
            assertTrue(additionalInfo.has(BrAPIAdditionalInfoFields.CREATED_DATE), "createdDate is missing");
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
            boolean referenceFound = false;
            for (JsonElement reference: externalReferences) {
                String referenceSource = reference.getAsJsonObject().get("referenceSource").getAsString();
                if (referenceSource.equals(BRAPI_REFERENCE_SOURCE)) {
                    referenceFound = true;
                    break;
                }
            }
            assertTrue(referenceFound, "Germplasm UUID reference not found");

            // Synonyms
            String[] splitGermplasmName = germplasm.get("germplasmName").getAsString().split(" ");
            String scope = splitGermplasmName[splitGermplasmName.length - 1];
            JsonArray synonyms = germplasm.getAsJsonArray("synonyms");
            for (JsonElement synonym: synonyms) {
                String synonymName = synonym.getAsJsonObject().get("synonym").getAsString();
                assertNotNull(synonymName);
                assertTrue(synonymName.contains(scope), "Germplasm synonym was not properly scoped");
            }
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, listName, GERM_LIST_DESC, listDescription), false, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
    public void OnlyMaleParentPreviewSuccess() {
        File file = new File("src/test/resources/files/germplasm_import/no_female_parent_blank_pedigree.csv");

        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "NoFemaleParentList"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            assertTrue(germplasm.has("pedigree"), "Pedigree string should be populated, but is empty");
            assertTrue(germplasm.get("pedigree").getAsString().substring(1).length() > 0, "Male pedigree string should be populated, but is empty");
        }
    }

    @Test
    @SneakyThrows
    public void missingRequiredUserInput() {
        File file = new File("src/test/resources/files/germplasm_import/female_dbid_not_exist.csv");
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(), true, client, validProgram, germplasmMappingId);
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(importService.getMissingUserInputMsg("List Name"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void germplasmListNameDuplicateError() {
        File file = new File("src/test/resources/files/germplasm_import/full_import.csv");
        Table fileData = Table.read().file("src/test/resources/files/germplasm_import/full_import.csv");

        String listName = "FullList";
        String listDescription = "A full import";
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, listName, GERM_LIST_DESC, listDescription), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();
        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.listNameAlreadyExists, result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void femaleParentDbIdNotExistError() {
        File file = new File("src/test/resources/files/germplasm_import/female_dbid_not_exist.csv");
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.missingEntryNumbersMsg, result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void duplicateEntryNumbersError() {
        File file = new File("src/test/resources/files/germplasm_import/duplicate_entry_numbers.csv");
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
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
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmMappingId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(importService.getWrongDataTypeMsg("Entry No"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void emptyRequiredFieldsError() {
        File file = new File("src/test/resources/files/germplasm_import/empty_required_fields.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmMappingId);
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
        assertEquals(2, rowErrors.size(), "Wrong number of row errors returned");

        JsonObject rowError1 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError1.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(422, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertEquals("Name", error.get("field").getAsString(), "Incorrect field name");
        assertEquals(importService.getBlankRequiredFieldMsg("Name"), error.get("errorMessage").getAsString(), "Incorrect error message");

        JsonObject rowError2 = rowErrors.get(1).getAsJsonObject();
        JsonArray errors2 = rowError2.getAsJsonArray("errors");
        assertEquals(1, errors2.size(), "Not enough errors were returned");
        JsonObject error2 = errors2.get(0).getAsJsonObject();
        assertEquals(422, error2.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertEquals("Source", error2.get("field").getAsString(), "Incorrect field name");
        assertEquals(importService.getBlankRequiredFieldMsg("Source"), error2.get("errorMessage").getAsString(), "Incorrect error message");
    }

    @Test
    @SneakyThrows
    public void headerCaseInsensitive() {
        File file = new File("src/test/resources/files/germplasm_import/germplasm_column_casing.csv");
        String listName = "CaseInsensitiveList";
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, listName), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt());

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        List<String> germplasmNames = new ArrayList<>();
        for (int i = 0; i < previewRows.size(); i++) {
            JsonObject germplasm = previewRows.get(i).getAsJsonObject().getAsJsonObject("germplasm").getAsJsonObject("brAPIObject");
            germplasmNames.add(germplasm.get("germplasmName").getAsString());
            int finalI = i;
            gson.fromJson(germplasm.getAsJsonObject("additionalInfo").getAsJsonObject("listEntryNumbers"), Map.class).forEach((listId, entryNumber) -> assertEquals(Integer.toString(finalI +1), entryNumber, "Wrong entry number"));
        }

        // Check the germplasm list
        checkGermplasmList(Germplasm.constructGermplasmListName(listName, validProgram), null, germplasmNames);
    }

    @Test
    @SneakyThrows
    public void missingRequiredFieldHeaderError() {
        File file = new File("src/test/resources/files/germplasm_import/missing_required_header.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmMappingId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(importService.getMissingColumnMsg("Source"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void missingOptionalFieldHeaderError() {
        File file = new File("src/test/resources/files/germplasm_import/missing_optional_header.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", validProgram.getId(), germplasmMappingId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(importService.getMissingColumnMsg("Entry No"), e.getMessage());
    }

    @Test
    @SneakyThrows
    public void circularParentDependencyError() {
        File file = new File("src/test/resources/files/germplasm_import/circular_parent_dependencies.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.circularDependency, result.getAsJsonObject("progress").get("message").getAsString());
    }

    @Test
    @SneakyThrows
    public void selfReferenceParentError() {
        File file = new File("src/test/resources/files/germplasm_import/self_ref_parent_dependencies.csv");
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(GERM_LIST_NAME, "Bad List"), true, client, validProgram, germplasmMappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, validProgram, germplasmMappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt());
        assertEquals(GermplasmProcessor.circularDependency, result.getAsJsonObject("progress").get("message").getAsString());
    }


    public void checkBasicResponse(JsonObject germplasm, Table fileData, Integer i) {

        // Germplasm display name
        assertEquals(fileData.getString(i, "Name"), germplasm.get("defaultDisplayName").getAsString(), "Wrong display name");
        // Entry Number
        gson.fromJson(germplasm.getAsJsonObject("additionalInfo").getAsJsonObject("listEntryNumbers"), Map.class).forEach((listId, entryNumber) -> assertEquals(fileData.getString(i, "Entry No"), entryNumber, "Wrong entry number"));
        JsonObject additionalInfo = germplasm.getAsJsonObject("additionalInfo");
        // Created By User ID
        assertEquals(testUser.getId().toString(), additionalInfo.getAsJsonObject(BrAPIAdditionalInfoFields.CREATED_BY).get(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID).getAsString(), "Wrong createdBy userId");
        // Created by User name
        assertEquals(testUser.getName(), additionalInfo.getAsJsonObject(BrAPIAdditionalInfoFields.CREATED_BY).get(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME).getAsString(), "Wrong createdBy userId");
        // Breeding Method
        String breedingMethodName = fileData.getString(i, "Breeding Method");
        ProgramBreedingMethodEntity breedingMethod = breedingMethodDAO.findByNameOrAbbreviation(breedingMethodName, validProgram.getId()).get(0);
        assertEquals(breedingMethod.getId().toString(), additionalInfo.get(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD_ID).getAsString(), "Wrong Breeding Method ID");
        assertEquals(breedingMethod.getName(), additionalInfo.get(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD).getAsString(), "Wrong Breeding Method name");
        // Seed source
        assertEquals(fileData.getString(i, "Source"), germplasm.get("seedSource").getAsString(), "Wrong seed source");
        // External Reference (user specified)
        // External Reference program
        JsonArray externalReferences = germplasm.getAsJsonArray("externalReferences");
        Map<String, String> expectedReferences = new HashMap<>();
        expectedReferences.put(Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.PROGRAMS), validProgram.getId().toString());
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
        String url = String.format("%sbrapi/v2/lists?listType=germplasm", BRAPI_URL);
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        boolean nameFound = false;
        boolean descriptionFound = false;
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
            assertFalse(found.contains(false), "Some germplasm names were not found in saved list");
        }
    }
}
