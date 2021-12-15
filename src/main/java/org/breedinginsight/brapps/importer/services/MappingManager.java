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

package org.breedinginsight.brapps.importer.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.mapping.MappingField;
import org.breedinginsight.brapps.importer.model.mapping.MappingValue;
import org.breedinginsight.brapps.importer.model.base.BrAPIObject;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.model.config.ImportRelationType.DB_LOOKUP_CONSTANT_VALUE;

@Singleton
public class MappingManager {

    private ImportConfigManager configManager;

    public static String wrongDataTypeMsg = "Column name \"%s\" must be integer type, but non-integer type provided.";
    public static String blankRequiredField = "Required field \"%s\" cannot contain empty values.";
    public static String missingColumn = "Column name \"%s\" does not exist in file";
    public static String missingUserInput = "User input, \"%s\" is required";
    public static String wrongUserInputDataType = "User input, \"%s\" must be an %s";

    @Inject
    MappingManager(ImportConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<BrAPIImport> map(ImportMapping importMapping, Table data, Map<String, Object> userInput) throws UnprocessableEntityException {
        return map(importMapping, data, userInput, true);
    }

    public List<BrAPIImport> map(ImportMapping importMapping, Table data) throws UnprocessableEntityException {
        return map(importMapping, data, null, false);
    }

    private List<BrAPIImport> map(ImportMapping importMapping, Table data, Map<String, Object> userInput, Boolean process) throws UnprocessableEntityException {

        // TODO: Need to make required checking better. Is it a required mapping, or a non-blank value in the field?
        if (importMapping.getMappingConfig() == null) {
            throw new NullPointerException("Mapping must be supplied");
        }

        // Go through the file and do the mapping
        // Validity of mapping is checked during the mapping
        List<BrAPIImport> brAPIImports = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < data.rowCount(); rowIndex++) {

            Optional<BrAPIImportService> optionalImportService = configManager.getImportServiceById(importMapping.getImportTypeId());
            if (optionalImportService.isEmpty()){
                throw new UnprocessableEntityException("Import type with that id does not exist.");
            }
            BrAPIImportService importService = optionalImportService.get();
            BrAPIImport brAPIImport = importService.getImportClass();

            // Run through the brapi fields and look for a match
            Field[] fields = brAPIImport.getClass().getDeclaredFields();
            for (Field field: fields) {
                mapField(brAPIImport, field, importMapping.getMappingConfig(), data, rowIndex, userInput, process);
            }

            brAPIImports.add(brAPIImport);
        }

        return brAPIImports;
    }

