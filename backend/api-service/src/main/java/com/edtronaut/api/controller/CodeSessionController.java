package com.edtronaut.api.controller;

import com.edtronaut.api.dto.AutosaveRequest;
import com.edtronaut.api.dto.RunResponse;
import com.edtronaut.api.dto.CreateSessionRequest;
import com.edtronaut.api.dto.SessionResponse;
import com.edtronaut.api.service.CodeSessionService;
import com.edtronaut.api.service.ExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RestController
@CrossOrigin
@RequestMapping("/code-sessions")
@Tag(name = "Code Session Management", description = "Endpoints for managing code sessions and running code")
public class CodeSessionController {

    private static final Logger log = LoggerFactory.getLogger(CodeSessionController.class);

    private final CodeSessionService codeSessionService;
    private final ExecutionService executionService;

    public CodeSessionController(CodeSessionService codeSessionService,
                                  ExecutionService executionService) {
        this.codeSessionService = codeSessionService;
        this.executionService = executionService;
    }

    @PostMapping
    @Operation(summary = "Create a new code session", description = "Initializes a new code session with an optional programming language.")
    public ResponseEntity<SessionResponse> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        String language = (request != null) ? request.language() : null;
        log.info("Request to create a new code session with language: {}", language != null ? language : "DEFAULT");
        SessionResponse response = codeSessionService.createSession(language);
        log.info("Session created successfully: {}", response.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{sessionId}")
    @Operation(summary = "Autosave code", description = "Saves the current state of the code in the session.")
    public ResponseEntity<Void> autosave(@PathVariable UUID sessionId,
                                          @RequestBody AutosaveRequest request) {
        log.debug("Autosave request for session: {}", sessionId);
        codeSessionService.autosave(sessionId, request.language(), request.sourceCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sessionId}/run")
    @Operation(summary = "Run code", description = "Submits the code in the session for execution.")
    public ResponseEntity<RunResponse> runCode(@PathVariable UUID sessionId) {
        log.info("Request to run code for session: {}", sessionId);
        RunResponse response = executionService.runCode(sessionId);
        log.info("Code execution queued for session: {}, executionId: {}", sessionId, response.executionId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
