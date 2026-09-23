package com.prp.web.dto;

import jakarta.validation.constraints.NotNull;

public record ActionRequest(@NotNull PaymentAction action) {}
