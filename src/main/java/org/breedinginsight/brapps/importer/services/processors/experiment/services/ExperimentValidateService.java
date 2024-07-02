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
package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.brapps.importer.services.processors.experiment.DynamicColumnParser.DynamicColumnParseResult;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.COMMA_DELIMITER;
import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.TIMESTAMP_REGEX;

@Singleton
@Slf4j
public class ExperimentValidateService {

    private final OntologyService ontologyService;
    private final FileMappingUtil fileMappingUtil;

    @Inject
    public ExperimentValidateService(OntologyService ontologyService, FileMappingUtil fileMappingUtil) {
        this.ontologyService = ontologyService;
        this.fileMappingUtil = fileMappingUtil;
    }

    /**
     * Verifies traits based on program ID and dynamic column parse result.
     *
     * @param programId The UUID of the program.
     * @param cols The dynamic column parse result object containing phenotype and timestamp columns.
     * @return The list of verified traits.
     * @throws HttpStatusException If ontology terms are not found or timestamp columns lack corresponding phenotype columns.
     */
    public List<Trait> verifyTraits(UUID programId, DynamicColumnParseResult cols) {
        Set<String> varNames = cols.getPhenotypeCols().stream()
                .map(Column::name)
                .collect(Collectors.toSet());
        Set<String> tsNames = cols.getTimestampCols().stream()
                .map(Column::name)
                .collect(Collectors.toSet());

        // filter out just traits specified in file
        List<Trait> filteredTraits = fetchFileTraits(programId, varNames);

        // check that all specified ontology terms were found
        if (filteredTraits.size() != varNames.size()) {
            Set<String> returnedVarNames = filteredTraits.stream()
                    .map(TraitEntity::getObservationVariableName)
                    .collect(Collectors.toSet());
            List<String> differences = varNames.stream()
                    .filter(var -> !returnedVarNames.contains(var))
                    .collect(Collectors.toList());
            //TODO convert this to a ValidationError
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ontology term(s) not found: " + String.join(COMMA_DELIMITER, differences));
        }

        // Check that each ts column corresponds to a phenotype column
        List<String> unmatchedTimestamps = tsNames.stream()
                .filter(e -> !(varNames.contains(e.replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY))))
                .collect(Collectors.toList());
        if (unmatchedTimestamps.size() > 0) {
            //TODO convert this to a ValidationError
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Timestamp column(s) lack corresponding phenotype column(s): " + String.join(COMMA_DELIMITER, unmatchedTimestamps));
        }

        // sort the verified traits to match the order of the trait columns
        List<String> phenotypeColNames = cols.getPhenotypeCols().stream().map(Column::name).collect(Collectors.toList());
        return fileMappingUtil.sortByField(phenotypeColNames, filteredTraits, TraitEntity::getObservationVariableName);
    }

    private List<Trait> fetchFileTraits(UUID programId, Collection<String> varNames) {
        try {
            Collection<String> upperCaseVarNames = varNames.stream().map(String::toUpperCase).collect(Collectors.toList());
            List<Trait> traits = ontologyService.getTraitsByProgramId(programId, true);
            // filter out just traits specified in file
            return traits.stream()
                    .filter(e -> upperCaseVarNames.contains(e.getObservationVariableName().toUpperCase()))
                    .collect(Collectors.toList());
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException(e.toString(), e);
        }
    }


}
