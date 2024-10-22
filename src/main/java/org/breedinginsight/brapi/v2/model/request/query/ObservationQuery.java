package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationTableQueryParams;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;

import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class ObservationQuery extends BrapiQuery {
    @Setter
    private String observationUnitDbId;
    @Setter
    private String observationVariableDbId;
    @Setter
    private String locationDbId;
    @Setter
    private String seasonDbId;
    private String observationLevel;
    @Setter
    private String searchResultsDbId;
    private String observationTimeStampRangeStart;
    private String observationTimeStampRangeEnd;
    @Setter
    private String programDbId;
    @Setter
    private String trialDbId;
    @Setter
    private String studyDbId;
    @Setter
    private String germplasmDbId;
    private String observationUnitLevelName;
    private String observationUnitLevelOrder;
    private String observationUnitLevelCode;
    private String observationUnitLevelRelationshipName;
    private String observationUnitLevelRelationshipOrder;
    private String observationUnitLevelRelationshipCode;

    public ObservationTableQueryParams toBrAPIQueryParams() {
        return (ObservationTableQueryParams) new ObservationTableQueryParams()
                .observationUnitDbId(observationUnitDbId)
                .observationVariableDbId(observationVariableDbId)
                .locationDbId(locationDbId)
                .seasonDbId(seasonDbId)
                .observationLevel(observationLevel)
                .searchResultsDbId(searchResultsDbId)
                .observationTimeStampRangeStart(observationTimeStampRangeStart)
                .observationTimeStampRangeEnd(observationTimeStampRangeEnd)
                .programDbId(programDbId)
                .trialDbId(trialDbId)
                .studyDbId(studyDbId)
                .germplasmDbId(germplasmDbId)
                .observationUnitLevelName(observationUnitLevelName)
                .observationUnitLevelOrder(observationUnitLevelOrder)
                .observationUnitLevelCode(observationUnitLevelCode)
                .observationUnitLevelRelationshipName(observationUnitLevelRelationshipName)
                .observationUnitLevelRelationshipOrder(observationUnitLevelRelationshipOrder)
                .observationUnitLevelRelationshipCode(observationUnitLevelRelationshipCode)
                .page(getPage())
                .pageSize(getPageSize());
    }

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
     * This may be useful for proxying requests to the backing BrAPI service.
     */
    public String toQueryString() {
        StringBuilder queryString = new StringBuilder();
        queryString.append("?");
        if (StringUtils.isNotBlank(observationUnitDbId)) {
            queryString.append("observationUnitDbId=").append(observationUnitDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationVariableDbId)) {
            queryString.append("observationVariableDbId=").append(observationVariableDbId).append("&");
        }
        if (StringUtils.isNotBlank(locationDbId)) {
            queryString.append("locationDbId=").append(locationDbId).append("&");
        }
        if (StringUtils.isNotBlank(seasonDbId)) {
            queryString.append("seasonDbId=").append(seasonDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationLevel)) {
            queryString.append("observationLevel=").append(observationLevel).append("&");
        }
        if (StringUtils.isNotBlank(searchResultsDbId)) {
            queryString.append("searchResultsDbId=").append(searchResultsDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationTimeStampRangeStart)) {
            queryString.append("observationTimeStampRangeStart=").append(observationTimeStampRangeStart).append("&");
        }
        if (StringUtils.isNotBlank(observationTimeStampRangeEnd)) {
            queryString.append("observationTimeStampRangeEnd=").append(observationTimeStampRangeEnd).append("&");
        }
        if (StringUtils.isNotBlank(programDbId)) {
            queryString.append("programDbId=").append(programDbId).append("&");
        }
        if (StringUtils.isNotBlank(trialDbId)) {
            queryString.append("trialDbId=").append(trialDbId).append("&");
        }
        if (StringUtils.isNotBlank(studyDbId)) {
            queryString.append("studyDbId=").append(studyDbId).append("&");
        }
        if (StringUtils.isNotBlank(germplasmDbId)) {
            queryString.append("germplasmDbId=").append(germplasmDbId).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelName)) {
            queryString.append("observationUnitLevelName=").append(observationUnitLevelName).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelOrder)) {
            queryString.append("observationUnitLevelOrder=").append(observationUnitLevelOrder).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelCode)) {
            queryString.append("observationUnitLevelCode=").append(observationUnitLevelCode).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelRelationshipName)) {
            queryString.append("observationUnitLevelRelationshipName=").append(observationUnitLevelRelationshipName).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelRelationshipOrder)) {
            queryString.append("observationUnitLevelRelationshipOrder=").append(observationUnitLevelRelationshipOrder).append("&");
        }
        if (StringUtils.isNotBlank(observationUnitLevelRelationshipCode)) {
            queryString.append("observationUnitLevelRelationshipCode=").append(observationUnitLevelRelationshipCode).append("&");
        }

        // If any query parameters were added, this removes the trailing "&" from the query string.
        // If no query parameters were added, this removes the superfluous "?" from the query string.
        queryString.deleteCharAt(queryString.length() - 1);

        return queryString.toString();
    }

}
