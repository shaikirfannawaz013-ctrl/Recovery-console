package com.prp.common;

import java.io.Serializable;

/** One explanation factor from the ML model: how much a feature moved the recovery chance. */
public record Factor(String feature, double impact) implements Serializable {}
