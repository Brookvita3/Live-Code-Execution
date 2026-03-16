package com.edtronaut.api.dto;

import java.util.UUID;

public record SessionResponse(
    UUID sessionId,
    String status
) {}
