package com.edtronaut.api.repository;

import com.edtronaut.api.model.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
}
