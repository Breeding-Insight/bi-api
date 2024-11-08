package org.breedinginsight.services;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.SneakyThrows;
import org.breedinginsight.DatabaseTest;
import io.kowalski.fannypack.FannyPack;
import org.breedinginsight.dao.db.tables.pojos.ExperimentProgramUserRoleEntity;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExperimentalCollaboratorServiceTest extends DatabaseTest {

    private FannyPack fp;

    @Inject
    private DSLContext dsl;

    @Inject
    private ExperimentalCollaboratorService experimentalCollaboratorService;


    @BeforeAll
    public void setup() {

        // Insert test data into the db
        fp = FannyPack.fill("src/test/resources/sql/ExperimentalCollaboratorServiceTest.sql");

        // Create user.
        dsl.execute(fp.get("CreateUser"));

        // Create program.
        dsl.execute(fp.get("CreateProgram"));

        // Add user to program in the Experimental Collaborator role.
        dsl.execute(fp.get("AddUserToProgram"));
    }

    @Test
    @SneakyThrows
    void testExperimentalCollaboratorCRUD() {
        // TODO: currently only Created, Read, and Delete are implemented. Once update is implemented, test that too.
        // Create experiment_program_user_role row using the service method, and ensure it was created and returned as expected.

        UUID experimentId = UUID.fromString("12d8aaf8-d0d9-4837-a71e-aa64b47885ae");
        UUID programUserRoleId = UUID.fromString("0fb8ecf4-3a16-40c6-9c7f-cdfc945967a1");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // Test create.
        ExperimentProgramUserRoleEntity created = experimentalCollaboratorService.createExperimentalCollaborator(
                programUserRoleId,
                experimentId,
                userId
        );

        assertNotNull(created);
        assertEquals(experimentId, created.getExperimentId());
        assertEquals(programUserRoleId, created.getProgramUserRoleId());
        assertEquals(userId, created.getCreatedBy());
        assertEquals(userId, created.getUpdatedBy());

        // Test read.
        ExperimentProgramUserRoleEntity retrieved = experimentalCollaboratorService.getExperimentalCollaborators(experimentId).get(0);

        assertNotNull(retrieved);

        assertEquals(created.getId(), retrieved.getId());
        assertEquals(created.getProgramUserRoleId(), retrieved.getProgramUserRoleId());
        assertEquals(created.getExperimentId(), retrieved.getExperimentId());
        assertEquals(created.getCreatedBy(), retrieved.getCreatedBy());
        assertEquals(created.getUpdatedBy(), retrieved.getUpdatedBy());
        assertEquals(created.getCreatedAt(), retrieved.getCreatedAt());
        assertEquals(created.getUpdatedAt(), retrieved.getUpdatedAt());

        // TODO: test update.

        // Test delete.
        experimentalCollaboratorService.deleteExperimentalCollaborator(created.getId());

        List<ExperimentProgramUserRoleEntity> result = experimentalCollaboratorService.getExperimentalCollaborators(experimentId);
        assertTrue(result.isEmpty());
    }

}