package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.jooq.tools.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class StudyQuery extends BrapiQuery {
    private String studyType;
    private String locationDbId;
    private String studyCode;
    private String studyPUI;
    private String commonCropName;
    private String trialDbId;
    private String studyDbId;
    private String studyName;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getStudyType())) {
            filters.add(constructFilterRequest("studyType", getStudyType()));
        }
        if (!StringUtils.isBlank(getLocationDbId())) {
            filters.add(constructFilterRequest("locationDbId", getLocationDbId()));
        }
        if (!StringUtils.isBlank(getStudyCode())) {
            filters.add(constructFilterRequest("studyCode", getStudyCode()));
        }
        if (!StringUtils.isBlank(getStudyPUI())) {
            filters.add(constructFilterRequest("studyPUI", getStudyPUI()));
        }
        if (!StringUtils.isBlank(getCommonCropName())) {
            filters.add(constructFilterRequest("commonCropName", getCommonCropName()));
        }
        if (!StringUtils.isBlank(getTrialDbId())) {
            filters.add(constructFilterRequest("trialDbId", getTrialDbId()));
        }
        if (!StringUtils.isBlank(getStudyDbId())) {
            filters.add(constructFilterRequest("studyDbId", getStudyDbId()));
        }
        if (!StringUtils.isBlank(getStudyName())) {
            filters.add(constructFilterRequest("studyName", getStudyName()));
        }
        return new SearchRequest(filters);
    }
}
