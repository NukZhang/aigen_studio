package com.aigen.studio.repository;

import com.aigen.studio.entity.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, Long> {

    Optional<Requirement> findByCode(String code);

    List<Requirement> findByStatus(Requirement.RequirementStatus status);

    boolean existsByCode(String code);
}