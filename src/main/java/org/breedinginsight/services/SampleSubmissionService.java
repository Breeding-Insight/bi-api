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

package org.breedinginsight.services;

import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.BrAPISample;
import org.breedinginsight.brapps.importer.daos.BrAPIPlateDAO;
import org.breedinginsight.brapps.importer.daos.BrAPISampleDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.SampleSubmissionDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SampleSubmission;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class SampleSubmissionService {

    private final String referenceSource;

    private final SampleSubmissionDAO submissionDAO;
    private final BrAPIPlateDAO plateDAO;
    private final BrAPISampleDAO sampleDAO;

    @Inject
    public SampleSubmissionService(@Property(name = "brapi.server.reference-source") String referenceSource, SampleSubmissionDAO submissionDAO, BrAPIPlateDAO plateDAO, BrAPISampleDAO sampleDAO) {
        this.referenceSource = referenceSource;
        this.submissionDAO = submissionDAO;
        this.plateDAO = plateDAO;
        this.sampleDAO = sampleDAO;
    }

    public SampleSubmission createSubmission(SampleSubmission submission, Program program, ImportUpload upload) throws ApiException {
        submission.setProgramId(program.getId());
        submission.setCreatedBy(upload.getCreatedBy());
        submission.setUpdatedBy(upload.getUpdatedBy());
        submissionDAO.insert(submission);

        List<BrAPIPlate> savedPlates = plateDAO.createPlates(program, submission.getPlates(), upload);
        submission.setPlates(savedPlates);
        Map<String, String> plateNameToDbId = savedPlates.stream().collect(Collectors.toMap(BrAPIPlate::getPlateName, BrAPIPlate::getPlateDbId));

        List<BrAPISample> samplesToSave = submission.getSamples().stream().map(sample -> sample.plateDbId(plateNameToDbId.get(sample.getPlateName()))).collect(Collectors.toList());
        List<BrAPISample> savedSamples = sampleDAO.createSamples(program, samplesToSave, upload);
        submission.setSamples(savedSamples);

        return submission;
    }

    public Optional<SampleSubmission> getSampleSubmission(Program program, UUID submissionId) throws ApiException {
        return populateSubmissions(program, submissionDAO.getBySubmissionId(program, submissionId)).stream().findFirst();
    }

    public List<SampleSubmission> getProgramSubmissions(Program program) {
        return submissionDAO.fetchByProgramId(program.getId())
                .stream()
                .map(SampleSubmission::new)
                .collect(Collectors.toList());
    }

    private List<SampleSubmission> populateSubmissions(Program program, List<SampleSubmission> submissions) throws ApiException {
        List<String> submissionIds = submissions.stream().map(s -> s.getId().toString()).collect(Collectors.toList());
        List<BrAPIPlate> plates = plateDAO.readPlatesBySubmissionIds(program, submissionIds);
        List<BrAPISample> samples = sampleDAO.readSamplesBySubmissionIds(program, submissionIds);

        Map<String, List<BrAPIPlate>> platesBySubmissionId = new HashMap<>();
        String submissionXrefSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATE_SUBMISSIONS);
        plates.forEach(plate -> {
            BrAPIExternalReference brAPIExternalReference = Utilities.getExternalReference(plate.getExternalReferences(), submissionXrefSource).orElseThrow(() -> new IllegalStateException(String.format("Plate %s does not have a submission ID", plate.getPlateName())));
            List<BrAPIPlate> submissionPlates = platesBySubmissionId.getOrDefault(brAPIExternalReference.getReferenceId(), new ArrayList<>());
            submissionPlates.add(plate);
            platesBySubmissionId.putIfAbsent(brAPIExternalReference.getReferenceId(), submissionPlates);
        });

        Map<String, List<BrAPISample>> samplesBySubmissionId = new HashMap<>();
        samples.forEach(sample -> {
            BrAPIExternalReference brAPIExternalReference = Utilities.getExternalReference(sample.getExternalReferences(), submissionXrefSource).orElseThrow(() -> new IllegalStateException(String.format("Plate %s does not have a submission ID", sample.getPlateName())));
            List<BrAPISample> submissionSamples = samplesBySubmissionId.getOrDefault(brAPIExternalReference.getReferenceId(), new ArrayList<>());
            submissionSamples.add(sample);
            samplesBySubmissionId.putIfAbsent(brAPIExternalReference.getReferenceId(), submissionSamples);
        });

        submissions.forEach(submission -> {
            submission.setPlates(platesBySubmissionId.get(submission.getId().toString()));
            submission.setSamples(samplesBySubmissionId.get(submission.getId().toString()));
        });

        return submissions;
    }
}
