package com.edtronaut.api.service;

import com.edtronaut.api.dto.SessionResponse;
import com.edtronaut.api.model.CodeSession;
import com.edtronaut.api.model.Language;
import com.edtronaut.api.model.SessionStatus;
import com.edtronaut.api.repository.CodeSessionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CodeSessionService {

    private final CodeSessionRepository repository;

    public CodeSessionService(CodeSessionRepository repository) {
        this.repository = repository;
    }

    public SessionResponse createSession(String language) {
        CodeSession session = new CodeSession();
        if (language != null) {
            session.setLanguage(Language.valueOf(language.toUpperCase()));
        } else {
            session.setLanguage(Language.PYTHON);
        }
        session.setSourceCode("");
        session.setStatus(SessionStatus.ACTIVE);

        session = repository.save(session);
        return new SessionResponse(session.getId(), session.getStatus().name());
    }

    public void autosave(UUID sessionId, String language, String sourceCode) {
        CodeSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (language != null) {
            session.setLanguage(Language.valueOf(language.toUpperCase()));
        }
        if (sourceCode != null) {
            session.setSourceCode(sourceCode);
        }

        repository.save(session);
    }

    public CodeSession getSession(UUID sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }
}
