package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.jooq.tools.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class ListQuery extends BrapiQuery {
    private String listType;
    private String name;
    private String description;
    private String size;
    private String dateCreated;
    private String ownerName;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (!StringUtils.isBlank(getListType())) {
            filters.add(constructFilterRequest("type", getListType()));
        }
        if (!StringUtils.isBlank(getName())) {
            filters.add(constructFilterRequest("name", getName()));
        }
        if (!StringUtils.isBlank(getDescription())) {
            filters.add(constructFilterRequest("description", getDescription()));
        }
        if (!StringUtils.isBlank(getSize())) {
            filters.add(constructFilterRequest("size", getSize()));
        }
        if (!StringUtils.isBlank(getDateCreated())) {
            filters.add(constructFilterRequest("dateCreated", getDateCreated()));
        }
        if (!StringUtils.isBlank(getOwnerName())) {
            filters.add(constructFilterRequest("ownerName", getOwnerName()));
        }
        return new SearchRequest(filters);
    }
}