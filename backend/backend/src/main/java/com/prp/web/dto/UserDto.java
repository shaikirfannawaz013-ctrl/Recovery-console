package com.prp.web.dto;

import com.prp.domain.Role;

public record UserDto(Long id, String username, String fullName, Role role) {}
