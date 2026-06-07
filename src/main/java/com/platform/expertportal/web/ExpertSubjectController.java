package com.platform.expertportal.web;

import com.platform.expertportal.domain.ExpertSubjectRepository;
import com.platform.expertportal.web.dto.SubjectRequest;
import com.platform.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the authenticated expert register a subject they handle (the expert profile/subjects notion). */
@RestController
@RequestMapping("/api/experts")
public class ExpertSubjectController {

    private final ExpertSubjectRepository subjects;

    public ExpertSubjectController(ExpertSubjectRepository subjects) {
        this.subjects = subjects;
    }

    @PostMapping("/subjects")
    public ResponseEntity<Void> register(
            @Valid @RequestBody SubjectRequest request, @AuthenticationPrincipal Jwt jwt) {
        subjects.register(CurrentUser.id(jwt), request.subject());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
