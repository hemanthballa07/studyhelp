package com.platform.lifecycle.web;

import com.platform.lifecycle.app.PostQuestionCommand;
import com.platform.lifecycle.app.QuestionPostingService;
import com.platform.lifecycle.web.dto.PostQuestionRequest;
import com.platform.lifecycle.web.dto.PostQuestionResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Student-facing entry point for posting a question. */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionPostingService posting;

    public QuestionController(QuestionPostingService posting) {
        this.posting = posting;
    }

    @PostMapping
    public ResponseEntity<PostQuestionResponse> post(@Valid @RequestBody PostQuestionRequest request) {
        UUID id = posting.post(new PostQuestionCommand(
                request.studentId(), request.subject(), request.title(), request.body(), request.deadlineAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new PostQuestionResponse(id));
    }
}
