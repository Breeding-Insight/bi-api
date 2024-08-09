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
import org.breedinginsight.dao.db.tables.ExperimentProgramUserRoleTable;
import org.breedinginsight.dao.db.tables.ProgramUserRoleTable;
import org.breedinginsight.dao.db.tables.daos.ExperimentProgramUserRoleDao;
import org.breedinginsight.dao.db.tables.pojos.ExperimentProgramUserRoleEntity;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.EXPERIMENT_PROGRAM_USER_ROLE;

@Slf4j
@Singleton
public class ExperimentalCollaboratorDAO extends ExperimentProgramUserRoleDao {

    private DSLContext dsl;

    @Inject
    public ExperimentalCollaboratorDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<UUID> fetchExperimentIds(UUID userId, UUID programId) {
        ExperimentProgramUserRoleTable EXPERIMENT_PROGRAM_USER_ROLE = ExperimentProgramUserRoleTable.EXPERIMENT_PROGRAM_USER_ROLE;
        ProgramUserRoleTable PROGRAM_USER_ROLE = ProgramUserRoleTable.PROGRAM_USER_ROLE;

        Result<Record> queryResult =
                dsl.select().from(EXPERIMENT_PROGRAM_USER_ROLE)
                        .join(PROGRAM_USER_ROLE)
                        .on(EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID.eq(PROGRAM_USER_ROLE.ID))
                        .where(PROGRAM_USER_ROLE.USER_ID.eq(userId)).and(PROGRAM_USER_ROLE.PROGRAM_ID.eq(programId))
                        .fetch();

        List<UUID> experimentIds = new ArrayList<>(queryResult.size());
        for (Record record : queryResult) {
            experimentIds.add(record.getValue(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID));
        }

        return experimentIds;
    }

    public ExperimentProgramUserRoleEntity create(UUID experimentId, UUID programUserRoleId, UUID userId) {
        return dsl.insertInto(EXPERIMENT_PROGRAM_USER_ROLE)
                .columns(EXPERIMENT_PROGRAM_USER_ROLE.EXPERIMENT_ID,
                        EXPERIMENT_PROGRAM_USER_ROLE.PROGRAM_USER_ROLE_ID,
                        EXPERIMENT_PROGRAM_USER_ROLE.CREATED_BY,
                        EXPERIMENT_PROGRAM_USER_ROLE.CREATED_AT,
                        EXPERIMENT_PROGRAM_USER_ROLE.UPDATED_BY,
                        EXPERIMENT_PROGRAM_USER_ROLE.UPDATED_AT)
                .values(experimentId,
                        programUserRoleId,
                        userId,
                        OffsetDateTime.now(),
                        userId,
                        OffsetDateTime.now())
                .returning(EXPERIMENT_PROGRAM_USER_ROLE.fields())
                .fetchOneInto(ExperimentProgramUserRoleEntity.class);
    }
}
