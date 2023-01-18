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

package org.breedinginsight.brapps.importer.model.imports;

import lombok.Getter;

/**
 Defines Ontology Term Type backend and user readable values for easy translation
 */
@Getter
public enum TermTypeTranslator {
    PHENOTYPE("Phenotype"),

    GERM_ATTRIBUTE("Germplasm Attribute"),

    GERM_PASSPORT("Germplasm Passport");

    public String userDisplay;

    TermTypeTranslator(String userDisplay) {
        this.userDisplay = userDisplay;
    }

    public static String nameFromUserDisplay(String userDisplay){
        for (TermTypeTranslator term: values()){
            if (term.userDisplay.equals(userDisplay)){
                return term.name();
            }
        }
        throw new IllegalArgumentException();
    }
}
