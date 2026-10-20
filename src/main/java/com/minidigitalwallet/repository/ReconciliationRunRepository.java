package com.minidigitalwallet.repository;

import com.minidigitalwallet.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    List<ReconciliationRun> findTop50ByOrderByCreatedAtDesc();
}
