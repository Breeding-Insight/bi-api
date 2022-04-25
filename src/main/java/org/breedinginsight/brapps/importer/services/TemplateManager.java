package org.breedinginsight.brapps.importer.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.base.BrAPIObject;
import org.breedinginsight.brapps.importer.model.config.ImportConfigMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportFieldTypeEnum;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.mapping.MappingField;
import org.breedinginsight.brapps.importer.model.mapping.MappingValue;
import org.breedinginsight.brapps.importer.services.processors.ExperimentProcessor;
import org.breedinginsight.brapps.importer.services.processors.GermplasmProcessor;
import org.breedinginsight.brapps.importer.services.processors.Processor;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides processors for the imports. Accesses BrAPIImport classes through the processors getSupportedImport object
 */
@Slf4j
@Singleton
public class TemplateManager {
    private List<Processor> processors;

    TemplateManager(Provider<GermplasmProcessor> germplasmProcessorProvider, Provider<ExperimentProcessor> experimentProcessorProvider) {
        this.processors = List.of(
                experimentProcessorProvider.get(),
                germplasmProcessorProvider.get()
        );

        // TODO: Check that all of the imports and processors are configured correctly
        // Check ids exist
        // Check that supported import is not null
    }

    public Optional<Processor> getProcessor(Class<? extends BrAPIImport> importClass) {
        for (Processor processor: processors) {
            if (processor.getSupportedImport().getClass().equals(importClass)) {
                return Optional.of(processor);
            }
        }
        return Optional.empty();
    }

    public Optional<BrAPIImport> getTemplate(int importId) {
        // Iterate through all of the brapi imports
        for (Processor processor: processors) {
            Class importClass = processor.getSupportedImport().getClass();
            ImportConfigMetadata metadata = (ImportConfigMetadata) importClass.getAnnotation(ImportConfigMetadata.class);
            if (importId == metadata.dbId()) {
                return Optional.of(processor.getSupportedImport());
            }
        }
        return Optional.empty();
    }

    public ImportMapping generateMappingForTemplate(Class<? extends BrAPIImport> importClass) {

        ImportMapping importMapping = new ImportMapping();
        importMapping.setSaved(false);
        Field[] fields = importClass.getDeclaredFields();
        importMapping.setMappingConfig(processFields(fields));
        return importMapping;
    }

    private List<MappingField> processFields(Field[] fields) {

        List<MappingField> mappingFields = new ArrayList<>();
        for (Field field: fields) {

            // If object recursively generate
            ImportFieldType type = field.getAnnotation(ImportFieldType.class);
            ImportFieldMetadata metadata = field.getAnnotation(ImportFieldMetadata.class);

            // If not a brapi import field, skip it
            if (type == null) continue;

            // Create new mapping field
            MappingField mappingField = new MappingField();
            mappingField.setObjectId(metadata.id());

            if (type.type() == ImportFieldTypeEnum.OBJECT) {
                Field[] subFields = field.getType().getDeclaredFields();
                mappingField.setMapping(processFields(subFields));
            } else {
                // If primitive field, add a mapping for field to field
                MappingValue value = new MappingValue();
                value.setFileFieldName(metadata.name());
                mappingField.setValue(value);
            }
            mappingFields.add(mappingField);
        }
        return mappingFields;
    }
}
