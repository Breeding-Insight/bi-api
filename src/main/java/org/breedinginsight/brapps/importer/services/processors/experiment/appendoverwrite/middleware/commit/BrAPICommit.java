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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;

import javax.inject.Inject;

@Slf4j
@Prototype
public class BrAPICommit extends AppendOverwriteMiddleware {
    AppendOverwriteMiddleware middleware;
    @Inject
    public BrAPICommit(BrAPIDatasetCommit brAPIDatasetCommit,
                       BrAPITrialCommit brAPITrialCommit,
                       LocationCommit locationCommit,
                       BrAPIStudyCommit brAPIStudyCommit,
                       BrAPIObservationUnitCommit brAPIObservationUnitCommit,
                       BrAPIObservationCommit brAPIObservationCommit) {

        // Note: the order is important because system-generated dbIds from prior steps are used as foreign keys in
        // subsequent steps
        this.middleware = (AppendOverwriteMiddleware) AppendOverwriteMiddleware.link(
                brAPIDatasetCommit,
                brAPITrialCommit,
                locationCommit,
                brAPIStudyCommit,
                brAPIObservationUnitCommit,
                brAPIObservationCommit);
    }

    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        log.debug("starting post of experiment data to BrAPI server");

        return this.middleware.process(context);
    }
}
