package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;

import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class StudyQuery extends BrapiQuery {
    private Boolean active;
    private List<String> commonCropNames;
    private List<String> externalReferenceIds;
    private List<String> externalReferenceSources;
    private List<String> germplasmDbIds;
    private List<String> germplasmNames;
    private List<String> locationDbIds;
    private List<String> locationNames;
    private List<String> observationVariableDbIds;
    private List<String> observationVariableNames;
    private List<String> observationVariablePUIs;
    private List<String> programDbIds;
    private List<String> programNames;
    private List<String> seasonDbIds;
    private List<String> studyCodes;
    private List<String> studyDbIds;
    private List<String> studyNames;
    private List<String> studyPUIs;
    private List<String> studyTypes;
    private List<String> trialDbIds;
    private List<String> trialNames;


    // TODO: need a more dynamic way to build FilterField, FilterRequest only handles a subset of filtering.
    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
//        if (getActive()) {
//            filters.add(constructFilterRequest("active", getActive()));
//        }
//        if (!StringUtils.isBlank(getCommonCropNames())) {
//            filters.add(constructFilterRequest("commonCropName", getCommonCropNames()));
//        }
//        if (!StringUtils.isBlank(getExternalReferenceIds())) {
//            filters.add(constructFilterRequest("externalReferenceId", getExternalReferenceIds()));
//        }
//        if (!StringUtils.isBlank(getExternalReferenceSources())) {
//            filters.add(constructFilterRequest("externalReferenceSource", getExternalReferenceSources()));
//        }
//        if (!StringUtils.isBlank(getGermplasmDbIds())) {
//            filters.add(constructFilterRequest("germplasmDbId", getGermplasmDbIds()));
//        }
//        if (!StringUtils.isBlank(getGermplasmNames())) {
//            filters.add(constructFilterRequest("germplasmName", getGermplasmNames()));
//        }
//        if (!StringUtils.isBlank(getLocationDbIds())) {
//            filters.add(constructFilterRequest("locationDbId", getLocationDbIds()));
//        }
//        if (!StringUtils.isBlank(getLocationNames())) {
//            filters.add(constructFilterRequest("locationName", getLocationNames()));
//        }
//        if (!StringUtils.isBlank(getObservationVariableDbIds())) {
//            filters.add(constructFilterRequest("observationVariableDbId", getObservationVariableDbIds()));
//        }
//        if (!StringUtils.isBlank(getObservationVariableNames())) {
//            filters.add(constructFilterRequest("observationVariableName", getObservationVariableNames()));
//        }
//        if (!StringUtils.isBlank(getObservationVariablePUIs())) {
//            filters.add(constructFilterRequest("observationVariablePUI", getObservationVariablePUIs()));
//        }
//        if (!StringUtils.isBlank(getPage())) {
//            filters.add(constructFilterRequest("page", getPage()));
//        }
//        if (!StringUtils.isBlank(getPageSize())) {
//            filters.add(constructFilterRequest("pageSize", getPageSize()));
//        }
//        if (!StringUtils.isBlank(getProgramDbIds())) {
//            filters.add(constructFilterRequest("programDbId", getProgramDbIds()));
//        }
//        if (!StringUtils.isBlank(getProgramNames())) {
//            filters.add(constructFilterRequest("programName", getProgramNames()));
//        }
//        if (!StringUtils.isBlank(getSeasonDbIds())) {
//            filters.add(constructFilterRequest("seasonDbId", getSeasonDbIds()));
//        }
//        if (!StringUtils.isBlank(getSortBy())) {
//            filters.add(constructFilterRequest("sortBy", getSortBy()));
//        }
//        if (!StringUtils.isBlank(getSortOrder())) {
//            filters.add(constructFilterRequest("sortOrder", getSortOrder()));
//        }
//        if (!StringUtils.isBlank(getStudyCodes())) {
//            filters.add(constructFilterRequest("studyCode", getStudyCodes()));
//        }
//        if (!StringUtils.isBlank(getStudyDbIds())) {
//            filters.add(constructFilterRequest("studyDbId", getStudyDbIds()));
//        }
//        if (!StringUtils.isBlank(getStudyNames())) {
//            filters.add(constructFilterRequest("studyName", getStudyNames()));
//        }
//        if (!StringUtils.isBlank(getStudyPUIs())) {
//            filters.add(constructFilterRequest("studyPUI", getStudyPUIs()));
//        }
//        if (!StringUtils.isBlank(getStudyTypes())) {
//            filters.add(constructFilterRequest("studyType", getStudyTypes()));
//        }
//        if (!StringUtils.isBlank(getTrialDbIds())) {
//            filters.add(constructFilterRequest("trialDbId", getTrialDbIds()));
//        }
//        if (!StringUtils.isBlank(getTrialNames())) {
//            filters.add(constructFilterRequest("trialName", getTrialNames()));
//        }

        return new SearchRequest(filters);
    }
}
