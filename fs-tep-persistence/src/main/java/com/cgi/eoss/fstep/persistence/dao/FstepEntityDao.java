package com.cgi.eoss.fstep.persistence.dao;

import com.cgi.eoss.fstep.model.FstepEntity;
import com.cgi.eoss.fstep.persistence.SpringJpaRepositoryIgnore;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QueryDslPredicateExecutor;

import java.util.List;

/**
 * <p>A generic DAO interface for FS-TEP entity objects.</p>
 *
 * @param <T> The type of entity managed by this DAO. It is expected that the type has a Long-type primary identifier.
 */
@SpringJpaRepositoryIgnore
public interface FstepEntityDao<T extends FstepEntity<T>> extends JpaRepository<T, Long>, QueryDslPredicateExecutor<T> {

    @Override
    List<T> findAll(Predicate predicate);

    @Override
    List<T> findAll(Predicate predicate, Sort sort);

    @Override
    List<T> findAll(Predicate predicate, OrderSpecifier<?>... orders);

    @Override
    List<T> findAll(OrderSpecifier<?>... orders);

    @Query("select distinct t.id from #{#entityName} t")
    List<Long> findAllIds();

}
