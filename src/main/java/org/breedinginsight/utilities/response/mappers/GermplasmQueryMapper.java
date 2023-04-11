package org.breedinginsight.utilities.response.mappers;

import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.constants.GermplasmQueryDefaults;


import javax.inject.Singleton;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Singleton
public class GermplasmQueryMapper extends AbstractQueryMapper {

    private final String defaultSortField = "accessionNumber";
    private final SortOrder defaultSortOrder = SortOrder.ASC;

    private final Map<String, Function<BrAPIGermplasm, ?>> fields;

    // The formatting to apply before filtering on createdDate.
    // Note: this does not change the DateTime format returned by the API, it only affects filtering.
    @Setter
    private String dateDisplayFormat = "yyyy-MM-dd";

    public GermplasmQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("importEntryNumber", (germplasm) ->{
                    String entryNumber = null;
                        if (germplasm.getAdditionalInfo() != null) {
                            // if additionalInfo contains the importEntryNumber key then return the value
                            if (germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER)) {
                                entryNumber = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER).getAsString();
                            }

                            // if additionalInfo has both listEntryNumbers and listId keys then return the entry number
                            // mapped to the listId
                            if (germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ENTRY_NUMBERS)
                                && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ID)) {
                                String listId = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ID).getAsString();
                                entryNumber = germplasm.getAdditionalInfo().getAsJsonObject(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ENTRY_NUMBERS).get(listId).getAsString();
                            }
                        }
                    return entryNumber;
                }),
                Map.entry("accessionNumber", BrAPIGermplasm::getAccessionNumber),
                Map.entry("defaultDisplayName", BrAPIGermplasm::getDefaultDisplayName),
                Map.entry("breedingMethod", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD) ?
                                germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD).getAsString() :
                                null),
                Map.entry("seedSource", BrAPIGermplasm::getSeedSource),
                Map.entry("pedigree", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_NAME) ?
                                germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_NAME).getAsString() :
                                null),
                Map.entry("femaleParentGID", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID) ?
                                germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID).getAsString() :
                                null),
                Map.entry("maleParentGID", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID) ?
                                germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID).getAsString() :
                                null),
                Map.entry("createdDate", (germplasm) ->{
                    String createdDate = null;
                    if (germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CREATED_DATE)) {
                        // Get the createdDate string value as it is returned by the BrAPI service.
                        String unformattedCreatedDate = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CREATED_DATE).getAsString();
                        // Format the createdDate with dateDisplayFormat before filtering.
                        createdDate = LocalDateTime
                                .parse(unformattedCreatedDate, DateTimeFormatter.ofPattern(GermplasmQueryDefaults.DEFAULT_DATETIME_FORMAT))
                                .format(DateTimeFormatter.ofPattern(dateDisplayFormat));
                    }
                    return createdDate;
                }),
                Map.entry("createdByUserName", (germplasm) ->
                            germplasm.getAdditionalInfo() != null
                                && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CREATED_BY)
                                && germplasm.getAdditionalInfo().getAsJsonObject(BrAPIAdditionalInfoFields.CREATED_BY).has(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME) ?
                                germplasm.getAdditionalInfo().getAsJsonObject(BrAPIAdditionalInfoFields.CREATED_BY).get(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME).getAsString() :
                                null),
                Map.entry("synonyms", (germplasm) ->
                        germplasm.getSynonyms() != null ?
                                germplasm.getSynonyms().stream().map((synonym) -> synonym.getSynonym())
                                .collect(Collectors.toList()) : null)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPIGermplasm, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) {
            return fields.get(fieldName);
        }
        else {
            throw new NullPointerException();
        }
    }
}
