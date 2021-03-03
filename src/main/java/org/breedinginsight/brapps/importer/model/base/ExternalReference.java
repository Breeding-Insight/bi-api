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

package org.breedinginsight.brapps.importer.model.base;

import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;

@ImportFieldMetadata(id="ExternalReference", name="External Reference",
        description = "An object that tracks connections to external data sets.")
public class ExternalReference {

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="referenceID", name="External Reference ID",
            description = "An id to an external reference. This is commonly used to save original ids of datasets that are imported, for later reference.")
    private String referenceID;

    @ImportType(type=ImportFieldType.TEXT)
    @ImportFieldMetadata(id="referenceSource", name="External Reference Source",
            description = "This describes the source of the external reference.")
    private String referenceSource;
}
