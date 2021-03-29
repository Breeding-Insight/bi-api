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

package org.breedinginsight.brapps.importer.model.imports;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.breedinginsight.brapps.importer.model.base.Cross;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.base.ObservationUnit;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import tech.tablesaw.api.Table;

import javax.inject.Inject;

@Getter
@Setter
@NoArgsConstructor
@ImportMetadata(id="PedigreeImport", name="Pedigree Import",
        description = "This import is used to create a pedigree history by importing germplasm and observation units.")
public class GermplasmImport implements BrAPIImport {
    private BrAPIProvider brAPIProvider;

    @ImportType(type = ImportFieldType.OBJECT)
    private Germplasm germplasm;

    @ImportType(type = ImportFieldType.OBJECT)
    private ObservationUnit observationUnit;

    @ImportType(type = ImportFieldType.OBJECT)
    private Cross cross;

    //TODO: Make an @ImportIgnore annotation

    @Inject
    public PedigreeImport(BrAPIProvider brAPIProvider) {
        this.brAPIProvider = brAPIProvider;
    }
    @Override
    public void process(BrAPIImport brAPIImport, Table data) {

        // Get our endpoint to push to
        ApiResponse<BrAPIObservationVariableListResponse> brApiVariables;
        try {
            brApiVariables = brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO).variablesGet(variablesRequest);
        } catch (ApiException e) {
            throw new InternalServerException("Error making BrAPI call", e);
        }

        // Check existing germplasm to see which ones exist already

        // Get a list of unique germplasm to be created

        // Check if there are observation units that exist already

        // Get a list of unique observation units to create

        // Check if there are crosses that exist already

        // Get a list of unique crosses to create
    }
}
