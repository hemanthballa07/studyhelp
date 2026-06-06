package com.platform.identity.web;

import com.platform.identity.app.EntitlementService;
import com.platform.identity.domain.Feature;
import com.platform.identity.web.dto.EntitlementResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entitlements")
public class EntitlementController {

    private final EntitlementService entitlements;

    public EntitlementController(EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    /** Allow/deny a feature for the authenticated user (identified by the JWT subject = email). */
    @GetMapping("/check")
    public EntitlementResponse check(@RequestParam("feature") Feature feature, Authentication authentication) {
        boolean allowed = entitlements.check(authentication.getName(), feature);
        return new EntitlementResponse(feature.name(), allowed);
    }
}
