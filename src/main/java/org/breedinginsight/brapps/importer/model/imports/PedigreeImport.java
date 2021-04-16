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

import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIGermplasmAttribute;
import org.brapi.v2.model.germ.BrAPIGermplasmAttributeValue;
import org.brapi.v2.model.germ.request.BrAPIGermplasmAttributeSearchRequest;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIGermplasmAttributeListResponse;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.BrAPIObservationUnitPosition;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.breedinginsight.brapps.importer.model.base.Cross;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.base.GermplasmAttribute;
import org.breedinginsight.brapps.importer.model.base.ObservationUnit;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldRequired;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;
import org.breedinginsight.brapps.importer.services.BrAPIQueryService;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.beans.Transient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@ImportMetadata(id="PedigreeImport", name="Pedigree Import",
        description = "This import is used to create a pedigree history by importing germplasm.")
public class PedigreeImport implements BrAPIImport {
    @ImportType(type = ImportFieldType.OBJECT)
    @ImportFieldRequired
    private Germplasm germplasm;

    @ImportType(type = ImportFieldType.OBJECT)
    private Cross cross;

    //TODO: Make an @ImportIgnore annotation
}
