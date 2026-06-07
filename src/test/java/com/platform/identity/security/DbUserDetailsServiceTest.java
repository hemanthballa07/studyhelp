package com.platform.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.identity.domain.Role;
import com.platform.identity.domain.User;
import com.platform.identity.domain.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The loaded principal carries the platform user id (as {@link AuthenticatedUser}) and the role
 * authority, so the JWT token customizer can mint the {@code userId} claim downstream contexts rely on.
 */
class DbUserDetailsServiceTest {

    @Test
    void loadsUserAsAuthenticatedUserCarryingTheUserId() {
        UserRepository users = mock(UserRepository.class);
        UUID id = UUID.randomUUID();
        when(users.findByEmail("expert@x.com"))
                .thenReturn(Optional.of(new User(id, "expert@x.com", "hash", Role.EXPERT, Instant.now())));
        DbUserDetailsService service = new DbUserDetailsService(users);

        UserDetails details = service.loadUserByUsername("expert@x.com");

        assertThat(details).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) details).getId()).isEqualTo(id);
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_EXPERT");
    }
}
