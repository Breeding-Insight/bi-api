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
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.api.model.v1.response.RowValidationErrors;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.ProgramObservationLevel;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TraitValidatorUnitTest {

    TraitValidatorService traitValidatorService;
    @Mock
    TraitDAO traitDAO;



    @BeforeAll
    public void setup() {
        traitValidatorService = new TraitValidatorService(traitDAO);
    }

    @Test
    @SneakyThrows
    public void missingMethod() {

        Trait trait = new Trait();
        trait.setObservationVariableName("Test Trait");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale = new Scale();
        scale.setScaleName("Test Scale");
        scale.setDataType(DataType.TEXT);
        trait.setScale(scale);

        // Trait
        trait.setTraitClass("Pheno trait");
        trait.setEntity("leaf");
        trait.setAttribute("length");
        trait.setTraitDescription("testing the test trait");
        trait.setDefaultValue("2.0");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));


        // Scale
        trait.getScale().setValidValueMin(1);
        trait.getScale().setValidValueMax(10);
        trait.getScale().setDecimalPlaces(3);
        trait.getScale().setCategories(List.of(new BrAPIScaleValidValuesCategories().label("label1").value("value1"),
                                               new BrAPIScaleValidValuesCategories().label("label2").value("value2")));


        ValidationErrors validationErrors = traitValidatorService.checkRequiredTraitFields(List.of(trait), new TraitValidatorError());

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(1, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        assertEquals(400, rowValidationErrors.getErrors().get(0).getHttpStatusCode(), "Wrong error code");
        assertEquals("method", rowValidationErrors.getErrors().get(0).getField(), "Wrong error column");
    }

    @Test
    @SneakyThrows
    public void missingScale() {

        Trait trait = new Trait();
        trait.setObservationVariableName("Test Trait");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Method method = new Method();
        trait.setMethod(method);

        // Trait
        trait.setTraitClass("Pheno trait");
        trait.setEntity("leaf");
        trait.setAttribute("length");
        trait.setTraitDescription("testing the test trait");
        trait.setDefaultValue("2.0");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));

        // Method
        trait.getMethod().setMethodClass("Estimation");
        trait.getMethod().setDescription("A method");
        trait.getMethod().setFormula("a^2 + b^2 = c^2");

        ValidationErrors validationErrors = traitValidatorService.checkRequiredTraitFields(List.of(trait), new TraitValidatorError());

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(1, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        assertEquals(400, rowValidationErrors.getErrors().get(0).getHttpStatusCode(), "Wrong error code");
        assertEquals("scale", rowValidationErrors.getErrors().get(0).getField(), "Wrong error column");
    }

    @Test
    @SneakyThrows
    public void missingMultiple() {

        Trait trait = new Trait();
        Method method = new Method();
        Scale scale = new Scale();
        trait.setScale(scale);
        trait.setMethod(method);

        ValidationErrors validationErrors = traitValidatorService.checkRequiredTraitFields(List.of(trait), new TraitValidatorError());

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(8, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("observationVariableName", 400);
        expectedColumns.put("entity", 400);
        expectedColumns.put("attribute", 400);
        expectedColumns.put("traitDescription", 400);
        expectedColumns.put("programObservationLevel.name", 400);
        expectedColumns.put("method.methodClass", 400);
        expectedColumns.put("scale.scaleName", 400);
        expectedColumns.put("scale.dataType", 400);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (ValidationError error: rowValidationErrors.getErrors()){
            String column = error.getField();
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

        ValidationErrors validationErrors = traitValidatorService.checkTraitDataConsistency(List.of(trait), new TraitValidatorError());

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(2, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("method.formula", 400);
        expectedColumns.put("scale.categories", 400);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (ValidationError error: rowValidationErrors.getErrors()){
            String column = error.getField();
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
    public void dataInsufficientCategoriesFailure() {

        Trait trait = new Trait();
        Method method = new Method();
        method.setMethodClass("Computation");
        Scale scale = new Scale();
        scale.setScaleName("Test Scale");
        scale.setDataType(DataType.ORDINAL);
        trait.setScale(scale);
        trait.setMethod(method);
        trait.getScale().setValidValueMin(1);
        trait.getScale().setValidValueMax(10);
        trait.getScale().setDecimalPlaces(3);
        trait.getScale().setCategories(List.of(new BrAPIScaleValidValuesCategories().label("label1").value("value1")));

        ValidationErrors validationErrors = traitValidatorService.checkTraitDataConsistency(List.of(trait), new TraitValidatorError());

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(2, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("method.formula", 400);
        expectedColumns.put("scale.categories", 422);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (ValidationError error: rowValidationErrors.getErrors()){
            String column = error.getField();
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
    public void duplicateTraitsInFile() {

        Trait trait1 = new Trait();
        trait1.setObservationVariableName("Test Trait");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        Trait trait2 = new Trait();
        trait2.setObservationVariableName("Test Trait");
        trait2.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale2 = new Scale();
        scale2.setScaleName("Test Scale");
        Method method2 = new Method();
        trait2.setScale(scale2);
        trait2.setMethod(method2);

        Trait trait3 = new Trait();
        trait3.setObservationVariableName("Test Trait");
        trait3.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale3 = new Scale();
        scale3.setScaleName("Test Scale");
        Method method3 = new Method();
        trait3.setScale(scale3);
        trait3.setMethod(method3);

        ValidationErrors validationErrors = traitValidatorService.checkDuplicateTraitsInFile(List.of(trait1, trait2, trait3), new TraitValidatorError());

        assertEquals(3, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors trait1Error = validationErrors.getRowErrors().get(0);
        assertEquals(1, trait1Error.getErrors().size(), "Wrong number of errors");
        assertEquals(409, trait1Error.getErrors().get(0).getHttpStatusCode(), "Wrong status code");
        RowValidationErrors trait2Error = validationErrors.getRowErrors().get(0);
        assertEquals(1, trait2Error.getErrors().size(), "Wrong number of errors");
        assertEquals(409, trait2Error.getErrors().get(0).getHttpStatusCode(), "Wrong status code");
        RowValidationErrors trait3Error = validationErrors.getRowErrors().get(0);
        assertEquals(1, trait3Error.getErrors().size(), "Wrong number of errors");
        assertEquals(409, trait3Error.getErrors().get(0).getHttpStatusCode(), "Wrong status code");
    }

    @Test
    @SneakyThrows
    public void charLimitExceeded() {

        Trait trait = new Trait();
        trait.setObservationVariableName("OverTwelveChar");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale = new Scale();
        scale.setScaleName("Test Scale");
        Method method = new Method();
        trait.setScale(scale);
        trait.setMethod(method);

        // Trait
        trait.setTraitClass("Pheno trait");
        trait.setEntity("This is over 30 characters error");
        trait.setAttribute("This is over 30 characters error");
        trait.setTraitDescription("testing the test trait");
        trait.setDefaultValue("2.0");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));

        // Method
        trait.getMethod().setMethodClass("Estimation");
        trait.getMethod().setDescription("This is over 30 characters error");
        trait.getMethod().setFormula("a^2 + b^2 = c^2");

        ValidationErrors validationErrors = traitValidatorService.checkTraitFieldsLength(List.of(trait), new TraitValidatorError());

        assertEquals(1, validationErrors.getRowErrors().size(), "Wrong number of row errors returned");
        RowValidationErrors rowValidationErrors = validationErrors.getRowErrors().get(0);
        assertEquals(4, rowValidationErrors.getErrors().size(), "Wrong number of errors for row");

        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("observationVariableName", 422);
        expectedColumns.put("entity", 422);
        expectedColumns.put("attribute", 422);
        expectedColumns.put("method.description", 422);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (ValidationError error: rowValidationErrors.getErrors()){
            String column = error.getField();
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


}
