package com.prp.ml;

import com.prp.common.Factor;

import java.util.List;

public record MlScoreResponse(
        double recoveryProbability,
        int riskScore,
        double anomalyScore,
        List<Factor> topFactors,
        List<String> signals,
        String modelVersion) {}