    private void mapField(Object parent, Field field, List<MappingField> mappings, Table importFile, Integer rowIndex,
                          Map<String, Object> userInput, Boolean process) throws UnprocessableEntityException {

        Row focusRow = importFile.row(rowIndex);
        // Process this field
        ImportFieldType type = field.getAnnotation(ImportFieldType.class);
        ImportFieldMetadata metadata;
        if (type == null) {
            return;
        } else if (type.type() != ImportFieldTypeEnum.LIST) {
            metadata = field.getAnnotation(ImportFieldMetadata.class) != null ?
                    field.getAnnotation(ImportFieldMetadata.class) : field.getType().getAnnotation(ImportFieldMetadata.class);
        } else {
            metadata = field.getAnnotation(ImportFieldMetadata.class) != null ?
                    field.getAnnotation(ImportFieldMetadata.class) : (ImportFieldMetadata) type.clazz().getAnnotation(ImportFieldMetadata.class);
        }

        ImportMappingRequired required = field.getAnnotation(ImportMappingRequired.class);

        // Check if it is a user input field
        if (type.collectTime().equals(ImportCollectTimeEnum.UPLOAD)) {
            // User input, check that it was passed
            if (process) {
                mapUserInputField(parent, field, userInput, type, metadata, required);
            }
            return;
        }

        List<MappingField> foundMappings = new ArrayList<>();
        if (mappings != null) {
             foundMappings = mappings.stream()
                    .filter(mappingField -> mappingField.getObjectId().equals(metadata.id())).collect(Collectors.toList());
        }

        // Check required field is present
        // TODO: Separate into validation method to add other validations later on
        if (required != null && foundMappings.size() == 0) {
            throw new UnprocessableEntityException(String.format(
                    "Required field, %s, not provided", metadata.id()));
        } else if (required == null && foundMappings.size() == 0) {
            return;
        }

        MappingField matchedMapping = foundMappings.get(0);
        if (type == null) {
            throw new InternalServerException("BrAPIObject is missing import type annotation");
        } else if (type.type() == ImportFieldTypeEnum.OBJECT) {

            Boolean objectIsEmpty = fieldObjectIsEmpty(matchedMapping);
            if (required != null && objectIsEmpty) {
                throw new UnprocessableEntityException(String.format(
                        "Required object, %s, was not mapped", metadata.id()));
            } else if (required == null && objectIsEmpty) {
                return;
            }

            BrAPIObject brAPIObject;
            try {
                brAPIObject = (BrAPIObject) field.getType().getDeclaredConstructor().newInstance();
                List<Field> fields = Arrays.asList(brAPIObject.getClass().getDeclaredFields());
                for (Field lowerField: fields) {
                    mapField(brAPIObject, lowerField, matchedMapping.getMapping(), importFile, rowIndex, userInput, process);
                }
                if (brapiObjectIsEmpty(brAPIObject)) brAPIObject = null;

                field.setAccessible(true);
                field.set(parent, brAPIObject);
                field.setAccessible(false);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new InternalServerException(e.toString(), e);
            }
        } else if (type.type() == ImportFieldTypeEnum.LIST) {
            //TODO: Current can't handle primitive types, only BrAPIObject types

            if (matchedMapping.getMapping() == null && required != null) {
                throw new UnprocessableEntityException(String.format(
                        "List field, %s, contains no entries", metadata.id()));
            } else if (matchedMapping.getMapping() == null) {
                return;
            }

            List<BrAPIObject> updatedList = new ArrayList<>();
            for (MappingField listField: matchedMapping.getMapping()){

                BrAPIObject newObject;
                try {
                    newObject = (BrAPIObject) type.clazz().getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new InternalServerException(e.toString(), e);
                }

                // Populate new object
                List<Field> fields = Arrays.asList(newObject.getClass().getDeclaredFields());
                for (Field lowerField: fields) {
                    mapField(newObject, lowerField, listField.getMapping(), importFile, rowIndex, userInput, process);
                }

                field.setAccessible(true);
                List<BrAPIObject> currentList;
                try {
                    currentList = (List<BrAPIObject>) field.get(parent);
                } catch (IllegalAccessException e) {
                    throw new InternalServerException(e.toString(), e);
                }
                if (currentList != null) updatedList.addAll(currentList);
                else updatedList.add(newObject);
            }

            try {
                field.setAccessible(true);
                field.set(parent, updatedList);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }
        } else if (type.type() == ImportFieldTypeEnum.RELATIONSHIP) {
            if (required != null && (matchedMapping.getValue() == null ||
                    matchedMapping.getValue().getRelationMap() == null)) {
                throw new UnprocessableEntityException(String.format("Relationship field %s is required", metadata.name()));
            } else if (matchedMapping.getValue() == null || matchedMapping.getValue().getRelationMap() == null) {
                return;
            } else if (matchedMapping.getValue().getRelationMap().getReference() == null &&
                    matchedMapping.getValue().getRelationMap().getTarget() == null)
            {
                throw new BadRequestException("Relationship field is not properly formatted");
            }

            MappingValue value = matchedMapping.getValue();
            MappedImportRelation relationship = new MappedImportRelation();
            relationship.setType(value.getRelationValue());
            relationship.setTargetColumn(value.getRelationMap().getTarget());
            String referenceColumn = value.getRelationMap().getReference();
            if (referenceColumn != null){
                if (relationship.getType() == DB_LOOKUP_CONSTANT_VALUE) {
                    relationship.setReferenceValue(referenceColumn);
                } else {
                    relationship.setReferenceValue(focusRow.getString(referenceColumn));
                }
            }
            if (StringUtils.isBlank(relationship.getReferenceValue())) relationship = null;
            try {
                field.setAccessible(true);
                field.set(parent, relationship);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }
        } else {
            // It is a simple type

            // Check that request field is properly formatted
            if (required != null && matchedMapping.getValue() == null) {
                throw new UnprocessableEntityException(String.format("Field %s is required", metadata.name()));
            } else if (required == null && matchedMapping.getValue() == null) {
                return;
            } else if (matchedMapping.getValue() != null &&
                    matchedMapping.getValue().getFileFieldName() == null && matchedMapping.getValue().getConstantValue() == null){
                throw new BadRequestException("Basic mapping field must have file field or constant value specified.");
            }

            // Check if the mapping passed a constant value or a mapped value
            if (matchedMapping.getValue().getFileFieldName() != null) {
                // Check that the file has this name
                if (!focusRow.columnNames().contains(matchedMapping.getValue().getFileFieldName())) {
                    throw new UnprocessableEntityException(String.format(missingColumn, matchedMapping.getValue().getFileFieldName()));
                }

                // TODO: should handle all types, not sure if better way to do this with tablesaw?
                String fileValue;
                ColumnType columnType = focusRow.getColumnType(matchedMapping.getValue().getFileFieldName());
                if (columnType == ColumnType.DOUBLE) {
                    fileValue = String.valueOf(focusRow.getDouble(matchedMapping.getValue().getFileFieldName()));
                } else if (columnType == ColumnType.INTEGER) {
                    fileValue = String.valueOf(focusRow.getInt(matchedMapping.getValue().getFileFieldName()));
                }
                else {
                    fileValue = focusRow.getString(matchedMapping.getValue().getFileFieldName());
                }

                checkFieldType(type.type(), matchedMapping.getValue().getFileFieldName(), fileValue);

                // Check non-null value
                if (required != null && fileValue.isBlank()) {
                    throw new UnprocessableEntityException(String.format(blankRequiredField,  matchedMapping.getValue().getFileFieldName()));
                }

                if (StringUtils.isBlank(fileValue)) fileValue = null;
                try {
                    field.setAccessible(true);
                    field.set(parent, fileValue);
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            } else if (matchedMapping.getValue().getConstantValue() != null){
                String value = matchedMapping.getValue().getConstantValue();
                checkFieldType(type.type(), metadata.name(), value);

                // Check non-null value
                if (required != null && value.isBlank()) {
                    throw new UnprocessableEntityException(String.format(blankRequiredField,  metadata.name()));
                }

                if (StringUtils.isBlank(value)) value = null;
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

    private void mapUserInputField(Object parent, Field field, Map<String, Object> userInput, ImportFieldType type,
                                   ImportFieldMetadata metadata, ImportMappingRequired required) throws UnprocessableEntityException {

        // Only supports user input at the top level of an object at the moment. No nested objects. Map<String, String>
        String fieldId = metadata.id();
        if (!userInput.containsKey(fieldId) && required != null) {
            throw new UnprocessableEntityException(String.format(missingUserInput, metadata.name()));
        }
        else if (required != null && userInput.containsKey(fieldId) && userInput.get(fieldId).toString().isBlank()) {
            throw new UnprocessableEntityException(String.format(missingUserInput, metadata.name()));
        }
        else if (userInput.containsKey(fieldId)) {
            String value = userInput.get(fieldId).toString();
            if (!isCorrectType(type.type(), value)) {
                throw new UnprocessableEntityException(String.format(wrongUserInputDataType, metadata.name(), type.type().toString().toLowerCase()));
            }
            try {
                field.setAccessible(true);
                field.set(parent, value);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }
    }

    private void checkFieldType(ImportFieldTypeEnum expectedType, String column, String value) throws UnprocessableEntityException {
        //TODO: Do more type checks
        if (!isCorrectType(expectedType, value)) {
            throw new UnprocessableEntityException(String.format(wrongDataTypeMsg, column));
        }
    }

    private Boolean isCorrectType(ImportFieldTypeEnum expectedType, String value) {
        if (!value.isBlank()) {
            if (expectedType == ImportFieldTypeEnum.INTEGER) {
                try {
                    Integer d = Integer.parseInt(value);
                } catch (NumberFormatException nfe) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
        A mapped object is considered null if no fields in it have been mapped
     */
    private Boolean fieldObjectIsEmpty(MappingField mappedObject) {
        if (mappedObject.getMapping() != null){
            for (MappingField mappedField: mappedObject.getMapping()) {
                if (mappedField != null &&
                        (mappedField.getValue() != null ||
                                (mappedField.getMapping() != null && mappedField.getMapping().size() > 0))
                ){
                    return false;
                }
            }
        }

        return true;
    }

    private Boolean brapiObjectIsEmpty(BrAPIObject brAPIObject) {

        if (brAPIObject == null) return true;

        Field[] fields = brAPIObject.getClass().getDeclaredFields();
        for (Field field: fields) {

            // Check the type
            ImportFieldType type = field.getAnnotation(ImportFieldType.class);
            if (type == null) continue;

            Object fieldValue;
            try {
                field.setAccessible(true);
                fieldValue = field.get(brAPIObject);
                field.setAccessible(false);
            } catch (IllegalAccessException e) {
                throw new InternalServerException(e.toString(), e);
            }

            if (type.type() == ImportFieldTypeEnum.LIST) continue;
            else if (type.type() == ImportFieldTypeEnum.OBJECT) {
                // Dive deeper
                BrAPIObject childBrAPIObject = (BrAPIObject) fieldValue;
                Boolean childObjectIsEmpty = brapiObjectIsEmpty(childBrAPIObject);
                if (!childObjectIsEmpty) return false;
            }
            else if (type.type() == ImportFieldTypeEnum.RELATIONSHIP) {
                // Check the reference value of the relationship
                MappedImportRelation relation = (MappedImportRelation) fieldValue;
                if (relation != null && !StringUtils.isBlank(relation.getReferenceValue())) return false;
            } else {
                // Check the value isn't blank
                String simpleValue = (String) fieldValue;
                if (!StringUtils.isBlank(simpleValue)) return false;
            }
        }

        return true;
    }
}
