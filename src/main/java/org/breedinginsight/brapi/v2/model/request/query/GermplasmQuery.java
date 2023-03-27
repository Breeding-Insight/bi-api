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
    private String importEntryNumber;
    private String accessionNumber;
    private String defaultDisplayName;
    private String breedingMethod;
    private String seedSource;
    private String pedigree;
    private String femaleParentGID;
    private String maleParentGID;
    private String createdDate;
    private String createdByUserName;
    private String synonym;
    // This is a meta-parameter, it describes the display format of the createdDate parameter.
    private String createdDateDisplayFormat;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getImportEntryNumber())) {
            filters.add(constructFilterRequest("importEntryNumber", getImportEntryNumber()));
        }
        if (!StringUtils.isBlank(getAccessionNumber())) {
            filters.add(constructFilterRequest("accessionNumber", getAccessionNumber()));
        }
        if (!StringUtils.isBlank(getDefaultDisplayName())) {
            filters.add(constructFilterRequest("defaultDisplayName", getDefaultDisplayName()));
        }
        if (!StringUtils.isBlank(getBreedingMethod())) {
            filters.add(constructFilterRequest("breedingMethod", getBreedingMethod()));
        }
        if (!StringUtils.isBlank(getSeedSource())) {
            filters.add(constructFilterRequest("seedSource", getSeedSource()));
        }
        if (!StringUtils.isBlank(getPedigree())) {
            filters.add(constructFilterRequest("pedigree", getPedigree()));
        }
        if (!StringUtils.isBlank(getFemaleParentGID())) {
            filters.add(constructFilterRequest("femaleParentGID", getFemaleParentGID()));
        }
        if (!StringUtils.isBlank(getMaleParentGID())) {
            filters.add(constructFilterRequest("maleParentGID", getMaleParentGID()));
        }
        if (!StringUtils.isBlank(getCreatedDate())) {
            filters.add(constructFilterRequest("createdDate", getCreatedDate()));
        }
        if (!StringUtils.isBlank(getCreatedByUserName())) {
            filters.add(constructFilterRequest("createdByUserName", getCreatedByUserName()));
        }
        if (!StringUtils.isBlank(getSynonym())) {
            filters.add(constructFilterRequest("synonyms", getSynonym()));
        }
        return new SearchRequest(filters);
    }
}
