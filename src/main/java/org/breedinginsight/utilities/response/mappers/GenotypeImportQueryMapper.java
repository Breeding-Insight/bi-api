package org.breedinginsight.utilities.response.mappers;

import lombok.Getter;
import org.breedinginsight.model.GenotypeImportDetails;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Getter
@Singleton
public class GenotypeImportQueryMapper extends AbstractQueryMapper<GenotypeImportDetails> {

    private final Map<String, Function<GenotypeImportDetails, ?>> fields;

    public GenotypeImportQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("projectNameForSampleSubmission", GenotypeImportDetails::getProjectNameForSampleSubmission),
                Map.entry("sampleSubmissionCreatedBy", GenotypeImportDetails::getSampleSubmissionCreatedBy),
                Map.entry("genotypingFileName", GenotypeImportDetails::getGenotypingFileName),
                Map.entry("genotypingImportDate", GenotypeImportDetails::getGenotypingImportDate),
                Map.entry("genotypingImportBy", GenotypeImportDetails::getGenotypingImportBy)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<GenotypeImportDetails, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}