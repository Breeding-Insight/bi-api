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
package org.breedinginsight.services.validators;

import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class TraitValidator {

    public static void checkRequiredTraitFields(Trait trait) throws UnprocessableEntityException {

        Method method = trait.getMethod();
        Scale scale = trait.getScale();

        if (method == null) {
            throw new UnprocessableEntityException("Missing method");
        }

        if (scale == null) {
            throw new UnprocessableEntityException("Missing scale");
        }

        if (isBlank(trait.getTraitName())) {
            throw new UnprocessableEntityException("Missing trait name");
        }
        if (isBlank(trait.getDescription())) {
            throw new UnprocessableEntityException("Missing trait description");
        }
        if (trait.getProgramObservationLevel() == null || isBlank(trait.getProgramObservationLevel().getName())) {
            throw new UnprocessableEntityException("Missing trait level");
        }
        if (isBlank(method.getMethodName())) {
            throw new UnprocessableEntityException("Missing method name");
        }
        if (isBlank(method.getDescription())) {
            throw new UnprocessableEntityException("Missing method description");
        }
        if (isBlank(method.getMethodClass())) {
            throw new UnprocessableEntityException("Missing method class");
        }
        if (isBlank(scale.getScaleName())) {
            throw new UnprocessableEntityException("Missing scale name");
        }
        if (scale.getDataType() == null) {
            throw new UnprocessableEntityException("Missing scale type");
        }
    }

    public static void checkTraitDataConsistency(Trait trait) throws UnprocessableEntityException {

        Method method = trait.getMethod();
        Scale scale = trait.getScale();

        if (method != null && method.getMethodClass().equals(Method.COMPUTATION_TYPE)) {
            if (isBlank(method.getFormula())) {
                throw new UnprocessableEntityException("Missing formula for Computation method");
            }
        }

        if (scale != null && scale.getDataType() == DataType.ORDINAL) {
            if (scale.getCategories() == null || scale.getCategories().isEmpty()) {
                throw new UnprocessableEntityException("Missing categories for Ordinal scale");
            }
        }
    }
}
