package com.platform.identity.web;

import com.platform.identity.domain.UserRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only surface. Access is enforced by the {@code /api/admin/**} hasRole(ADMIN) rule. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;

    public AdminController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/users/count")
    public Map<String, Long> userCount() {
        return Map.of("count", users.count());
    }
}
