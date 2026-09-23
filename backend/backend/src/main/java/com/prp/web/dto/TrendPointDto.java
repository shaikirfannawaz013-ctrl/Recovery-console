package com.prp.web.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record TrendPointDto(String date, BigDecimal failed, BigDecimal recovered) implements Serializable {}
