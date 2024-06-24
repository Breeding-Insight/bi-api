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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

/**
 * ExpUnitMiddleware class extends Middleware class to handle compensating transactions in the context of ExpUnitMiddlewareContext.
 */
public abstract class AppendOverwriteMiddleware extends Middleware<AppendOverwriteMiddlewareContext> {

    /**
     * Compensates for an error that occurred in the current local transaction, tagging the error and undoing the previous local transaction.
     *
     * @param context The context in which the compensation is to be performed.
     * @return True if the prior local transaction was successfully compensated, false otherwise.
     */
    @Override
    public AppendOverwriteMiddlewareContext compensate(AppendOverwriteMiddlewareContext context) {
        // tag an error if it occurred in this local transaction
        context.getAppendOverwriteWorkflowContext().getProcessError().tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context);
    }
}
