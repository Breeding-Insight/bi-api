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
public class GermplasmQuery extends BrapiQuery {
    private String accessionNumber;
    private String defaultDisplayName;
    private String breedingMethod;
    private String seedSource;
    private String femaleParentGID;
    private String maleParentGID;
    private String createdDate;
    private String createdByUserName;
    private String synonym;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getAccessionNumber())) {
            filters.add(constructFilterRequest("accessionNumber", getAccessionNumber()));
        }
        if (!StringUtils.isBlank(getDefaultDisplayName())) {
            filters.add(constructFilterRequest("defaultDisplayName", getDefaultDisplayName()));
        }
        if (!StringUtils.isBlank(getBreedingMethod())) {
            filters.add(constructFilterRequest("additionalInfo.breedingMethod", getBreedingMethod()));
        }
        if (!StringUtils.isBlank(getSeedSource())) {
            filters.add(constructFilterRequest("seedSource", getSeedSource()));
        }
        if (!StringUtils.isBlank(getFemaleParentGID())) {
            filters.add(constructFilterRequest("femaleParent", getFemaleParentGID()));
        }
        if (!StringUtils.isBlank(getMaleParentGID())) {
            filters.add(constructFilterRequest("maleParent", getMaleParentGID()));
        }
        if (!StringUtils.isBlank(getCreatedDate())) {
            filters.add(constructFilterRequest("additionalInfo.createdDate", getCreatedDate()));
        }
        if (!StringUtils.isBlank(getCreatedByUserName())) {
            filters.add(constructFilterRequest("additionalInfo.createdBy.userName", getCreatedByUserName()));
        }
        if (!StringUtils.isBlank(getSynonym())) {
            filters.add(constructFilterRequest("synonyms", getSynonym()));
        }
        return new SearchRequest(filters);
    }
}
