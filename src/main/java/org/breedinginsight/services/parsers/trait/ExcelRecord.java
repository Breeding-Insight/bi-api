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
package org.breedinginsight.services.parsers.trait;

import lombok.NonNull;
import org.apache.poi.ss.usermodel.Cell;

import java.util.Map;
import java.util.Objects;

public class ExcelRecord {

    private Map<String, Cell> values;

    public ExcelRecord(@NonNull Map<String, Cell> values) {
        this.values = values;
    }

    public Cell get(Enum<?> e) {
        return get(Objects.toString(e, (String)null));
    }

    // null if value doesn't exist otherwise value
    public Cell get(String name) {

        return values.get(name);

    }

}
