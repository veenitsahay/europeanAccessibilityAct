package com.vulmo.a11y.repo;

import com.vulmo.a11y.domain.Scan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScanRepository extends JpaRepository<Scan, UUID> {
}
