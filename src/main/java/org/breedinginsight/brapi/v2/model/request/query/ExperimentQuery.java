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
public class ExperimentQuery extends BrapiQuery {
    private String name;
    private String active;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getName())) {
            filters.add(constructFilterRequest("name", getName()));
        }
        if (!StringUtils.isBlank(getActive())) {
            filters.add(constructFilterRequest("active", getActive()));
        }
        return new SearchRequest(filters);
    }
}
