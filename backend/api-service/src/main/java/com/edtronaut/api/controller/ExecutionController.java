package com.edtronaut.api.controller;

import com.edtronaut.api.dto.ExecutionResponse;
import com.edtronaut.api.service.ExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@RestController
@CrossOrigin
@RequestMapping("/executions")
@Tag(name = "Execution Management", description = "Endpoints for retrieving code execution results")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/{executionId}")
    @Operation(summary = "Get execution result", description = "Retrieves the result of a code execution by its ID.")
    public ResponseEntity<ExecutionResponse> getResult(@PathVariable UUID executionId) {
        ExecutionResponse response = executionService.getResult(executionId);
        return ResponseEntity.ok(response);
    }
}
