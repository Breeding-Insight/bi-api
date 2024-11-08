/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import static org.breedinginsight.dao.db.Tables.PROGRAM_USER_ROLE;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.EXPERIMENT_PROGRAM_USER_ROLE;
import static org.breedinginsight.dao.db.Tables.PROGRAM_USER_ROLE;


@Slf4j
@Singleton
public class ExperimentalCollaboratorDAO extends ExperimentProgramUserRoleDao {

    private DSLContext dsl;

    @Inject
    public ExperimentalCollaboratorDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public ExperimentProgramUserRoleEntity create(UUID experimentId, UUID programUserRoleId, UUID createdByUserId) {
        return dsl.insertInto(EXPERIMENT_PROGRAM_USER_ROLE)
                .columns(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID,
                        EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID,
                        EXPERIMENT_PROGRAM_USER_ROLE.CREATED_BY,
                        EXPERIMENT_PROGRAM_USER_ROLE.CREATED_AT,
                        EXPERIMENT_PROGRAM_USER_ROLE.UPDATED_BY,
                        EXPERIMENT_PROGRAM_USER_ROLE.UPDATED_AT)
                .values(experimentId,
                        programUserRoleId,
                        createdByUserId,
                        OffsetDateTime.now(),
                        createdByUserId,
                        OffsetDateTime.now())
                .returning(EXPERIMENT_PROGRAM_USER_ROLE.fields())
                .fetchOneInto(ExperimentProgramUserRoleEntity.class);
    }

    /**
     * Get the list (expected to have zero or one elements) of records authorizing a program user to access an experiment.
     * @param programUserRoleId the primary key of a program_user_role record.
     * @param experimentId the BI-assigned UUID of an experiment.
     * @return a list of ExperimentProgramUserRoleEntity.
     */
    public List<ExperimentProgramUserRoleEntity> fetchByProgramUserIdAndExperimentId(UUID programUserRoleId, UUID experimentId) {
        // Only returns results for active program_user_role rows.
        return dsl.select(EXPERIMENT_PROGRAM_USER_ROLE.fields())
                .from(EXPERIMENT_PROGRAM_USER_ROLE)
                .innerJoin(PROGRAM_USER_ROLE).on(EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID.eq(PROGRAM_USER_ROLE.ID))
                .where(EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID.eq(programUserRoleId))
                .and(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID.eq(experimentId))
                .and(PROGRAM_USER_ROLE.ACTIVE.eq(true))
                .fetchInto(ExperimentProgramUserRoleEntity.class);
    }

    /**
     * Get a list of BI-assigned experiment UUIDs of experiments which a program user is authorized to access.
     * @param programUserRoleId the primary key of a program_user_role record.
     * @param activeOnly if true, only return records for active program users.
     * @return a list of BI-assigned experiment UUIDs.
     */
    public List<UUID> getExperimentIds(UUID programUserRoleId, boolean activeOnly) {
        // If activeOnly, this will only return results if the program_user_role row is active.
        if (activeOnly)
        {
            return getExperimentIdsIfActive(programUserRoleId);
        }
        return getExperimentIds(programUserRoleId);
    }

    private List<UUID> getExperimentIdsIfActive(UUID programUserRoleId) {
        return dsl.select(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID)
                .from(EXPERIMENT_PROGRAM_USER_ROLE)
                .join(PROGRAM_USER_ROLE).on(EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID.eq(PROGRAM_USER_ROLE.ID))
                .where(EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID.eq(programUserRoleId))
                .and(PROGRAM_USER_ROLE.ACTIVE.eq(true))
                .fetchInto(UUID.class);
    }

    private List<UUID> getExperimentIds(UUID programUserRoleId) {
        return dsl.select(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID)
                .from(EXPERIMENT_PROGRAM_USER_ROLE)
                .where(EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID.eq(programUserRoleId))
                .fetchInto(UUID.class);
    }
}
