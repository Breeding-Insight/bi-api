package org.breedinginsight.utilities.response.mappers;

import com.google.gson.JsonObject;
import lombok.Getter;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.brapi.v1.model.ObservationVariable;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Singleton
public class GermplasmQueryMapper extends AbstractQueryMapper {

    private final String defaultSortField = "accessionNumber";
    private final SortOrder defaultSortOrder = SortOrder.ASC;

    private final Map<String, Function<BrAPIGermplasm, ?>> fields;

    public GermplasmQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("importEntryNumber", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER) ?
                        germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER).getAsString() :
                        null),
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
                Map.entry("createdDate", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CREATED_DATE) ?
                                germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CREATED_DATE).getAsString() :
                                null),
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
