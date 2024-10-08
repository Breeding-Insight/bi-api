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
public class ObservationQuery extends BrapiQuery {
    private String accept;
    private String observationUnitDbId;
    private String observationVariableDbId;
    private String locationDbId;
    private String seasonDbId;
    private String observationLevel;
    private String searchResultsDbId;
    private String observationTimeStampRangeStart;
    private String observationTimeStampRangeEnd;
    private String programDbId;
    private String trialDbId;
    private String studyDbId;
    private String germplasmDbId;
    private String observationUnitLevelName;
    private String observationUnitLevelOrder;
    private String observationUnitLevelCode;
    private String observationUnitLevelRelationshipName;
    private String observationUnitLevelRelationshipOrder;
    private String observationUnitLevelRelationshipCode;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getAccept())) {
            filters.add(constructFilterRequest("accept", getAccept()));
        }
        if (!StringUtils.isBlank(getObservationUnitDbId())) {
            filters.add(constructFilterRequest("observationUnitDbId", getObservationUnitDbId()));
        }
        if (!StringUtils.isBlank(getObservationVariableDbId())) {
            filters.add(constructFilterRequest("observationVariableDbId", getObservationVariableDbId()));
        }
        if (!StringUtils.isBlank(getLocationDbId())) {
            filters.add(constructFilterRequest("locationDbId", getLocationDbId()));
        }
        if (!StringUtils.isBlank(getSeasonDbId())) {
            filters.add(constructFilterRequest("seasonDbId", getSeasonDbId()));
        }
        if (!StringUtils.isBlank(getObservationLevel())) {
            filters.add(constructFilterRequest("observationLevel", getObservationLevel()));
        }
        if (!StringUtils.isBlank(getSearchResultsDbId())) {
            filters.add(constructFilterRequest("searchResultsDbId", getSearchResultsDbId()));
        }
        if (!StringUtils.isBlank(getObservationTimeStampRangeStart())) {
            filters.add(constructFilterRequest("observationTimeStampRangeStart", getObservationTimeStampRangeStart()));
        }
        if (!StringUtils.isBlank(getObservationTimeStampRangeEnd())) {
            filters.add(constructFilterRequest("observationTimeStampRangeEnd", getObservationTimeStampRangeEnd()));
        }
        if (!StringUtils.isBlank(getProgramDbId())) {
            filters.add(constructFilterRequest("programDbId", getProgramDbId()));
        }
        if (!StringUtils.isBlank(getTrialDbId())) {
            filters.add(constructFilterRequest("trialDbId", getTrialDbId()));
        }
        if (!StringUtils.isBlank(getStudyDbId())) {
            filters.add(constructFilterRequest("studyDbId", getStudyDbId()));
        }
        if (!StringUtils.isBlank(getGermplasmDbId())) {
            filters.add(constructFilterRequest("germplasmDbId", getGermplasmDbId()));
        }
        if (!StringUtils.isBlank(getObservationUnitLevelName())) {
            filters.add(constructFilterRequest("observationUnitLevelName", getObservationUnitLevelName()));
        }
        if (!StringUtils.isBlank(getObservationUnitLevelOrder())) {
            filters.add(constructFilterRequest("observationUnitLevelOrder", getObservationUnitLevelOrder()));
        }
        if (!StringUtils.isBlank(getObservationUnitLevelCode())) {
            filters.add(constructFilterRequest("observationUnitLevelCode", getObservationUnitLevelCode()));
        }
        if (!StringUtils.isBlank(getObservationUnitLevelRelationshipName())) {
            filters.add(constructFilterRequest("observationUnitLevelRelationshipName", getObservationUnitLevelRelationshipName()));
        }
        if (!StringUtils.isBlank(getObservationUnitLevelRelationshipOrder())) {
            filters.add(constructFilterRequest("observationUnitLevelRelationshipOrder", getObservationUnitLevelRelationshipOrder()));
        }
        if (!StringUtils.isBlank(getObservationUnitLevelRelationshipCode())) {
            filters.add(constructFilterRequest("observationUnitLevelRelationshipCode", getObservationUnitLevelRelationshipCode()));
        }
        return new SearchRequest(filters);
    }
}
