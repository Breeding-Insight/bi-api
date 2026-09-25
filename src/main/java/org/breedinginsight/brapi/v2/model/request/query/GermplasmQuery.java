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
    // This is a meta-parameter, it describes the display format of any date fields.
    private String dateDisplayFormat;

    // The list id used to get a collection of germplasm
    private String listDbId;

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

    @Override
    public Map<String, String> getFilterValuesByBrAPIColumnName() {
        Map<String, String> filterValuesByBrAPIColumnName = new HashMap<>();

        filterValuesByBrAPIColumnName.put("importEntryNumber", getImportEntryNumber());
        filterValuesByBrAPIColumnName.put("accessionNumber", getAccessionNumber());
        filterValuesByBrAPIColumnName.put("defaultDisplayName", getDefaultDisplayName());
        filterValuesByBrAPIColumnName.put("breedingMethod", getBreedingMethod());
        filterValuesByBrAPIColumnName.put("seedSource", getSeedSource());
        filterValuesByBrAPIColumnName.put("pedigree", getPedigree());
        filterValuesByBrAPIColumnName.put("femaleParentGID", getFemaleParentGID());
        filterValuesByBrAPIColumnName.put("maleParentGID", getMaleParentGID());
        filterValuesByBrAPIColumnName.put("createdDate", getCreatedDate());
        filterValuesByBrAPIColumnName.put("createdBy", getCreatedByUserName());
        filterValuesByBrAPIColumnName.put("synonyms", getSynonym());

        return filterValuesByBrAPIColumnName;
    }

    @Override
    public Map<String, String> getBrAPIColumnNamesByBiColumnName() {
        Map<String, String> brAPIColumnNamesByBiColumnName = new HashMap<>();

        brAPIColumnNamesByBiColumnName.put("importEntryNumber", "importEntryNumber");
        brAPIColumnNamesByBiColumnName.put("accessionNumber", "accessionNumber");
        brAPIColumnNamesByBiColumnName.put("defaultDisplayName", "defaultDisplayName");
        brAPIColumnNamesByBiColumnName.put("breedingMethod", "breedingMethod");
        brAPIColumnNamesByBiColumnName.put("seedSource", "seedSource");
        brAPIColumnNamesByBiColumnName.put("pedigree", "pedigree");
        brAPIColumnNamesByBiColumnName.put("femaleParentGID", "femaleParentGID");
        brAPIColumnNamesByBiColumnName.put("maleParentGID", "maleParentGID");
        brAPIColumnNamesByBiColumnName.put("createdDate", "createdDate");
        brAPIColumnNamesByBiColumnName.put("createdBy", "createdByUserName");
        brAPIColumnNamesByBiColumnName.put("synonyms", "synonyms");

        return brAPIColumnNamesByBiColumnName;
    }
}
