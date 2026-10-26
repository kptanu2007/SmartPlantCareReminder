package com.smartplantcare.security;

import com.smartplantcare.storage.JsonStore;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StoreUserDetailsService implements UserDetailsService {
    private final JsonStore store;

    public StoreUserDetailsService(JsonStore store) {
        this.store = store;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        return store.snapshot().users.stream()
                .filter(user -> user.username().equalsIgnoreCase(normalized)
                        || user.email().equalsIgnoreCase(normalized))
                .findFirst()
                .map(AccountPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}
