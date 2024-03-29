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
public class ListQuery extends BrapiQuery {
    private String listType;
    private String name;
    private String description;
    private String size;
    private String externalReferenceSource;
    private String externalReferenceId;
    private String dateCreated;
    private String ownerName;
    // This is a meta-parameter, it describes the display format of any date fields.
    private String dateDisplayFormat;

    public SearchRequest constructSearchRequest() {
        List<FilterRequest> filters = new ArrayList<>();
        if (getListType() != null) {
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
        if (!StringUtils.isBlank(getExternalReferenceSource())) {
            filters.add(constructFilterRequest("externalReferenceSource", getExternalReferenceSource()));
        }
        if (!StringUtils.isBlank(getExternalReferenceId())) {
            filters.add(constructFilterRequest("externalReferenceId", getExternalReferenceId()));
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
