package com.vulmo.a11y.repo;

import com.vulmo.a11y.domain.Violation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {

    @Query("select v from Violation v join fetch v.page p where p.scan.id = :scanId")
    List<Violation> findAllByScanId(@Param("scanId") UUID scanId);
}
