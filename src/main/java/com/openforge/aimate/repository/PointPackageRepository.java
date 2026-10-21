package com.openforge.aimate.repository;

import com.openforge.aimate.domain.PointPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointPackageRepository extends JpaRepository<PointPackage, Long> {

    List<PointPackage> findByEnabledTrueOrderBySortOrderAsc();

    List<PointPackage> findAllByOrderBySortOrderAsc();
}
