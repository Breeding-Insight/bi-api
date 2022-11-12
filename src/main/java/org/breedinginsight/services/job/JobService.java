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

package org.breedinginsight.services.job;

import lombok.SneakyThrows;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.FileImportService;
import org.breedinginsight.model.job.Job;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JobService {
    private FileImportService fileImportService;
    private ProgramService programService;

    @Inject
    public JobService(FileImportService fileImportService, ProgramService programService) {
        this.fileImportService = fileImportService;
        this.programService = programService;
    }

    public List<Job> getProgramJobs(UUID programId) throws DoesNotExistException {
        if (programService.getById(programId)
                          .isEmpty()) {
            throw new DoesNotExistException("Program id does not exist");
        }

        List<Job> jobs = new ArrayList<>();
        jobs.addAll(getProgramImports(programId));

        return jobs;
    }

    @SneakyThrows
    private List<Job> getProgramImports(UUID programId) {
        List<ImportResponse> uploads = fileImportService.getProgramUploads(programId, false);
        return uploads.stream()
                      .map(importJob -> new Job().setJobDetail(importJob)
                                                                  .setId(importJob.getImportId().toString())
                                                                  .setJobType(importJob.getImportType())
                                                                  .setCreatedAt(importJob.getCreatedAt())
                                                                  .setUpdatedAt(importJob.getProgress().getUpdatedAt())
                                                                  .setStatuscode(importJob.getProgress()
                                                                                             .getStatuscode())
                                                                  .setCreatedByUser(importJob.getCreatedByUser())
                                                                  .setStatusMessage(importJob.getProgress()
                                                                                                .getMessage())
                      )
                      .collect(Collectors.toList());
    }
}
