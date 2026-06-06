package com.platform.identity.web;

import com.platform.identity.app.RegistrationService;
import com.platform.identity.domain.User;
import com.platform.identity.web.dto.RegisterRequest;
import com.platform.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
public class RegistrationController {

    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registration.register(request.email(), request.password(), request.role());
        return new UserResponse(user.getId().toString(), user.getEmail(), user.getRole().name());
    }
}
