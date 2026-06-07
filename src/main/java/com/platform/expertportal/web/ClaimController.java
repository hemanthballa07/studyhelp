package com.platform.expertportal.web;

import com.platform.expertportal.app.ExpertClaimService;
import com.platform.expertportal.domain.ClaimableQueueRepository;
import com.platform.expertportal.web.dto.ClaimRequest;
import com.platform.expertportal.web.dto.ClaimResponse;
import com.platform.expertportal.web.dto.QueueItem;
import com.platform.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Expert-facing claim API. The claiming expert is taken from the authenticated principal. */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ExpertClaimService claims;
    private final ClaimableQueueRepository queue;

    public ClaimController(ExpertClaimService claims, ClaimableQueueRepository queue) {
        this.claims = claims;
        this.queue = queue;
    }

    /** Claim the next claimable question for a subject: 200 with the question, or 204 if none. */
    @PostMapping
    public ResponseEntity<ClaimResponse> claim(
            @Valid @RequestBody ClaimRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID expertId = CurrentUser.id(jwt);
        return claims.claim(expertId, request.subject())
                .map(claimed -> ResponseEntity.ok(ClaimResponse.from(claimed)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** The current claimable queue for a subject (read view), capped to a bounded page size. */
    @GetMapping("/queue")
    public List<QueueItem> queue(@RequestParam("subject") String subject,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return queue.findBySubject(subject, capped).stream().map(QueueItem::from).toList();
    }
}
