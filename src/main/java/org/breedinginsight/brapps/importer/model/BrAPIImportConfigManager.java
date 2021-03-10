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

package org.breedinginsight.brapps.importer.model;

import io.micronaut.context.annotation.Context;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.ImportMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportConfig;
import org.breedinginsight.brapps.importer.model.config.ImportFieldConfig;
import org.breedinginsight.brapps.importer.model.config.ImportRelationOptionConfig;
import org.reflections.Reflections;

import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

@Context
@Singleton
public class BrAPIImportConfigManager {

    private Map<String, Class> brAPIImportsMap;

    BrAPIImportConfigManager() {
        // Get all imports
        brAPIImportsMap = new HashMap<>();
        Reflections reflections = new Reflections("org.breedinginsight");
        Set<Class<?>> brAPIImports = reflections.getTypesAnnotatedWith(ImportMetadata.class);
        for (Class brAPIImport: brAPIImports) {
            ImportMetadata metadata = (ImportMetadata) brAPIImport.getAnnotation(ImportMetadata.class);
            if (metadata == null) throw new InternalServerException("BrAPI File import config set up incorrectly.");
            brAPIImportsMap.put(metadata.id(), brAPIImport);
        }
    }

    public List<ImportConfig> getAllTypeConfigs() {
        List<ImportConfig> configs = new ArrayList<>();
        for (Class brAPIImport: brAPIImportsMap.values()){
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
        List<ImportFieldConfig> importObjectConfigs = new ArrayList<>();
        for (Field objectField: objectFields) {
            // Recurses down to population config
            ImportFieldConfig objectConfig = getObjectField(objectField);
            importObjectConfigs.add(objectConfig);
        }

        importConfig.setFields(importObjectConfigs);
        return importConfig;
    }

    public Optional<BrAPIImport> getTypeConfigById(String importTypeId) {

        if (brAPIImportsMap.containsKey(importTypeId)) {
            Class<?> importClass = brAPIImportsMap.get(importTypeId);
            BrAPIImport brAPIImport;
            try {
                brAPIImport = (BrAPIImport) importClass.getDeclaredConstructor(null).newInstance();
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new InternalServerException(e.toString());
            }

            return Optional.of(brAPIImport);
        } else {
            return Optional.empty();
        }
    }

    private ImportFieldConfig getObjectField(Field field) {

        ImportFieldConfig fieldConfig = constructFieldConfig(field);

        ImportType fieldType = field.getAnnotation(ImportType.class);

        // Dive deeper if necessary
        if (fieldType.type() == ImportFieldType.OBJECT) {
            List<Field> fields = Arrays.asList(field.getType().getDeclaredFields());
            List<ImportFieldConfig> subFieldConfigs = fields.stream().map(subField -> getObjectField(subField)).collect(Collectors.toList());
            fieldConfig.setFields(subFieldConfigs);
        } else if (fieldType.type() == ImportFieldType.LIST) {
            List<Field> fields = Arrays.asList(fieldType.clazz().getDeclaredFields());
            List<ImportFieldConfig> subFieldConfigs = fields.stream().map(subField -> getObjectField(subField)).collect(Collectors.toList());
            fieldConfig.setFields(subFieldConfigs);
        }
        else if (fieldType.type() == ImportFieldType.RELATIONSHIP) {
            fieldConfig = constructRelationField(field);
        }

        return fieldConfig;
    }

    private ImportFieldConfig constructFieldConfig(Field field) {

        ImportFieldMetadata fieldMetadata = field.getAnnotation(ImportFieldMetadata.class);
        ImportType type = field.getAnnotation(ImportType.class);
        Class c = type != null && type.type() == ImportFieldType.LIST ? type.clazz() : field.getType();
        ImportFieldMetadata classMetadata = (ImportFieldMetadata) c.getAnnotation(ImportFieldMetadata.class);
        ImportFieldRequired required = field.getAnnotation(ImportFieldRequired.class);
        ImportFieldMetadata metadata = fieldMetadata != null ? fieldMetadata : classMetadata;
        // This is in the case of metadata missing on a java type, e.g. String
        //TODO: Give more detail, such as which import this errored out on
        if (metadata == null) throw new InternalServerException("Metadata missing on java primitive");

        ImportFieldConfig fieldConfig = new ImportFieldConfig();
        fieldConfig.setId(metadata.id());
        fieldConfig.setName(metadata.name());
        fieldConfig.setDescription(metadata.description());
        fieldConfig.setType(type.type());
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
            if (relation.type() == ImportRelationType.DB_LOOKUP || relation.type() == ImportRelationType.DB_LOOKUP_CONSTANT_VALUE) {
                relationConfig.setImportFields(relation.importFields());
            }

            relationConfigs.add(relationConfig);
        }

        fieldConfig.setRelationOptions(relationConfigs);
        return fieldConfig;
    }
}
