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

package org.breedinginsight.brapps.importer.model.config;

import lombok.Getter;

@Getter
public enum ImportRelationType {
    DB_LOOKUP("DB_LOOKUP", "Database and Current File Lookup", "Select a value to search the values on. We will search for matches within the file, and within the database. If we can't find a relationship, we'll ignore the link."),
    FILE_LOOKUP("FILE_LOOKUP", "File Lookup", "Specify a column with a unique id that references a matching id within this file. If the object isn't found we will throw an error."),
    DB_LOOKUP_CONSTANT_VALUE("DB_LOOKUP_CONSTANT_VALUE", "Manual Input Database Lookup", "Manually enter a value to be searched for in hte database. If the object isn't found, we will throw an error.");

    private String id;
    private String name;
    private String defaultDescription;

    ImportRelationType(String id, String name, String defaultDescription) {
        this.id = id;
        this.name = name;
        this.defaultDescription = defaultDescription;
    }
}
