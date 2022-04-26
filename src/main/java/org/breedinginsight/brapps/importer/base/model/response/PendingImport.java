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

package org.breedinginsight.brapps.importer.base.model.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.base.model.response.PendingImportObject;

@Getter
@Setter
@NoArgsConstructor
public class PendingImport {

    private PendingImportObject<BrAPIGermplasm> germplasm;
    private PendingImportObject<BrAPITrial> trial;
    private PendingImportObject<BrAPILocation> location;
    private PendingImportObject<BrAPIStudy> study;
    private PendingImportObject<BrAPIObservationUnit> observationUnit;
    private PendingImportObject<BrAPIObservation> observation;

}
