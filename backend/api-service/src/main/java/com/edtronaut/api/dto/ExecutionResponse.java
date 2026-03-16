package com.edtronaut.api.dto;

import java.util.UUID;

public record ExecutionResponse(
    UUID executionId,
    String status,
    String stdout,
    String stderr,
    Integer executionTimeMs
) {}
