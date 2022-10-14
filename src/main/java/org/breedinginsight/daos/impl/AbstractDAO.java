package org.breedinginsight.daos.impl;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.*;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public abstract class AbstractDAO<R extends TableRecord<R>, P, T> implements DAO<R , P, T> {

    @Getter
    private final DAO<R, P, T> jooqDAO;

    public AbstractDAO(DAO<R, P, T> jooqDAO) {
        this.jooqDAO = jooqDAO;
    }

    @Override
    public @NotNull Configuration configuration() {
        return jooqDAO.configuration();
    }

    @Override
    public @NotNull Settings settings() {
        return jooqDAO.settings();
    }

    @Override
    public @NotNull SQLDialect dialect() {
        return jooqDAO.dialect();
    }

    @Override
    public @NotNull SQLDialect family() {
        return jooqDAO.family();
    }

    @Override
    public @NotNull RecordMapper<R, P> mapper() {
        return jooqDAO.mapper();
    }

    @Override
    public void insert(P object) throws DataAccessException {
        jooqDAO.insert(object);
    }

    @Override
    public void insert(P... objects) throws DataAccessException {
        jooqDAO.insert(objects);
    }

    @Override
    public void insert(Collection<P> objects) throws DataAccessException {
        jooqDAO.insert(objects);
    }

    @Override
    public void update(P object) throws DataAccessException {
        jooqDAO.update(object);
    }

    @Override
    public void update(P... objects) throws DataAccessException {
        jooqDAO.update(objects);
    }

    @Override
    public void update(Collection<P> objects) throws DataAccessException {
        jooqDAO.update(objects);
    }

    @Override
    public void merge(P object) throws DataAccessException {
        jooqDAO.merge(object);
    }

    @Override
    public void merge(P... objects) throws DataAccessException {
        jooqDAO.merge(objects);
    }

    @Override
    public void merge(Collection<P> objects) throws DataAccessException {
        jooqDAO.merge(objects);
    }

    @Override
    public void delete(P object) throws DataAccessException {
        jooqDAO.delete(object);
    }

    @Override
    public void delete(P... objects) throws DataAccessException {
        jooqDAO.delete(objects);
    }

    @Override
    public void delete(Collection<P> objects) throws DataAccessException {
        jooqDAO.delete(objects);
    }

    @Override
    public void deleteById(T... ids) throws DataAccessException {
        jooqDAO.deleteById(ids);
    }

    @Override
    public void deleteById(Collection<T> ids) throws DataAccessException {
        jooqDAO.deleteById(ids);
    }

    @Override
    public boolean exists(P object) throws DataAccessException {
        return jooqDAO.exists(object);
    }

    @Override
    public boolean existsById(T id) throws DataAccessException {
        return jooqDAO.existsById(id);
    }

    @Override
    public long count() throws DataAccessException {
        return jooqDAO.count();
    }

    @Override
    public @NotNull List<P> findAll() throws DataAccessException {
        return jooqDAO.findAll();
    }

    @Override
    public @Nullable P findById(T id) throws DataAccessException {
        return jooqDAO.findById(id);
    }

    @Override
    public @NotNull Optional<P> findOptionalById(T id) throws DataAccessException {
        return jooqDAO.findOptionalById(id);
    }

    @Override
    public @NotNull <Z> List<P> fetch(Field<Z> field, Z... values) throws DataAccessException {
        return jooqDAO.fetch(field, values);
    }

    @Override
    public @NotNull <Z> List<P> fetch(Field<Z> field, Collection<? extends Z> values) throws DataAccessException {
        return jooqDAO.fetch(field, values);
    }

    @Override
    public @NotNull <Z> List<P> fetchRange(Field<Z> field, Z lowerInclusive, Z upperInclusive) throws DataAccessException {
        return jooqDAO.fetchRange(field, lowerInclusive, upperInclusive);
    }

    @Override
    public <Z> @Nullable P fetchOne(Field<Z> field, Z value) throws DataAccessException {
        return jooqDAO.fetchOne(field, value);
    }

    @Override
    public @NotNull <Z> Optional<P> fetchOptional(Field<Z> field, Z value) throws DataAccessException {
        return jooqDAO.fetchOptional(field, value);
    }

    @Override
    public @NotNull Table<R> getTable() {
        return jooqDAO.getTable();
    }

    @Override
    public @NotNull Class<P> getType() {
        return jooqDAO.getType();
    }

    @Override
    public T getId(P object) {
        return jooqDAO.getId(object);
    }
}
