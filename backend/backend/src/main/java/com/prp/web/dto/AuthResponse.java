package com.prp.web.dto;

public record AuthResponse(String accessToken, String refreshToken, UserDto user) {}
