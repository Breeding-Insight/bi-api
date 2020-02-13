package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.daos.ProgramDao;
import org.breedinginsight.daos.UserDao;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class ProgramService {

    @Inject
    private ProgramDao dao;
}
