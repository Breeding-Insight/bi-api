package org.breedinginsight.brapi.v2;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import groovy.lang.Tuple2;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.reactivex.Flowable;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.ImportTestUtils;
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
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static io.micronaut.http.HttpRequest.GET;

public class BrAPITestUtils {

    private static final List<Column> columns = ExperimentFileColumns.getOrderedColumns();

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

    /**
    * Create a test program and populate it with users, traits, germplasm, and experiment data.
    * Note: Brittle, the exact state created by this method is depended on by multiple tests.
    * @return a tuple containing the created program and the ids of the created experiments.
    */
    public Tuple2<Program, List<String>> setupTestProgram(DSLContext brAPIDslContext, Gson gson) throws Exception {

        // Insert species in BrAPI server database (not bidb).
        FannyPack brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        brAPIDslContext.execute(brapiFp.get("InsertSpecies"));

        // Create program, species and test users.
        FannyPack fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Test User
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).orElseThrow(Exception::new);
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Species
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
        Program program = TestUtils.insertAndFetchTestProgram(gson, client, programRequest);

        // Add Program Administrator user.
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId());

        // Add Experimental Collaborator user.
        User collaborator = userDAO.getUserByOrcId(TestTokenValidator.OTHER_TEST_USER_ORCID).orElseThrow(Exception::new);
        dsl.execute(securityFp.get("InsertProgramRolesExperimentalCollaborator"), collaborator.getId().toString(), program.getId().toString());

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
        List<Trait> traits = createTraits(2);
        AuthenticatedUser user = new AuthenticatedUser(testUser.getName(), new ArrayList<>(), testUser.getId(), new ArrayList<>());
        try {
            ontologyService.createTraits(program.getId(), traits, user, false);
        } catch (ValidatorException e) {
            System.err.println(e.getErrors());
            throw e;
        }

        // Add germplasm to program
        List<BrAPIGermplasm> germplasm = createGermplasm(1, BRAPI_REFERENCE_SOURCE);
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(String.format("%s/programs", BRAPI_REFERENCE_SOURCE));
        newReference.setReferenceId(program.getId().toString());

        germplasm.forEach(germ -> germ.getExternalReferences().add(newReference));

        germplasmDAO.createBrAPIGermplasm(germplasm, program.getId(), null);

        ImportTestUtils importTestUtils = new ImportTestUtils();
        String newExperimentWorkflowId = importTestUtils.getExperimentWorkflowId(client, 0);

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

        // Import test experiment, environments, and any observations
        JsonObject importResult = importTestUtils.uploadAndFetchWorkflow(
                writeDataToFile(List.of(row1, row2), new ArrayList<>()),
                null,
                true,
                client,
                program,
                mappingId,
                newExperimentWorkflowId);
        String experiment1Id = importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("trial").getAsJsonObject()
                .get("id").getAsString();

        // Import test experiment, environments, and any observations
        importResult = importTestUtils.uploadAndFetchWorkflow(
                writeDataToFile(List.of(makeExpImportRow("Env3", "xyz"), makeExpImportRow("Env4", "xyz")), new ArrayList<>()),
                null,
                true,
                client,
                program,
                mappingId,
                newExperimentWorkflowId);
        String experiment2Id = importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("trial").getAsJsonObject()
                .get("id").getAsString();

        // Refetch collaborator, since we updated program_user_roles.
        collaborator = userDAO.getUserByOrcId(TestTokenValidator.OTHER_TEST_USER_ORCID).orElseThrow(Exception::new);
        // Add collaborator to experiment.
        dsl.execute(securityFp.get("AddCollaborator"), experiment2Id, collaborator.getProgramRoles().get(0).getId());

        // Return program and experimentId.
        return new Tuple2<>(program, List.of(experiment1Id, experiment2Id));
    }

    public File writeDataToFile(List<Map<String, Object>> data, List<Trait> traits) throws IOException {
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

    public String getEnvId(JsonObject result, int index) {
        return result
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(index).getAsJsonObject()
                .get("study").getAsJsonObject()
                .get("brAPIObject").getAsJsonObject()
                .get("externalReferences").getAsJsonArray()
                .get(2).getAsJsonObject()
                .get("referenceId").getAsString();
    }

    public List<BrAPIGermplasm> createGermplasm(int numToCreate, String referenceSource) {
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
            testReference.setReferenceSource(referenceSource);
            testReference.setReferenceID(UUID.randomUUID().toString());
            externalRef.add(testReference);
            testGermplasm.setExternalReferences(externalRef);
            germplasm.add(testGermplasm);
        }

        return germplasm;
    }

    public List<Trait> createTraits(int numToCreate) {
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
                            .units("test unit")
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

    public Map<String, Object> makeExpImportRow(String environment) {
        return makeExpImportRow(environment, "Test Exp");
    }

    public Map<String, Object> makeExpImportRow(String environment, String expTitle) {
        Map<String, Object> row = new HashMap<>();
        row.put(ExperimentObservation.Columns.GERMPLASM_GID, "1");
        row.put(ExperimentObservation.Columns.TEST_CHECK, "T");
        row.put(ExperimentObservation.Columns.EXP_TITLE, expTitle);
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
}
