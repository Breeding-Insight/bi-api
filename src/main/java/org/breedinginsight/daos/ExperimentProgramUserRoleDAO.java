package org.breedinginsight.daos;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.ExperimentProgramUserRoleDao;
import org.breedinginsight.dao.db.tables.pojos.ExperimentProgramUserRoleEntity;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.EXPERIMENT_PROGRAM_USER_ROLE;

@Slf4j
@Singleton
public class ExperimentProgramUserRoleDAO extends ExperimentProgramUserRoleDao {

    private DSLContext dsl;

    @Inject
    public ExperimentProgramUserRoleDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<ExperimentProgramUserRoleEntity> getProgramUserRoleExperimentIds(UUID programUserRoleId){
        return fetchByProgramUserRoleId(programUserRoleId);
    }

    public ExperimentProgramUserRoleEntity createExperimentProgramUserRole(UUID experimentId, UUID programUserRoleId, UUID userId){
        return dsl.insertInto(EXPERIMENT_PROGRAM_USER_ROLE)
                .columns(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID,
                        EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID,
                        EXPERIMENT_PROGRAM_USER_ROLE.CREATED_BY,
                        EXPERIMENT_PROGRAM_USER_ROLE.UPDATED_BY,
                        EXPERIMENT_PROGRAM_USER_ROLE.CREATED_AT,
                        EXPERIMENT_PROGRAM_USER_ROLE.UPDATED_AT)
                .values(experimentId, programUserRoleId, userId, userId, OffsetDateTime.now(), OffsetDateTime.now())
                .returning(EXPERIMENT_PROGRAM_USER_ROLE.fields())
                .fetchOneInto(ExperimentProgramUserRoleEntity.class);
    }
}
