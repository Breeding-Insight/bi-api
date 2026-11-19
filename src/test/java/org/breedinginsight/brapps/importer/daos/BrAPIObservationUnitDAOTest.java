package org.breedinginsight.brapps.importer.daos;

import com.google.gson.Gson;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.SneakyThrows;
import org.brapi.client.v2.JSON;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservationTreatment;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.TestUtils.insertAndFetchTestProgram;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIObservationUnitDAOTest extends BrAPITest {

    private FannyPack fp;

    @Inject
    private DSLContext dsl;

    @Inject
    private SpeciesDAO speciesDAO;

    @Inject
    private UserDAO userDAO;

    @Inject
    private BrAPIObservationUnitDAO obsUnitDAO;

    @Inject
    private ProgramService programService;

    private Program validProgram;

    private Gson gson;
    private ImportUpload upload;

    private BrAPIObservationTreatment testTreatment;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient biClient;

    @Property(name = "brapi.server.reference-source")
    String referenceSource;

    @BeforeAll
    @SneakyThrows
    public void setup() {

        ImportProgress progress = ImportProgress.builder().build();

        upload = ImportUpload.uploadBuilder()
                .progress(progress)
                .build();

        gson = new JSON().getGson();

        // Add species needed to create program
        fp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        super.getBrapiDsl().execute(fp.get("InsertSpecies"));
        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);

        // Insert system admin role so can create program
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        User testUser = userDAO.getUserByOAuthId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        ProgramRequest program = ProgramRequest.builder()
                .name("Test Program")
                .species(speciesRequest)
                .key("TEST")
                .build();

        // create test program
        validProgram = insertAndFetchTestProgram(gson, biClient, program);
        // updated with brapi db id
        validProgram = programService.getById(validProgram.getId()).get();

        testTreatment = new BrAPIObservationTreatment();
        testTreatment.setFactor("ou1 treatment");
    }

    @Test
    @SneakyThrows
    @Order(1)
    public void testCreateObservationUnitAdditionalInfoSingleTreatmentFactor() {
        // treatments field
        BrAPIObservationUnit ou1 = new BrAPIObservationUnit();
        ou1.setObservationUnitName("test1");
        ou1.setProgramDbId(validProgram.getBrapiProgram().getProgramDbId());

        var treatment = new BrAPIObservationTreatment();
        treatment.setFactor("ou1 treatment");
        ou1.setTreatments(List.of(treatment));
        // Set xref.
        BrAPIExternalReference xref = new BrAPIExternalReference();
        xref.setReferenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS));
        xref.setReferenceId(UUID.randomUUID().toString());
        ou1.setExternalReferences(List.of(xref));

        List<BrAPIObservationUnit> ous = List.of(ou1);
        List<BrAPIObservationUnit> createdOus = obsUnitDAO.createBrAPIObservationUnits(ous, validProgram.getId(), upload);
        singleTreatmentAsserts(createdOus, testTreatment);
    }

    @Test
    @SneakyThrows
    @Order(2)
    public void testGetObservationUnitAdditionalInfoSingleTreatmentFactor() {
        List<BrAPIObservationUnit> createdOus = obsUnitDAO.getProgramObservationUnits(validProgram.getId());
        singleTreatmentAsserts(createdOus, testTreatment);
    }

    private void singleTreatmentAsserts(List<BrAPIObservationUnit> obsUnits, BrAPIObservationTreatment expectedTreatment) {
        assertEquals(1, obsUnits.size(), "Expected 1 observation unit");

        BrAPIObservationUnit ou = obsUnits.get(0);
        List<BrAPIObservationTreatment> treatments = ou.getTreatments();
        assertEquals(1, treatments.size(), "Expected treatments property");

        BrAPIObservationTreatment treatment = treatments.get(0);
        assertEquals(expectedTreatment, treatment, "Expected treatments to be same");

        // Storing treatments in additionalInfo is no longer necessary since BrAPI server stores treatments in observation_unit_treatment
        assertNull(ou.getAdditionalInfo(), "Expected OU additionalInfo to be null");
    }

}
