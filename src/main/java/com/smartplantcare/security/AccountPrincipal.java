package com.smartplantcare.security;

import com.smartplantcare.model.PlantData.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AccountPrincipal(String id, String fullName, String email, String username,
                               String password, boolean enabled) implements UserDetails {
    public static AccountPrincipal from(User user) {
        return new AccountPrincipal(user.id(), user.fullName(), user.email(), user.username(),
                user.passwordHash(), true);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
