package com.prp.service;

import com.prp.common.NotFoundException;
import com.prp.domain.AppUser;
import com.prp.repository.AppUserRepository;
import com.prp.security.JwtService;
import com.prp.security.RefreshTokenStore;
import com.prp.web.dto.AuthResponse;
import com.prp.web.dto.Mappers;
import com.prp.web.dto.UserDto;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenStore refreshTokens;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, RefreshTokenStore refreshTokens) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
    }

    public AuthResponse login(String username, String password) {
        AppUser user = users.findByUsernameIgnoreCase(username.trim())
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Username or password is incorrect."));
        return tokensFor(user);
    }

    public AuthResponse refresh(String refreshToken) {
        long userId = refreshTokens.consume(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Your session has expired. Sign in again."));
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Your session has expired. Sign in again."));
        return tokensFor(user);
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    public UserDto me(String username) {
        return users.findByUsernameIgnoreCase(username).map(Mappers::user)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private AuthResponse tokensFor(AppUser user) {
        return new AuthResponse(jwt.issueAccessToken(user), refreshTokens.issue(user.getId()), Mappers.user(user));
    }
}
