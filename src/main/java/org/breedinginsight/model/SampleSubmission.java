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

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.BrAPISample;
import org.breedinginsight.dao.db.tables.pojos.SampleSubmissionEntity;

import java.util.List;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@Introspected
@Jacksonized
//@JsonIgnoreProperties(value = {"createdBy", "updatedBy"})
public class SampleSubmission extends SampleSubmissionEntity {
    private List<BrAPIPlate> plates;
    private List<BrAPISample> samples;

    public SampleSubmission(SampleSubmissionEntity entity) {
        this.setId(entity.getId());
        this.setName(entity.getName());
        this.setSubmitted(entity.getSubmitted());
        this.setVendorOrderId(entity.getVendorOrderId());
        this.setShipmentforms(entity.getShipmentforms());
        this.setProgramId(entity.getProgramId());
        this.setCreatedAt(entity.getCreatedAt());
        this.setCreatedBy(entity.getCreatedBy());
        this.setUpdatedAt(entity.getUpdatedAt());
        this.setUpdatedBy(entity.getUpdatedBy());
    }
}
