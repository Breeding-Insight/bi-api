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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.DynamicColumnParser;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.TIMESTAMP_REGEX;

@Singleton
@Slf4j
public class SharedPhenotypeService {

    private final SharedValidateService sharedValidateService;

    @Inject
    public SharedPhenotypeService(SharedValidateService sharedValidateService) {
        this.sharedValidateService = sharedValidateService;
    }

    /**
     * Extracts phenotypes from the import context.
     *
     * @param importContext The import context containing the data, upload, and program information.
     * @return A ProcessedPhenotypeData object with the extracted phenotypes.
     */
    public ProcessedPhenotypeData extractPhenotypes(ImportContext importContext) {
        Table data = importContext.getData();
        ImportUpload upload = importContext.getUpload();
        Program program = importContext.getProgram();

        DynamicColumnParser.DynamicColumnParseResult result = DynamicColumnParser.parse(data, upload.getDynamicColumnNames());
        List<Trait> traits = sharedValidateService.verifyTraits(program.getId(), result);

        Map<String, Column<?>> timeStampColByPheno = new HashMap<>();
        //Now know timestamps all valid phenotypes, can associate with phenotype column name for easy retrieval
        for (Column<?> tsColumn : result.getTimestampCols()) {
            timeStampColByPheno.put(tsColumn.name().replaceFirst(TIMESTAMP_REGEX, StringUtils.EMPTY), tsColumn);
        }

        return ProcessedPhenotypeData.builder()
                .referencedTraits(traits)
                .phenotypeCols(result.getPhenotypeCols())
                .timeStampColByPheno(timeStampColByPheno)
                .build();
    }

}
