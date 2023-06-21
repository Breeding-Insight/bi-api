package org.breedinginsight.utilities.response.mappers;

import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;

import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

@Getter
@Singleton
public class ListQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<BrAPIListSummary, ?>> fields;

    // The formatting to apply before filtering on dateCreated.
    // Note: this does not change the DateTime format returned by the API, it only affects filtering.
    @Setter
    private String dateDisplayFormat = "yyyy-MM-dd";

    public ListQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name", BrAPIListSummary::getListName),
                Map.entry("description", BrAPIListSummary::getListDescription),
                Map.entry("size", BrAPIListSummary::getListSize),
                Map.entry("dateCreated", (listSummary) -> {
                    String dateCreated = null;
                    OffsetDateTime unformattedDateCreated = listSummary.getDateCreated();
                    if (unformattedDateCreated != null)
                    {
                        dateCreated = unformattedDateCreated.format(DateTimeFormatter.ofPattern(dateDisplayFormat));
                    }
                    return dateCreated;
                }),
                Map.entry("ownerName", BrAPIListSummary::getListOwnerName),
                Map.entry("type", BrAPIListSummary::getListType)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPIListSummary, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) {
            return fields.get(fieldName);
        }
        else {
            throw new NullPointerException();
        }
    }
}
