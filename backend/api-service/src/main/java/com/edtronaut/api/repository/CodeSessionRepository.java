package com.edtronaut.api.repository;

import com.edtronaut.api.model.CodeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CodeSessionRepository extends JpaRepository<CodeSession, UUID> {
}
