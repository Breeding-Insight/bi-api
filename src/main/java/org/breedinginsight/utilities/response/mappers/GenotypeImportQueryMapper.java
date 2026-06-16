/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
                Map.entry("sampleSubmissionId", GenotypeImportDetails::getSampleSubmissionId),
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