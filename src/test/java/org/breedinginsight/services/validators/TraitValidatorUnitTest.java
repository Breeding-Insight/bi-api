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

import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.brapi.v2.phenotyping.model.BrApiScaleCategories;
import org.breedinginsight.api.model.v1.response.RowValidationErrors;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.ProgramObservationLevel;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.trait.TraitFileParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TraitValidatorUnitTest {

    @Test
    @SneakyThrows
    public void missingMethod() {

        Trait trait = new Trait();
        trait.setTraitName("Test Trait");
        trait.setDescription("A trait1");
        trait.setAbbreviations(List.of("t1", "t2").toArray(String[]::new));
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale = new Scale();
        scale.setScaleName("Test Scale");
        scale.setDataType(DataType.TEXT);
        trait.setScale(scale);

        // Trait
        trait.setTraitClass("Pheno trait");
        trait.setAttribute("leaf length");
        trait.setDefaultValue("2.0");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));


        // Scale
        trait.getScale().setValidValueMin(1);
        trait.getScale().setValidValueMax(10);
        trait.getScale().setDecimalPlaces(3);
        trait.getScale().setCategories(List.of(BrApiScaleCategories.builder().label("label1").value("value1").build(),
                BrApiScaleCategories.builder().label("label2").value("value2").build()));


        ValidationErrors validationErrors = TraitValidator.checkRequiredTraitFields(List.of(trait));

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(1, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        assertEquals(422, rowValidationErrors.getErrors().get(0).getHttpStatusCode(), "Wrong error code");
        assertEquals("method", rowValidationErrors.getErrors().get(0).getColumn(), "Wrong error column");
    }

    @Test
    @SneakyThrows
    public void missingScale() {

        Trait trait = new Trait();
        trait.setTraitName("Test Trait");
        trait.setDescription("A trait1");
        trait.setAbbreviations(List.of("t1", "t2").toArray(String[]::new));
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Method method = new Method();
        method.setMethodName("Test Method");
        trait.setMethod(method);

        // Trait
        trait.setTraitClass("Pheno trait");
        trait.setAttribute("leaf length");
        trait.setDefaultValue("2.0");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));

        // Method
        trait.getMethod().setMethodClass("Estimation");
        trait.getMethod().setDescription("A method");
        trait.getMethod().setFormula("a^2 + b^2 = c^2");

        ValidationErrors validationErrors = TraitValidator.checkRequiredTraitFields(List.of(trait));

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(1, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        assertEquals(422, rowValidationErrors.getErrors().get(0).getHttpStatusCode(), "Wrong error code");
        assertEquals("scale", rowValidationErrors.getErrors().get(0).getColumn(), "Wrong error column");
    }

    @Test
    @SneakyThrows
    public void missingMultiple() {

        Trait trait = new Trait();
        Method method = new Method();
        Scale scale = new Scale();
        trait.setScale(scale);
        trait.setMethod(method);

        ValidationErrors validationErrors = TraitValidator.checkRequiredTraitFields(List.of(trait));

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(8, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("traitName", 422);
        expectedColumns.put("description", 422);
        expectedColumns.put("programObservationLevel.name", 422);
        expectedColumns.put("method.methodName", 422);
        expectedColumns.put("method.description", 422);
        expectedColumns.put("method.methodClass", 422);
        expectedColumns.put("scale.scaleName", 422);
        expectedColumns.put("scale.dataType", 422);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (ValidationError error: rowValidationErrors.getErrors()){
            String column = error.getColumn();
            if (expectedColumns.containsKey(column)){
                assertEquals(expectedColumns.get(column), error.getHttpStatusCode(), "Wrong code was returned");
            } else {
                unknownColumnReturned = true;
            }
        }

        if (unknownColumnReturned){
            throw new AssertionFailedError("Unexpected error was returned");
        }

    }

    @Test
    @SneakyThrows
    public void dataConsistencyCheckFailure() {

        Trait trait = new Trait();
        Method method = new Method();
        method.setMethodClass("Computation");
        Scale scale = new Scale();
        scale.setDataType(DataType.ORDINAL);
        trait.setScale(scale);
        trait.setMethod(method);

        ValidationErrors validationErrors = TraitValidator.checkTraitDataConsistency(List.of(trait));

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(2, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("method.formula", 422);
        expectedColumns.put("scale.categories", 422);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (ValidationError error: rowValidationErrors.getErrors()){
            String column = error.getColumn();
            if (expectedColumns.containsKey(column)){
                assertEquals(expectedColumns.get(column), error.getHttpStatusCode(), "Wrong code was returned");
            } else {
                unknownColumnReturned = true;
            }
        }
    }

    @Test
    @SneakyThrows
    public void duplicateTraitsInFile() {

        Trait trait1 = new Trait();
        trait1.setTraitName("Test Trait");
        trait1.setAbbreviations("t1", "t2");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        Method method1 = new Method();
        method1.setMethodName("Test Method");
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        Trait trait2 = new Trait();
        trait2.setTraitName("Test Trait");
        trait2.setAbbreviations("t1", "t2");
        trait2.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale2 = new Scale();
        scale2.setScaleName("Test Scale");
        Method method2 = new Method();
        method2.setMethodName("Test Method");
        trait2.setScale(scale2);
        trait2.setMethod(method2);

        Trait trait3 = new Trait();
        trait3.setTraitName("Test Trait");
        trait3.setAbbreviations("t1", "t2");
        trait3.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale3 = new Scale();
        scale3.setScaleName("Test Scale");
        Method method3 = new Method();
        method3.setMethodName("Test Method");
        trait3.setScale(scale3);
        trait3.setMethod(method3);

        ValidationErrors validationErrors = TraitValidator.checkDuplicateTraitsInFile(List.of(trait1, trait2, trait3));

        assertEquals(3, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors trait1Error = validationErrors.getRowErrors().get(0);
        assertEquals(2, trait1Error.getErrors().size(), "Wrong number of errors");
        assertEquals(409, trait1Error.getErrors().get(0).getHttpStatusCode(), "Wrong status code");
        RowValidationErrors trait2Error = validationErrors.getRowErrors().get(0);
        assertEquals(2, trait2Error.getErrors().size(), "Wrong number of errors");
        assertEquals(409, trait2Error.getErrors().get(0).getHttpStatusCode(), "Wrong status code");
        RowValidationErrors trait3Error = validationErrors.getRowErrors().get(0);
        assertEquals(2, trait3Error.getErrors().size(), "Wrong number of errors");
        assertEquals(409, trait3Error.getErrors().get(0).getHttpStatusCode(), "Wrong status code");
    }

}
