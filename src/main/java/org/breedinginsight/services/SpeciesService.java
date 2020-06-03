/*
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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

package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.model.Species;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class SpeciesService {

    @Inject
    private SpeciesDAO dao;

    public List<Species> getAll() {
        List<SpeciesEntity> speciesEntities = dao.findAll();

        List<Species> species = new ArrayList<>();
        for (SpeciesEntity speciesEntity: speciesEntities){
            species.add(new Species(speciesEntity));
        }

        return species;
    }

    public Optional<Species> getById(UUID speciesId) {

        SpeciesEntity species = dao.fetchOneById(speciesId);

        if (species == null) {
            return Optional.empty();
        }

        return Optional.of(new Species(species));
    }

    public boolean exists(UUID speciesId){
        return dao.existsById(speciesId);
    }
}
