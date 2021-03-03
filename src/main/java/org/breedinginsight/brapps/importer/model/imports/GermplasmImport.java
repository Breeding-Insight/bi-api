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

import org.breedinginsight.brapps.importer.model.base.Cross;
import org.breedinginsight.brapps.importer.model.base.Germplasm;


@ImportMetadata(id="GermplasmImport", name="Germplasm",
        description = "This import is used to import germplasm and to create crosses to connect germplasm together in order to construct a pedigree.")
public class GermplasmImport implements BrAPIImport {

    private Germplasm germplasm;
    private Cross cross;
}
