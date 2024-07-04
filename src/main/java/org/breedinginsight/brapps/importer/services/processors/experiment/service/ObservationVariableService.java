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

package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.http.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import tech.tablesaw.columns.Column;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ObservationVariableService {
    private final OntologyService ontologyService;

    @Inject
    public ObservationVariableService(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    /**
     * Fetches traits by name for the given set of variable names and program.
     *
     * This method fetches all stored traits for the specified program and filters them based on the set of variable names provided.
     * It ensures that all requested observation variables are present and returns a list of matching traits.
     * If any observation variables are missing, it throws an IllegalStateException with the missing variable names.
     *
     * @param varNames a set of variable names to fetch traits for
     * @param program the program for which traits are fetched
     * @return a list of traits filtered by the provided variable names
     * @throws DoesNotExistException if the program or traits do not exist
     * @throws IllegalStateException if any requested observation variables are missing
     */
    public List<Trait> fetchTraitsByName(Set<String> varNames, Program program) throws DoesNotExistException, IllegalStateException {
        List<Trait> traits = null;

        // Fetch all stored traits for the program
        List<Trait> programTraits = ontologyService.getTraitsByProgramId(program.getId(), true);

        // Only keep traits that are in the set of names
        List<String> upperCaseVarNames = varNames.stream().map(String::toUpperCase).collect(Collectors.toList());
        traits = programTraits.stream().filter(e -> upperCaseVarNames.contains(e.getObservationVariableName().toUpperCase())).collect(Collectors.toList());

        // If any requested observation variables are missing, throw an IllegalStateException
        if (varNames.size() != traits.size()) {
            Set<String> missingVarNames = new HashSet<>(varNames);
            missingVarNames.removeAll(traits.stream().map(TraitEntity::getObservationVariableName).collect(Collectors.toSet()));
            throw new IllegalStateException("Observation variables not found for name(s): " + String.join(ExpImportProcessConstants.COMMA_DELIMITER, missingVarNames));
        }

        return traits;
    }

    /**
     * Validates that each timestamp column corresponds to a phenotype column.
     *
     * This method takes a Set of observationVariableNames and a List of timestamp columns, and checks
     * if each timestamp column corresponds to a phenotype column by comparing it with the observationVariableNames.
     *
     * @param observationVariableNames A Set of observation variable names representing the phenotype columns.
     * @param timestampCols A List of timestamp columns to be validated.
     *
     * @return An Optional containing a list of ValidationErrors if there are mismatches, or an empty
     * Optional if all timestamp columns have corresponding phenotype columns.
     */
    public Optional<List<ValidationError>> validateMatchedTimestamps(Set<String> observationVariableNames,
                                                                     List<Column<?>> timestampCols) {
        Optional<List<ValidationError>> ve = Optional.empty();

        // Check that each timestamp column corresponds to a phenotype column
        List<ValidationError> valErrs = timestampCols.stream()
                .filter(col -> !(observationVariableNames.contains(col.name().replaceFirst(ExpImportProcessConstants.TIMESTAMP_REGEX, StringUtils.EMPTY))))
                .map(col -> new ValidationError(col.name().toString(), String.format("Timestamp column %s lacks corresponding phenotype column", col.name().toString()), HttpStatus.UNPROCESSABLE_ENTITY))
                .collect(Collectors.toList());

        if (valErrs.size() > 0) {
            ve = Optional.of(valErrs);
        }

        return ve;
    }
}
