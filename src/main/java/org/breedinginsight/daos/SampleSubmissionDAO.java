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

import org.breedinginsight.dao.db.Tables;
import org.breedinginsight.dao.db.tables.daos.SampleSubmissionDao;
import org.breedinginsight.dao.db.tables.pojos.SampleSubmissionEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SampleSubmission;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.breedinginsight.dao.db.Tables.SAMPLE_SUBMISSION;

@Singleton
public class SampleSubmissionDAO extends SampleSubmissionDao {

    private DSLContext dsl;

    @Inject
    public SampleSubmissionDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<SampleSubmission> getBySubmissionId(Program program, UUID submissionId) {
        return dsl.select()
                  .from(SAMPLE_SUBMISSION)
                  .where(SAMPLE_SUBMISSION.PROGRAM_ID.eq(program.getId()))
                  .and(SAMPLE_SUBMISSION.ID.eq(submissionId))
                  .fetchInto(SampleSubmissionEntity.class)
                  .stream()
                  .map(SampleSubmission::new)
                  .collect(Collectors.toList());
    }

}
