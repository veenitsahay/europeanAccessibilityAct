package com.vulmo.a11y.repo;

import com.vulmo.a11y.domain.ScanPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScanPageRepository extends JpaRepository<ScanPage, UUID> {

    List<ScanPage> findByScanId(UUID scanId);
}
