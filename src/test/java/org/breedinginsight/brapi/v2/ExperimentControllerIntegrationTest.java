package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.ImportTestUtils;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.parsers.experiment.ExperimentFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import javax.inject.Inject;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperimentControllerIntegrationTest extends BrAPITest {

    private Program program;
    private String experimentId;
    private List<String> envIds = new ArrayList<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Column> columns = ExperimentFileColumns.getOrderedColumns();
    private List<Trait> traits;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    @Inject
    private DSLContext dsl;
    @Inject
    private UserDAO userDAO;
    @Inject
    private SpeciesDAO speciesDAO;
    @Inject
    private OntologyService ontologyService;
    @Inject
    private BrAPIGermplasmDAO germplasmDAO;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() throws Exception {
        FannyPack fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        ImportTestUtils importTestUtils = new ImportTestUtils();
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        FannyPack brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        // Test User
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).orElseThrow(Exception::new);
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Species
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));
        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        // Test Program
        ProgramRequest programRequest = ProgramRequest.builder()
                .name("Test Program")
                .abbreviation("Test")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("TEST")
                .build();
        program = TestUtils.insertAndFetchTestProgram(gson, client, programRequest);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId());
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Get experiment import map
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=ExperimentsTemplateMap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String mappingId = JsonParser.parseString(Objects.requireNonNull(response.body())).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Add traits to program
        traits = createTraits(2);
        AuthenticatedUser user = new AuthenticatedUser(testUser.getName(), new ArrayList<>(), testUser.getId(), new ArrayList<>());
        try {
            ontologyService.createTraits(program.getId(), traits, user, false);
        } catch (ValidatorException e) {
            System.err.println(e.getErrors());
            throw e;
        }

        // Add germplasm to program
        List<BrAPIGermplasm> germplasm = createGermplasm(1);
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(String.format("%s/programs", BRAPI_REFERENCE_SOURCE));
        newReference.setReferenceID(program.getId().toString());

        germplasm.forEach(germ -> germ.getExternalReferences().add(newReference));

        germplasmDAO.createBrAPIGermplasm(germplasm, program.getId(), null);

        // Make test experiment import
        Map<String, Object> row1 = makeExpImportRow("Env1");
        Map<String, Object> row2 = makeExpImportRow("Env2");

        // Add test observation data
        for (Trait trait : traits) {
            Random random = new Random();

            // TODO: test for sending obs data as double.
            //  A float is returned from the backend instead of double. there is a separate card to fix this.
            // Double val1 = Math.random();

            Float val1 = random.nextFloat();
            row1.put(trait.getObservationVariableName(), val1);
        }

        rows.add(row1);
        rows.add(row2);

        // Import test experiment, environments, and any observations
        JsonObject importResult = importTestUtils.uploadAndFetch(
                writeDataToFile(rows, traits),
                null,
                true,
                client,
                program,
                mappingId);
        experimentId = importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("trial").getAsJsonObject()
                .get("id").getAsString();
        envIds.add(importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("study").getAsJsonObject()
                .get("brAPIObject").getAsJsonObject()
                .get("externalReferences").getAsJsonArray()
                .get(2).getAsJsonObject()
                .get("referenceID").getAsString()
        );
    }

    /*
   Tests
    - export empty dataset, single environment, csv format
    - export empty dataset, single environment, xls format
    - export empty dataset, single environment, xlsx format
    - export populated dataset, single environment, csv format
    - export populated dataset, single environment, xls format
    - export populated dataset, single environment, xlsx format
    - export empty dataset, multiple environment, csv format
    - export empty dataset, multiple environment, xls format
    - export empty dataset, multiple environment, xlsx format
    - export populated dataset, multiple environment, csv format
    - export populated dataset, multiple environment, xls format
    - export populated dataset, multiple environment, xlsx format
   */
    @ParameterizedTest
    @CsvSource(value = {"true,false,CSV", "true,true,CSV",
            "false,false,CSV", "false,true,CSV",
            "true,false,XLS", "true,true,XLS",
            "false,false,XLS", "false,true,XLS",
            "true,false,XLSX", "true,true,XLSX",
            "false,false,XLSX", "false,true,XLSX",})
    @SneakyThrows
    void downloadDatasets(boolean includeTimestamps, boolean requestEnv, String extension) {
        // Download test experiment
        String envParam = "all=true";
        if (requestEnv) {
            envParam = "environments=" + String.join(",", envIds);
        }
        Flowable<HttpResponse<byte[]>> call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/export?includeTimestamps=%s&%s&fileExtension=%s",
                        program.getId().toString(), experimentId, includeTimestamps, envParam, extension))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> response = call.blockingFirst();

        // Assert 200 response
        assertEquals(HttpStatus.OK, response.getStatus());

        // Assert file format fidelity
        Map<String, String> mediaTypeByExtension = new HashMap<>();
        mediaTypeByExtension.put("CSV", FileType.CSV.getMimeType());
        mediaTypeByExtension.put("XLS", FileType.XLS.getMimeType());
        mediaTypeByExtension.put("XLSX", FileType.XLSX.getMimeType());
        String downloadMediaType = response.getHeaders().getContentType().orElseThrow(Exception::new);
        assertEquals(mediaTypeByExtension.get(extension), downloadMediaType);

        // Assert import/export fidelity and presence of observation units in export
        ByteArrayInputStream bodyStream = new ByteArrayInputStream(Objects.requireNonNull(response.body()));
        Table download = Table.create();
        if (extension.equals("CSV")) {
            download = FileUtil.parseTableFromCsv(bodyStream);
        }
        if (extension.equals("XLS") || extension.equals("XLSX")) {
            download = FileUtil.parseTableFromExcel(bodyStream, 0);
        }
        checkDownloadTable(requestEnv, rows, download, includeTimestamps, extension);
    }
    private File writeDataToFile(List<Map<String, Object>> data, List<Trait> traits) throws IOException {
        File file = File.createTempFile("test", ".csv");

        if(traits != null) {
            traits.forEach(trait -> columns.add(
                        Column.builder()
                                .value(trait.getObservationVariableName())
                                .dataType(Column.ColumnDataType.STRING)
                                .build())
            );
        }
        ByteArrayOutputStream byteArrayOutputStream = CSVWriter.writeToCSV(columns, data);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(byteArrayOutputStream.toByteArray());

        return file;
    }

    private Map<String, Object> makeExpImportRow(String environment) {
        Map<String, Object> row = new HashMap<>();
        row.put(ExperimentObservation.Columns.GERMPLASM_GID, "1");
        row.put(ExperimentObservation.Columns.TEST_CHECK, "T");
        row.put(ExperimentObservation.Columns.EXP_TITLE, "Test Exp");
        row.put(ExperimentObservation.Columns.EXP_UNIT, "Plot");
        row.put(ExperimentObservation.Columns.EXP_TYPE, "Phenotyping");
        row.put(ExperimentObservation.Columns.ENV, environment);
        row.put(ExperimentObservation.Columns.ENV_LOCATION, "Location A");
        row.put(ExperimentObservation.Columns.ENV_YEAR, "2023");
        row.put(ExperimentObservation.Columns.EXP_UNIT_ID, "a-1");
        row.put(ExperimentObservation.Columns.REP_NUM, "1");
        row.put(ExperimentObservation.Columns.BLOCK_NUM, "1");
        row.put(ExperimentObservation.Columns.ROW, "1");
        row.put(ExperimentObservation.Columns.COLUMN, "1");
        return row;
    }

    private List<Trait> createTraits(int numToCreate) {
        List<Trait> traits = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String varName = "tt_test_" + (i + 1);
            traits.add(Trait.builder()
                    .observationVariableName(varName)
                    .fullName(varName)
                    .entity("Plant " + i)
                    .attribute("height " + i)
                    .traitDescription("test")
                    .programObservationLevel(ProgramObservationLevel.builder().name("Plot").build())
                    .scale(Scale.builder()
                            .scaleName("test scale")
                            .dataType(DataType.NUMERICAL)
                            .validValueMin(0)
                            .validValueMax(100)
                            .build())
                    .method(Method.builder()
                            .description("test method")
                            .methodClass("test method")
                            .build())
                    .build());
        }

        return traits;
    }

    private List<BrAPIGermplasm> createGermplasm(int numToCreate) {
        List<BrAPIGermplasm> germplasm = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String gid = ""+(i+1);
            BrAPIGermplasm testGermplasm = new BrAPIGermplasm();
            testGermplasm.setGermplasmName(String.format("Germplasm %s [TEST-%s]", gid, gid));
            testGermplasm.setSeedSource("Wild");
            testGermplasm.setAccessionNumber(gid);
            testGermplasm.setDefaultDisplayName(String.format("Germplasm %s", gid));
            JsonObject additionalInfo = new JsonObject();
            additionalInfo.addProperty("importEntryNumber", gid);
            additionalInfo.addProperty("breedingMethod", "Allopolyploid");
            testGermplasm.setAdditionalInfo(additionalInfo);
            List<BrAPIExternalReference> externalRef = new ArrayList<>();
            BrAPIExternalReference testReference = new BrAPIExternalReference();
            testReference.setReferenceSource(BRAPI_REFERENCE_SOURCE);
            testReference.setReferenceID(UUID.randomUUID().toString());
            externalRef.add(testReference);
            testGermplasm.setExternalReferences(externalRef);
            germplasm.add(testGermplasm);
        }

        return germplasm;
    }
    private void checkDownloadTable(
            boolean requestEnv,
            List<Map<String, Object>> importRows,
            Table table,
            boolean includeTimestamps,
            String extension) {
        // Filename is correct: <exp-title>_Observation Dataset [<prog-key>-<exp-seq>]_<environment>_<export-timestamp>
        List<Map<String, Object>> requestedImportRows = importRows;

        // All columns included
        Integer expectedColNumber = columns.size();
        if (includeTimestamps) {
            expectedColNumber += traits.size();
        }
        assertEquals(expectedColNumber, table.columnCount());

        if (!requestEnv) {

            // All environments downloaded
            importRows.stream()
                    .map(row -> row.get(ExperimentObservation.Columns.ENV).toString())
                    .distinct()
                    .collect(Collectors.toList())
                    .forEach(envName -> assertTrue(table.stringColumn("Env").contains(envName)));

        } else {

            // Only requested environment downloaded
            requestedImportRows = importRows
                    .stream()
                    .filter(row -> row.get("Env").toString().equals("Env1")).collect(Collectors.toList());
            assertEquals(1, table.stringColumn("Env").countUnique());
            assertTrue(table.stringColumn("Env").contains("Env1"));
        }

        List<Map<String, Object>> matchingImportRows = new ArrayList<>();
        Optional<Map<String, Object>> matchingImportRow;

        for (int rowNum = 0; rowNum < requestedImportRows.size(); rowNum++) {
            Row downloadRow = table.row(rowNum);

            // sort order is not guaranteed to be th same as import, so find import row for corresponding export row
            // by first matching environment and GID
            matchingImportRow = requestedImportRows.stream().filter(row -> {
                String gid = ExperimentObservation.Columns.GERMPLASM_GID;
                String env = ExperimentObservation.Columns.ENV;
                if (extension.equalsIgnoreCase(FileType.CSV.getName())) {
                    return Integer.parseInt(row.get(gid).toString()) == downloadRow.getInt(gid) &&
                            row.get(env).equals(downloadRow.getString(env));
                } else {
                    return row.get(gid).equals(downloadRow.getString(gid)) && row.get(env).equals(downloadRow.getString(env));
                }
            }).findAny();
            assertTrue(matchingImportRow.isPresent() && !matchingImportRow.get().isEmpty());

            // then check the rest of the fields match
            if (isMatchedRow(matchingImportRow.get(), downloadRow)) {
                matchingImportRows.add(matchingImportRow.get());
            }
        }
        assertEquals(requestedImportRows.size(),matchingImportRows.size());

        // Observation units populated
        assertEquals(0, table.column("ObsUnitID").countMissing());
        assertEquals(requestedImportRows.size(), table.column("ObsUnitID").countUnique());
    }

    private boolean isMatchedRow(Map<String, Object> importRow, Row downloadRow) {
        System.out.println("Validating row: " + downloadRow.getRowNumber());
        System.out.println("import columns: " + importRow.size());
        return importRow.entrySet().stream().filter(e -> {
            String header = e.getKey();
            List<Column> importColumns = columns
                    .stream()
                    .filter(col -> header.equals(col.getValue())).collect(Collectors.toList());
            if (importColumns.size() != 1) {
                return false;
            }
            Object expectedVal = null;
            Object downloadedVal = null;
            boolean doCompare = false;

            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.STRING)) {
                expectedVal = e.getValue().toString();
                downloadedVal = downloadRow.getString(e.getKey());
                doCompare = true;
            }
            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.INTEGER)) {
                expectedVal = Integer.parseInt(e.getValue().toString());
                downloadedVal = downloadRow.getInt(e.getKey());
                doCompare = true;
            }
            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.DOUBLE)) {
                expectedVal = Double.parseDouble(e.getValue().toString());
                downloadedVal = downloadRow.getDouble(e.getKey());
                doCompare = true;
            }
            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.FLOAT)) {
                expectedVal = e.getValue();
                downloadedVal = downloadRow.getFloat(e.getKey());
                doCompare = true;
            }
            System.out.println("Column: "+e.getKey()+", Expected: '"+ expectedVal +"', Received: '" + downloadedVal+"'");
            if(doCompare) {
                assertEquals(expectedVal, downloadedVal);
                return expectedVal.equals(downloadedVal);
            } else {
                return false;
            }
        }).count() == importRow.size();
    }
}
