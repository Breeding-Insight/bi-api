package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.jooq.tools.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private String externalReferenceSource;
    private String externalReferenceId;

    @Override
    public Map<String, String> getFilterValuesByBrAPIColumnName() {
        Map<String, String> filterValuesByBrAPIColumnName = new HashMap<>();

        filterValuesByBrAPIColumnName.put("studyType", getStudyType());
        filterValuesByBrAPIColumnName.put("locationDbId", getLocationDbId());
        filterValuesByBrAPIColumnName.put("studyCode", getStudyCode());
        filterValuesByBrAPIColumnName.put("studyPUI", getStudyPUI());
        filterValuesByBrAPIColumnName.put("commonCropName", getCommonCropName());
        filterValuesByBrAPIColumnName.put("trialDbId", getTrialDbId());
        filterValuesByBrAPIColumnName.put("studyDbId", getStudyDbId());
        filterValuesByBrAPIColumnName.put("studyName", getStudyName());
        filterValuesByBrAPIColumnName.put("externalReferenceSource", getExternalReferenceSource());
        filterValuesByBrAPIColumnName.put("externalReferenceId", getExternalReferenceId());

        return filterValuesByBrAPIColumnName;
    }

    @Override
    public Map<String, String> getBrAPIColumnNamesByBiColumnName() {
        Map<String, String> brAPIColumnNamesByBiColumnName = new HashMap<>();

        brAPIColumnNamesByBiColumnName.put("studyType", "studyType");
        brAPIColumnNamesByBiColumnName.put("locationDbId", "locationDbId");
        brAPIColumnNamesByBiColumnName.put("studyCode", "studyCode");
        brAPIColumnNamesByBiColumnName.put("studyPUI", "studyPUI");
        brAPIColumnNamesByBiColumnName.put("commonCropName", "commonCropName");
        brAPIColumnNamesByBiColumnName.put("trialDbId", "trialDbId");
        brAPIColumnNamesByBiColumnName.put("studyDbId", "studyDbId");
        brAPIColumnNamesByBiColumnName.put("studyName", "studyName");

        return brAPIColumnNamesByBiColumnName;
    }

}
