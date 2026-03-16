package com.edtronaut.worker.repository;

import com.edtronaut.worker.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
}
