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
package org.breedinginsight.brapps.importer.services.processors.experiment.create.model;

import lombok.*;
import org.breedinginsight.model.Trait;
import tech.tablesaw.columns.Column;

import java.util.List;
import java.util.Map;

// TODO: move to common higher level location
@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ProcessedPhenotypeData {
    private Map<String, Column<?>> timeStampColByPheno;
    private List<Trait> referencedTraits;
    private List<Column<?>> phenotypeCols;
}
