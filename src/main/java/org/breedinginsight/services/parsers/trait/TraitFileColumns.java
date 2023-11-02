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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum TraitFileColumns {

    NAME("Name"),
    SYNONYMS("Synonyms"),
    TRAIT_ENTITY("Trait entity"),
    TRAIT_ATTRIBUTE("Trait attribute"),
    DESCRIPTION("Description"),
    STATUS("Status"),
    METHOD_DESCRIPTION("Method description"),
    METHOD_CLASS("Method class"),
    METHOD_FORMULA("Method formula"),
    SCALE_NAME("Units"),
    SCALE_CLASS("Scale class"),
    SCALE_DECIMAL_PLACES("Scale decimal places"),
    SCALE_LOWER_LIMIT("Scale lower limit"),
    SCALE_UPPER_LIMIT("Scale upper limit"),
    SCALE_CATEGORIES("Scale categories"),
    TAGS("Tags"),
    FULL_NAME("Full name"),
    TERM_TYPE("Term Type");


    private String value;

    TraitFileColumns(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static Set<String> getColumns() {
        return Arrays.stream(TraitFileColumns.values())
                .map(value -> value.toString())
                .collect(Collectors.toSet());
    }
}