package com.edtronaut.api.dto;

import java.util.UUID;

public record RunResponse(
    UUID executionId,
    String status
) {}
