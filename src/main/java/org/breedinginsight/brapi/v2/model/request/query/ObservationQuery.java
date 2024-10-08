package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;

import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class ObservationQuery extends BrapiQuery {
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

    /**
     * Build a query string from the ObservationQuery object.
     */
    public String toQueryString() {
        StringBuilder queryStringBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(observationUnitDbId)) {
            queryStringBuilder.append("observationUnitDbId=").append(observationUnitDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationVariableDbId)) {
            queryStringBuilder.append("observationVariableDbId=").append(observationVariableDbId).append("&");
        }
        if (StringUtils.isNotBlank(locationDbId)) {
            queryStringBuilder.append("locationDbId=").append(locationDbId).append("&");
        }
        if (StringUtils.isNotBlank(seasonDbId)) {
            queryStringBuilder.append("seasonDbId=").append(seasonDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationLevel)) {
            queryStringBuilder.append("observationLevel=").append(observationLevel).append("&");
        }
        if (StringUtils.isNotBlank(searchResultsDbId)) {
            queryStringBuilder.append("searchResultsDbId=").append(searchResultsDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationTimeStampRangeStart)) {
            queryStringBuilder.append("observationTimeStampRangeStart=").append(observationTimeStampRangeStart).append("&");
        }
        if (StringUtils.isNotBlank(observationTimeStampRangeEnd)) {
            queryStringBuilder.append("observationTimeStampRangeEnd=").append(observationTimeStampRangeEnd).append("&");
        }
        if (StringUtils.isNotBlank(programDbId)) {
            queryStringBuilder.append("programDbId=").append(programDbId).append("&");
        }
        if (StringUtils.isNotBlank(trialDbId)) {
            queryStringBuilder.append("trialDbId=").append(trialDbId).append("&");
        }
        if (StringUtils.isNotBlank(studyDbId)) {
            queryStringBuilder.append("studyDbId=").append(studyDbId).append("&");
        }
        if (StringUtils.isNotBlank(germplasmDbId)) {
            queryStringBuilder.append("germplasmDbId=").append(germplasmDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelName)) {
            queryStringBuilder.append("observationUnitLevelName=").append(observationUnitLevelName).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelOrder)) {
            queryStringBuilder.append("observationUnitLevelOrder=").append(observationUnitLevelOrder).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelCode)) {
            queryStringBuilder.append("observationUnitLevelCode=").append(observationUnitLevelCode).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelRelationshipName)) {
            queryStringBuilder.append("observationUnitLevelRelationshipName=").append(observationUnitLevelRelationshipName).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelRelationshipOrder)) {
            queryStringBuilder.append("observationUnitLevelRelationshipOrder=").append(observationUnitLevelRelationshipOrder).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelRelationshipCode)) {
            queryStringBuilder.append("observationUnitLevelRelationshipCode=").append(observationUnitLevelRelationshipCode).append("&");
        }

        // If any query parameters were added, remove the trailing "&" from the query string.
        if (queryStringBuilder.length() > 0) {
            queryStringBuilder.deleteCharAt(queryStringBuilder.length() - 1);
        }

        return queryStringBuilder.toString();
    }

}
