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

package org.breedinginsight.brapi.v2.model.request.query;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyQueryUnitTest {

    @Test
    void exposesAllSupportedStudyFiltersUsingBrAPIColumnNames() throws Exception {
        StudyQuery query = new StudyQuery();
        setField(query, "studyType", "Phenotyping");
        setField(query, "locationDbId", "location-1");
        setField(query, "studyCode", "study-code");
        setField(query, "studyPUI", "study-pui");
        setField(query, "commonCropName", "maize");
        setField(query, "trialDbId", "trial-1");
        setField(query, "studyDbId", "study-1");
        setField(query, "studyName", "Environment 1");
        setField(query, "externalReferenceSource", "breedinginsight.org/studies");
        setField(query, "externalReferenceId", "environment-1");

        assertEquals(Map.ofEntries(
                Map.entry("studyType", "Phenotyping"),
                Map.entry("locationDbId", "location-1"),
                Map.entry("studyCode", "study-code"),
                Map.entry("studyPUI", "study-pui"),
                Map.entry("commonCropName", "maize"),
                Map.entry("trialDbId", "trial-1"),
                Map.entry("studyDbId", "study-1"),
                Map.entry("studyName", "Environment 1"),
                Map.entry("externalReferenceSource", "breedinginsight.org/studies"),
                Map.entry("externalReferenceId", "environment-1")),
                query.getFilterValuesByBrAPIColumnName());
    }

    @Test
    void mapsAllSupportedBiSortFieldsToBrAPIColumnNames() {
        assertEquals(Map.ofEntries(
                Map.entry("studyType", "studyType"),
                Map.entry("locationDbId", "locationDbId"),
                Map.entry("studyCode", "studyCode"),
                Map.entry("studyPUI", "studyPUI"),
                Map.entry("commonCropName", "commonCropName"),
                Map.entry("trialDbId", "trialDbId"),
                Map.entry("studyDbId", "studyDbId"),
                Map.entry("studyName", "studyName")),
                new StudyQuery().getBrAPIColumnNamesByBiColumnName());
    }

    private void setField(StudyQuery query, String fieldName, String value) throws Exception {
        Field field = StudyQuery.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(query, value);
    }
}
