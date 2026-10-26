package com.smartplantcare.web;

import com.smartplantcare.model.PlantData.User;
import com.smartplantcare.security.AccountPrincipal;
import com.smartplantcare.storage.JsonStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JsonStore store;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    public AuthController(JsonStore store, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        User user = new User(UUID.randomUUID().toString(), request.fullName().trim(), email, username,
                passwordEncoder.encode(request.password()), Instant.now());
        try {
            store.update(data -> {
                if (data.users.stream().anyMatch(existing -> existing.email().equalsIgnoreCase(email))) {
                    throw new ConflictException("An account with this email already exists.");
                }
                if (data.users.stream().anyMatch(existing -> existing.username().equalsIgnoreCase(username))) {
                    throw new ConflictException("This username is already taken.");
                }
                data.users.add(user);
                return data;
            }, data -> null);
        } catch (ConflictException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest servletRequest, HttpServletResponse response) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email, username, or password is incorrect.");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.login().trim(), request.password()));
            servletRequest.getSession(true);
            servletRequest.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, servletRequest, response);
            AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
            return new UserResponse(principal.id(), principal.fullName(), principal.email(), principal.username());
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email, username, or password is incorrect.");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setHeader("Clear-Site-Data", "\"cache\"");
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new UserResponse(principal.id(), principal.fullName(), principal.email(), principal.username());
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,24}$",
                    message = "Use 3–24 letters, numbers, or underscores.") String username,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank String confirmPassword) {
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String login, @NotBlank String password) {
    }

    public record UserResponse(String id, String fullName, String email, String username) {
        static UserResponse from(User user) {
            return new UserResponse(user.id(), user.fullName(), user.email(), user.username());
        }
    }

    public record CsrfResponse(String token) {
    }

    private static final class ConflictException extends RuntimeException {
        private ConflictException(String message) {
            super(message);
        }
    }
}
