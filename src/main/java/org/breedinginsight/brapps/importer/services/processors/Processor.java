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
package org.breedinginsight.brapps.importer.services.processors;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.ValidatorException;
import tech.tablesaw.api.Table;

import java.util.List;
import java.util.Map;

public interface Processor {

    /**
     * Given importRows, get all existing objects in the brapi service. First phase of processing followed by process.
     * @param importRows
     * @param program
     * @throws ValidatorException
     */
    void getExistingBrapiData(List<BrAPIImport> importRows, Program program) throws ValidatorException, ApiException;

    /**
     * Update mappedBrAPIImport mapping with PendingImport data for brapi object based on new and existing objects.
     * Return stats on number of new & existing objects
     *
     * @param upload
     * @param importRows
     * @param mappedBrAPIImport
     * @param data
     * @param program
     * @return
     * @throws ValidatorException
     */
    Map<String, ImportPreviewStatistics> process(ImportUpload upload, List<BrAPIImport> importRows,
                                                 Map<Integer, PendingImport> mappedBrAPIImport, Table data,
                                                 Program program, User user, boolean commit)
            throws Exception;

    /**
     * Given mapped brapi import with updates from prior dependencies, check if have everything needed
     * prior to posting objects and throw a validatorException if not
     * @param mappedBrAPIImport
     * @throws ValidatorException
     */
    void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException;

    /**
     * Given mapped brapi import with updates from prior dependencies, post complete brapi objects
     * @param mappedBrAPIImport
     * @param program
     * @throws ValidatorException
     */
    void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) throws ValidatorException;

    /**
     * Provide a human readable name that should just be the name of the import object the processor works with
     * @return
     */
    String getName();

}
