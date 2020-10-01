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

package org.breedinginsight;

import com.google.gson.JsonArray;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    public static void checkStringSorting(JsonArray data, String field, SortOrder sortOrder) {

        for (int i = 0; i < data.size() - 2; i++){
            String firstValue = data.get(i).getAsJsonObject().get(field).getAsString();
            String secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsString();

            if (firstValue.compareTo(secondValue) == 0){
                continue;
            }
            
            if (sortOrder == SortOrder.ASC) {
                assertEquals(true, firstValue.compareTo(secondValue) < 0, "Incorrect sorting");
            } else {
                assertEquals(true, firstValue.compareTo(secondValue) > 0, "Incorrect sorting");
            }

        }

    }

    public static void checkNumericSorting(JsonArray data, String field, SortOrder sortOrder) {

        for (int i = 0; i < data.size() - 2; i++){
            if (!data.get(i).getAsJsonObject().has(field) || !data.get(i + 1).getAsJsonObject().has(field)) {
                continue;
            }
            Float firstValue = data.get(i).getAsJsonObject().get(field).getAsFloat();
            Float secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsFloat();

            if (firstValue.compareTo(secondValue) == 0){
                continue;
            }

            if (sortOrder == SortOrder.ASC) {
                assertEquals(true, firstValue.compareTo(secondValue) < 0, "Incorrect sorting");
            } else {
                assertEquals(true, firstValue.compareTo(secondValue) > 0, "Incorrect sorting");
            }
        }

    }

    public static void checkDateSorting(JsonArray data, String field, SortOrder sortOrder) {

        for (int i = 0; i < data.size() - 2; i++){
            String firstValue = data.get(i).getAsJsonObject().get(field).getAsString();
            String secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsString();
            OffsetDateTime firstDate = OffsetDateTime.parse(firstValue);
            OffsetDateTime secondDate = OffsetDateTime.parse(secondValue);

            if (firstDate.compareTo(secondDate) == 0){
                continue;
            }

            if (sortOrder == SortOrder.ASC){
                assertEquals(true, firstDate.compareTo(secondDate) < 0, "Incorrect sorting");
            } else {
                assertEquals(true, firstDate.compareTo(secondDate) > 0, "Incorrect sorting");
            }
        }

    }
}
