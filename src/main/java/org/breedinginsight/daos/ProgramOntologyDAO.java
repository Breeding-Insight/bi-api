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

import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.ProgramTable;
import org.breedinginsight.dao.db.tables.daos.ProgramOntologyDao;
import org.breedinginsight.dao.db.tables.daos.ProgramSharedOntologyDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramSharedOntologyEntity;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramOntologyDAO extends ProgramOntologyDao {

    private DSLContext dsl;
    private ProgramSharedOntologyDao programSharedOntologyDao;

    @Inject
    public ProgramOntologyDAO(Configuration config, DSLContext dsl, ProgramSharedOntologyDao programSharedOntologyDao) {
        super(config);
        this.dsl = dsl;
        this.programSharedOntologyDao = programSharedOntologyDao;
    }

    /**
     * Retrieves all programs that the given program has shared its ontology with.
     * @param programId
     */
    public List<Program> getSharedPrograms(UUID programId) {

        ProgramTable sharedProgram = PROGRAM.as("sharedProgram");

        Result<Record> queryResult = dsl.select()
                .from(PROGRAM)
                .join(PROGRAM_SHARED_ONTOLOGY)
                    .on(PROGRAM.ID.eq(PROGRAM_SHARED_ONTOLOGY.PROGRAM_ID))
                .join(sharedProgram)
                    .on(PROGRAM_SHARED_ONTOLOGY.SHARED_PROGRAM_ID.eq(sharedProgram.ID))
                .where(PROGRAM.ID.eq(programId))
                .fetch();

        List<Program> sharedPrograms = new ArrayList<>();
        for (Record record: queryResult){
            if (record.getValue(PROGRAM.BRAPI_URL) == null) {
                record.setValue(PROGRAM.BRAPI_URL, BrAPIConstants.SYSTEM_DEFAULT.getValue());
            }
            Program program = Program.parseSQLRecord(record);
            sharedPrograms.add(program);
        }
        return sharedPrograms;
    }

    public void createSharedOntologies(List<ProgramSharedOntologyEntity> shareRecords) {
        programSharedOntologyDao.insert(shareRecords);
    }

    public List<ProgramSharedOntologyEntity> getSharedOntologies(UUID programId) {
        return programSharedOntologyDao.fetchByProgramId(programId);
    }

    public Optional<ProgramSharedOntologyEntity> getSharedOntologyById(UUID programId, UUID sharedProgramId) {
        List<ProgramSharedOntologyEntity> sharedOntologies = getSharedOntologies(programId).stream()
                .filter(programSharedOntologyEntity -> programSharedOntologyEntity.getSharedProgramId().equals(sharedProgramId))
                .collect(Collectors.toList());
        return sharedOntologies.size() > 0 ? Optional.of(sharedOntologies.get(0)) : Optional.empty();
    }

    public void revokeSharedOntology(ProgramSharedOntologyEntity sharedOntology) {
        programSharedOntologyDao.delete(sharedOntology);
    }
}
