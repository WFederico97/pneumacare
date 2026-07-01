package wfederico.pneumacare.shared.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import wfederico.pneumacare.shared.security.user.UserRepository;

/**
 * Loads users for authentication from the {@code users} table.
 *
 * <p>An unknown username throws {@link UsernameNotFoundException}; callers surface
 * a generic 401 so unknown-user and wrong-password are indistinguishable.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Authentication failed"));
    }
}
