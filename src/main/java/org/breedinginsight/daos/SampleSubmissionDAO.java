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

import org.brapi.v2.model.geno.response.BrAPIVendorOrderStatusResponseResult;
import org.breedinginsight.dao.db.Tables;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.SampleSubmissionDao;
import org.breedinginsight.dao.db.tables.pojos.SampleSubmissionEntity;
import org.breedinginsight.dao.db.tables.records.SampleSubmissionRecord;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SampleSubmission;
import org.breedinginsight.model.User;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.breedinginsight.dao.db.Tables.*;
import static org.breedinginsight.dao.db.Tables.BI_USER;

@Singleton
public class SampleSubmissionDAO extends SampleSubmissionDao {

    private DSLContext dsl;

    @Inject
    public SampleSubmissionDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<SampleSubmission> getBySubmissionId(Program program, UUID submissionId) {
        return getRecords(List.of(SAMPLE_SUBMISSION.PROGRAM_ID.eq(program.getId()), SAMPLE_SUBMISSION.ID.eq(submissionId)));
    }

    public List<SampleSubmission> getByProgramId(UUID programId) {
        return getRecords(List.of(SAMPLE_SUBMISSION.PROGRAM_ID.eq(programId)));
    }

    public List<SampleSubmission> getSubmittedAndNotComplete() {
        return getRecords(List.of(SAMPLE_SUBMISSION.VENDOR_ORDER_ID.isNotNull(),
                SAMPLE_SUBMISSION.VENDOR_STATUS.isNull().or(SAMPLE_SUBMISSION.VENDOR_STATUS.ne(BrAPIVendorOrderStatusResponseResult.StatusEnum.COMPLETED.name()))));
    }

    public SampleSubmission update(SampleSubmission submission, User updatedBy) {
        submission.setUpdatedAt(OffsetDateTime.now());
        submission.setUpdatedBy(updatedBy.getId());
        if(submission.getSubmittedByUser() != null) {
            submission.setSubmittedBy(submission.getSubmittedByUser().getId());
        } else {
            submission.setSubmittedBy(null);
        }

        super.update(submission);
        return submission;
    }

    private List<SampleSubmission> getRecords(List<Condition> andConditions) {
        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");
        BiUserTable submittedByUser = BI_USER.as("submittedByUser");
        try(SelectSelectStep<Record> select = dsl.select()) {
            SelectConditionStep<Record> query = select
                    .from(SAMPLE_SUBMISSION)
                    .join(createdByUser).on(SAMPLE_SUBMISSION.CREATED_BY.eq(createdByUser.ID))
                    .join(updatedByUser).on(SAMPLE_SUBMISSION.UPDATED_BY.eq(updatedByUser.ID))
                    .leftJoin(submittedByUser).on(SAMPLE_SUBMISSION.SUBMITTED_BY.eq(submittedByUser.ID))
                    .where("1=1");

            for (Condition condition : andConditions) {
                query = query.and(condition);
            }

            return query.fetch()
                    .stream()
                    .map(record -> parseRecord(record, createdByUser, updatedByUser, submittedByUser))
                    .collect(Collectors.toList());
        }

    }


    private SampleSubmission parseRecord(Record record, BiUserTable createdByUser, BiUserTable updatedByUser, BiUserTable submittedByUser) {
        SampleSubmission submission = new SampleSubmission(record.into(SampleSubmissionEntity.class));
        submission.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
        submission.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
        submission.setSubmittedByUser(User.parseSQLRecord(record, submittedByUser));
        //these explicit setters are needed because of overlapping column names with the bi_user table joined to the query
        submission.setId(record.get(SAMPLE_SUBMISSION.ID));
        submission.setName(record.get(SAMPLE_SUBMISSION.NAME));
        submission.setProgramId(record.get(SAMPLE_SUBMISSION.PROGRAM_ID));
        submission.setCreatedAt(record.get(SAMPLE_SUBMISSION.CREATED_AT));
        submission.setUpdatedAt(record.get(SAMPLE_SUBMISSION.UPDATED_AT));
        submission.setCreatedBy(submission.getCreatedByUser().getId());
        submission.setUpdatedBy(submission.getUpdatedByUser().getId());
        if(submission.getSubmittedByUser() != null) {
            submission.setSubmittedBy(submission.getSubmittedByUser().getId());
        }
        return submission;
    }
}
