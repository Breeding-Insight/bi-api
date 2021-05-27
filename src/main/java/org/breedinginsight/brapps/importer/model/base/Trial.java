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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportFieldTypeEnum;
import org.breedinginsight.brapps.importer.model.config.ImportMappingRequired;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Trial", name="Trial",
        description = "A trial.")
public class Trial implements BrAPIObject {

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="trialName", name="Trial Name", description = "The name of the trial.")
    private String trialName;

    public BrAPITrial constructBrAPITrial(BrAPIProgram brapiProgram) {
        BrAPITrial trial = new BrAPITrial();
        trial.setTrialName(getTrialName());
        trial.setActive(true);
        trial.setProgramDbId(brapiProgram.getProgramDbId());
        return trial;
    }

    public Trial constructTrial() {
        Trial trial = new Trial();
        trial.setTrialName(getTrialName());
        return trial;
    }
}
