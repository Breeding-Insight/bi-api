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

package org.breedinginsight.model.brapi_import;

import io.micronaut.context.annotation.Context;
import org.breedinginsight.model.brapi_import.config.*;
import org.breedinginsight.model.brapi_import.imports.ImportMetadata;
import org.breedinginsight.model.brapi_import.response.ImportConfig;
import org.breedinginsight.model.brapi_import.response.ImportFieldConfig;
import org.breedinginsight.model.brapi_import.response.ImportObjectConfig;
import org.breedinginsight.model.brapi_import.response.ImportRelationOptionConfig;
import org.reflections.Reflections;

import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Context
@Singleton
public class BrAPIImportConfigManager {

    private Set<Class<?>> brAPIImports;

    BrAPIImportConfigManager() {
        // Get all imports
        Reflections reflections = new Reflections("org.breedinginsight");
        brAPIImports = reflections.getTypesAnnotatedWith(ImportMetadata.class);
    }

    public List<ImportConfig> getAllTypeConfigs() {
        List<ImportConfig> configs = new ArrayList<>();
        for (Class brAPIImport: brAPIImports){
            configs.add(getTypeConfig(brAPIImport));
        }
        return configs;
    }

    public ImportConfig getTypeConfig(Class c) {

        Field[] objectFields = c.getDeclaredFields();
        ImportMetadata metadata = (ImportMetadata) c.getAnnotation(ImportMetadata.class);
        ImportConfig importConfig = new ImportConfig();
        importConfig.setId(metadata.id());
        importConfig.setName(metadata.name());
        importConfig.setDescription(metadata.description());

        // Construct the objects in brapi
        List<ImportObjectConfig> importObjectConfigs = new ArrayList<>();
        for (Field objectField: objectFields) {
            ImportObjectConfig objectConfig = constructObjectConfig(objectField);
            importObjectConfigs.add(objectConfig);
        }

        importConfig.setObjects(importObjectConfigs);
        return importConfig;
    }

    private ImportObjectConfig constructObjectConfig(Field objectField) {
        ImportFieldMetadata fieldMetadata = objectField.getAnnotation(ImportFieldMetadata.class);
        Class c = objectField.getType();
        ImportFieldMetadata classMetadata = (ImportFieldMetadata) c.getAnnotation(ImportFieldMetadata.class);
        ImportFieldRequired required = objectField.getAnnotation(ImportFieldRequired.class);
        ImportObjectConfig config = new ImportObjectConfig();
        // Use the default metadata on the class if their isn't anything on the field level
        config.setId(fieldMetadata != null ? fieldMetadata.id() : classMetadata.id());
        config.setName(fieldMetadata != null ? fieldMetadata.name() : classMetadata.name());
        config.setDescription(fieldMetadata != null ? fieldMetadata.description() : classMetadata.description());
        config.setRequired(required != null);

        List<ImportFieldConfig> fieldConfigs = getObjectFields(objectField.getType());
        config.setFields(fieldConfigs);

        return config;
    }

    private List<ImportFieldConfig> getObjectFields(Class c) {

        List<ImportFieldConfig> fieldConfigs = new ArrayList<>();
        Field[] fields = c.getDeclaredFields();
        // Go through the fields of the object. Might need some recursion here
        for (Field field: fields) {

            // Construct field based on type
            ImportType fieldType = field.getAnnotation(ImportType.class);
            ImportFieldConfig fieldConfig;
            if (fieldType.type() == ImportFieldType.LIST){
                fieldConfig = constructListField(field);
            } else if (fieldType.type() == ImportFieldType.RELATIONSHIP) {
                fieldConfig = constructRelationField(field);
            } else {
                fieldConfig = constructFieldConfig(field);
            }

            fieldConfigs.add(fieldConfig);
        }
        return fieldConfigs;
    }

    private ImportFieldConfig constructFieldConfig(Field field) {

        ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class);
        ImportType fieldType = field.getAnnotation(ImportType.class);
        ImportFieldRequired required = field.getAnnotation(ImportFieldRequired.class);
        ImportFieldConfig fieldConfig = new ImportFieldConfig();
        fieldConfig.setId(metadata.id());
        fieldConfig.setName(metadata.name());
        fieldConfig.setDescription(metadata.description());
        fieldConfig.setType(fieldType.type());
        fieldConfig.setRequired(required != null);
        return fieldConfig;
    }

    private ImportFieldConfig constructRelationField(Field field) {
        ImportFieldConfig fieldConfig = constructFieldConfig(field);

        ImportFieldRelations relations = field.getAnnotation(ImportFieldRelations.class);
        List<ImportRelationOptionConfig> relationConfigs = new ArrayList<>();
        for (ImportFieldRelation relation: relations.relations()) {
            ImportRelationOptionConfig relationConfig = new ImportRelationOptionConfig();
            relationConfig.setId(relation.type().getId());
            relationConfig.setName(relation.type().getName());
            relationConfig.setDescription(!relation.description().isEmpty() ? relation.description() : relation.type().getDefaultDescription());

            // Relation type specific logic
            if (relation.type() == ImportRelationType.DB_LOOKUP) {
                relationConfig.setImportFields(relation.importFields());
            }

            relationConfigs.add(relationConfig);
        }

        fieldConfig.setRelationOptions(relationConfigs);
        return fieldConfig;
    }

    private ImportFieldConfig constructListField(Field field) {
        ImportFieldConfig fieldConfig = constructFieldConfig(field);
        ImportType fieldType = field.getAnnotation(ImportType.class);
        Class listType = fieldType.clazz();

        // Get descriptions of the class
        ImportFieldMetadata classMetadata = (ImportFieldMetadata) listType.getAnnotation(ImportFieldMetadata.class);
        ImportObjectConfig listObject = new ImportObjectConfig();
        listObject.setId(classMetadata.id());
        listObject.setName(classMetadata.name());
        listObject.setDescription(classMetadata.description());
        List<ImportFieldConfig> fields = getObjectFields(listType);
        listObject.setFields(fields);

        fieldConfig.setListObject(listObject);

        return fieldConfig;
    }
}
