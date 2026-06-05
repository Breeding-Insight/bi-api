package org.breedinginsight.model;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Accessors(chain = true)
@ToString
@SuperBuilder
@NoArgsConstructor
@Introspected
@Jacksonized
public class GenotypeImportDetails {
    private UUID sampleSubmissionId;
    private String projectNameForSampleSubmission;
    private String sampleSubmissionCreatedBy;
    private String genotypingFileName;
    private OffsetDateTime genotypingImportDate;
    private String genotypingImportBy;
}
