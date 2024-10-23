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

}
