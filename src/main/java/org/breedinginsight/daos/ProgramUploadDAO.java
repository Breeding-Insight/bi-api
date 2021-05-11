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

import org.breedinginsight.dao.db.enums.UploadType;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.BatchUploadDao;
import org.breedinginsight.model.*;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramUploadDAO extends BatchUploadDao {

    private DSLContext dsl;

    @Inject
    public ProgramUploadDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<ProgramUpload> getUploads(UUID programId, UUID userId, UploadType type) {

        List<Record> records = getUploadsQuery()
                .where(BATCH_UPLOAD.PROGRAM_ID.eq(programId)
                        .and(BATCH_UPLOAD.USER_ID.eq(userId))
                        .and(BATCH_UPLOAD.TYPE.eq(type)))
                .fetch();

        return parseRecords(records);

    }

    public Optional<ProgramUpload> getUploadById(UUID id) {
        List<Record> records = getUploadsQuery()
                .where(BATCH_UPLOAD.ID.eq(id))
                .fetch();

        if (records.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(parseRecords(records).get(0));
        }

    }

    private SelectOnConditionStep<Record> getUploadsQuery() {

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        return dsl.select()
                .from(BATCH_UPLOAD)
                .innerJoin(PROGRAM).on(BATCH_UPLOAD.PROGRAM_ID.eq(PROGRAM.ID))
                .innerJoin(BI_USER).on(BATCH_UPLOAD.USER_ID.eq(BI_USER.ID))
                .innerJoin(createdByUser).on(BATCH_UPLOAD.CREATED_BY.eq(createdByUser.ID))
                .innerJoin(updatedByUser).on(BATCH_UPLOAD.UPDATED_BY.eq(updatedByUser.ID));
    }

    private List<ProgramUpload> parseRecords(List<Record> records) {

        List<ProgramUpload> resultUploads = new ArrayList<>();
        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");

        // Parse the result
        for (Record record : records) {
            ProgramUpload upload = ProgramUpload.parseSQLRecord(record);
            upload.setProgram(Program.parseSQLRecord(record));
            upload.setUser(User.parseSQLRecord(record));
            upload.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
            upload.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
            resultUploads.add(upload);
        }

        return resultUploads;
    }

    public void deleteUploads(UUID programId, UUID userId, UploadType type) {
        dsl.delete(BATCH_UPLOAD)
                .where(BATCH_UPLOAD.PROGRAM_ID.eq(programId)
                        .and(BATCH_UPLOAD.USER_ID.eq(userId))
                        .and(BATCH_UPLOAD.TYPE.eq(type)))
                .execute();
    }


}
