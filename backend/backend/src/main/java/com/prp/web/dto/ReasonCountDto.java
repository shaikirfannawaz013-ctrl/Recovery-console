package com.prp.web.dto;

import com.prp.domain.FailureCategory;
import com.prp.domain.FailureReason;

import java.io.Serializable;

public record ReasonCountDto(FailureReason reason, FailureCategory category, long count, long recovered)
        implements Serializable {}
