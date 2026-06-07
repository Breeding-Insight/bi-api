package org.breedinginsight.api.model.v1.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class GenotypeImportQuery extends QueryParams {

    private String sampleSubmissionId;
    private String projectNameForSampleSubmission;
    private String sampleSubmissionCreatedBy;
    private String genotypingFileName;
    private String genotypingImportDate;
    private String genotypingImportBy;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();

        if (!StringUtils.isBlank(getSampleSubmissionId())) {
            filters.add(constructFilterRequest("sampleSubmissionId", getSampleSubmissionId()));
        }
        if (!StringUtils.isBlank(getProjectNameForSampleSubmission())) {
            filters.add(constructFilterRequest("projectNameForSampleSubmission", getProjectNameForSampleSubmission()));
        }
        if (!StringUtils.isBlank(getSampleSubmissionCreatedBy())) {
            filters.add(constructFilterRequest("sampleSubmissionCreatedBy", getSampleSubmissionCreatedBy()));
        }
        if (!StringUtils.isBlank(getGenotypingFileName())) {
            filters.add(constructFilterRequest("genotypingFileName", getGenotypingFileName()));
        }
        if (!StringUtils.isBlank(getGenotypingImportDate())) {
            filters.add(constructFilterRequest("genotypingImportDate", getGenotypingImportDate()));
        }
        if (!StringUtils.isBlank(getGenotypingImportBy())) {
            filters.add(constructFilterRequest("genotypingImportBy", getGenotypingImportBy()));
        }

        return new SearchRequest(filters);
    }

    private FilterRequest constructFilterRequest(String field, String value) {
        return FilterRequest.builder()
                .field(field)
                .value(value)
                .build();
    }
}