package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;

import java.util.HashMap;
import java.util.Map;

@Getter
@Introspected
public class ExperimentQuery extends BrapiQuery {
    private String name;
    private String active;
    private String createdBy;
    private String createdDate;

    @Override
    public Map<String, String> getFilterValuesByBrAPIColumnName() {
        Map<String, String> filterValuesByBrAPIColumnName = new HashMap<>();

        filterValuesByBrAPIColumnName.put("trialName", getName());
        filterValuesByBrAPIColumnName.put("active", getActive());
        filterValuesByBrAPIColumnName.put("createdBy", getCreatedBy());
        filterValuesByBrAPIColumnName.put("createdDate", getCreatedDate());

        return filterValuesByBrAPIColumnName;
    }

    @Override
    public Map<String, String> getBrAPIColumnNamesByBiColumnName() {
        Map<String, String> brAPIColumnNamesByBiColumnName = new HashMap<>();

        brAPIColumnNamesByBiColumnName.put("name", "trialName");
        brAPIColumnNamesByBiColumnName.put("active", "active");
        brAPIColumnNamesByBiColumnName.put("createdBy", "createdBy");
        brAPIColumnNamesByBiColumnName.put("createdDate", "createdDate");

        return brAPIColumnNamesByBiColumnName;
    }
}
