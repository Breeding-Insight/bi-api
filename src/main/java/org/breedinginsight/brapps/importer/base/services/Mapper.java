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

package org.breedinginsight.brapps.importer.base.services;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.base.model.config.*;
import org.breedinginsight.brapps.importer.base.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.base.model.mapping.MappingField;
import org.breedinginsight.brapps.importer.base.model.config.BrAPIObject;
import org.breedinginsight.brapps.importer.base.model.BrAPIImport;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class Mapper {

    private TemplateManager configManager;

    // TODO: Extra, remove these
    public static String wrongDataTypeMsg = "Column name \"%s\" must be integer type, but non-integer type provided.";
    public static String blankRequiredField = "Required field \"%s\" cannot contain empty values";
    public static String missingColumn = "Column name \"%s\" does not exist in file";
    public static String missingUserInput = "User input, \"%s\" is required";

    @Inject
    Mapper(TemplateManager configManager) {
        this.configManager = configManager;
    }

    public List<BrAPIImport> map(ImportMapping importMapping, Table data, Map<String, Object> userInput) throws ValidatorException {

        ValidationErrors validationErrors = new ValidationErrors();

        if (importMapping.getMappingConfig() == null) {
            throw new NullPointerException("Mapping must be supplied");
        }

        // Map each row to a BrAPI Import object
        List<BrAPIImport> brAPIImports = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < data.rowCount(); rowIndex++) {

            // Get new template object template
            BrAPIImport brAPIImport = configManager.getTemplate(importMapping.getImporterTemplateId()).get();

            // Run through the brapi fields and look for a match
            Field[] fields = brAPIImport.getClass().getDeclaredFields();
            processRow(brAPIImport, fields, importMapping.getMappingConfig(), data, rowIndex, userInput, validationErrors);

            // Each element in list corresponds to row in file
            brAPIImports.add(brAPIImport);
        }

        if (validationErrors.hasErrors() ){
            throw new ValidatorException(validationErrors);
        }

        return brAPIImports;
    }

    private void processRow(Object parent, Field[] fields, List<MappingField> mappingConfig, Table importFile, Integer rowIndex,
                              Map<String, Object> userInput, ValidationErrors validationErrors) {

        Row focusRow = importFile.row(rowIndex);

        for (Field field: fields) {
            // Get metadata from field annotations
            ImportFieldType type = field.getAnnotation(ImportFieldType.class);
            ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class);
            ImportMappingRequired required = field.getAnnotation(ImportMappingRequired.class);

            // Skip any fields that don't have a type annotation on them
            if (type == null) continue;

            // Get mapping for column to template field
            List<MappingField> foundMappings = new ArrayList<>();
            if (mappingConfig != null) {
                foundMappings = mappingConfig.stream()
                        .filter(mappingField -> mappingField.getObjectId().equals(metadata.id())).collect(Collectors.toList());
            }

            // Check required column is present
            if (required != null && foundMappings.size() == 0) {
                validationErrors.addError(metadata.name(), HttpStatus.BAD_REQUEST, String.format(missingColumn, metadata.name()));
                continue;
            } else if (required == null && foundMappings.size() == 0) {
                return;
            }

            // Check data type
            // TODO: Validation for extra matchings found
            MappingField matchedMapping = foundMappings.get(0);
            if (type.collectTime().equals(ImportCollectTimeEnum.UPLOAD)) {
                processUserInput(parent, field, userInput);
            } else if (type.type() == ImportFieldTypeEnum.OBJECT) {
                // Process object
                try {
                    BrAPIObject brAPIObject = (BrAPIObject) field.getType().getDeclaredConstructor().newInstance();
                    processRow(brAPIObject, brAPIObject.getClass().getDeclaredFields(), matchedMapping.getMapping(),
                            importFile, rowIndex, userInput, validationErrors);

                    field.setAccessible(true);
                    field.set(parent, brAPIObject);
                    field.setAccessible(false);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            } else {
                // Process primitive
                processPrimitive(parent, focusRow, field, matchedMapping, validationErrors);
            }
        }
    }

    private void processPrimitive(Object parent, Row focusRow, Field field, MappingField matchedMapping, ValidationErrors validationErrors) {
        ImportMappingRequired required = field.getAnnotation(ImportMappingRequired.class);
        ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class);

        // If column is not required and is not passed, skip it.
        if (required == null && matchedMapping.getValue() == null) return;

        // Get mapped string
        String value = null;
        if (matchedMapping.getValue().getFileFieldName() != null) {
            // Check that the file has this name
            if (!Utilities.containsCaseInsensitive(matchedMapping.getValue().getFileFieldName(), focusRow.columnNames())) {
                validationErrors.addError(metadata.name(), HttpStatus.BAD_REQUEST,
                        String.format(missingColumn, matchedMapping.getValue().getFileFieldName()));
                return;
            }

            // Imports only parse to string. All type validations done in processor validation
            value = focusRow.getString(matchedMapping.getValue().getFileFieldName());
        } else if (matchedMapping.getValue().getConstantValue() != null) {
            value = matchedMapping.getValue().getConstantValue();
        }

        // Set our value
        if (StringUtils.isBlank(value)) value = null;
        try {
            field.setAccessible(true);
            field.set(parent, value);
            field.setAccessible(false);
        } catch (IllegalAccessException e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    private void processUserInput(Object parent, Field field, Map<String, Object> userInput) {

        if (userInput == null) return;

        ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class);
        ImportMappingRequired required = field.getAnnotation(ImportMappingRequired.class);

        String fieldId = metadata.id();
        if (!userInput.containsKey(fieldId) && required != null) {
            // Let the processor through validations for the user input
            return;
        } else if (userInput.containsKey(fieldId)) {
            String value = userInput.get(fieldId).toString();
            try {
                field.setAccessible(true);
                field.set(parent, value);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }
    }
}
