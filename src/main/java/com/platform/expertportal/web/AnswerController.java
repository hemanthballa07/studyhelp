package com.platform.expertportal.web;

import com.platform.expertportal.app.AnswerResult;
import com.platform.expertportal.app.AnswerService;
import com.platform.expertportal.web.dto.AnswerRequest;
import com.platform.expertportal.web.dto.AnswerResponse;
import com.platform.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expert-facing answer submission. The submitting expert is taken from the authenticated principal. */
@RestController
@RequestMapping("/api/answers")
public class AnswerController {

    private final AnswerService answers;

    public AnswerController(AnswerService answers) {
        this.answers = answers;
    }

    /** Submit an answer: 201 when delivered, 202 when persisted but stale (lease expired / not owner). */
    @PostMapping
    public ResponseEntity<AnswerResponse> submit(
            @Valid @RequestBody AnswerRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID expertId = CurrentUser.id(jwt);
        AnswerResult result = answers.submit(expertId, request.questionId(), request.body());
        AnswerResponse body = new AnswerResponse(result.answerId(), request.questionId(), result.stale());
        HttpStatus status = result.stale() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(body);
    }
}
