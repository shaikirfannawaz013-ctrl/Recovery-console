package com.prp.ml;

import com.prp.common.Factor;
import com.prp.config.AppProperties;
import com.prp.domain.FailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls the Python scoring service. If it is slow or down, falls back to a
 * rule-based estimate so payment processing never stops.
 */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private final RestClient rest;

    public MlClient(AppProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.ml().timeout().toMillis());
        factory.setReadTimeout((int) props.ml().timeout().toMillis());
        this.rest = RestClient.builder().baseUrl(props.ml().baseUrl()).requestFactory(factory).build();
    }

    public MlScoreResponse score(MlScoreRequest req) {
        try {
            MlScoreResponse res = rest.post().uri("/score").body(req).retrieve().body(MlScoreResponse.class);
            if (res != null) return res;
        } catch (Exception e) {
            log.warn("ML service unavailable, using fallback scoring: {}", e.getMessage());
        }
        return fallback(req);
    }

    static MlScoreResponse fallback(MlScoreRequest r) {
        double base = switch (FailureReason.valueOf(r.reasonCode())) {
            case NETWORK_TIMEOUT -> 0.88;
            case BANK_DOWNTIME -> 0.82;
            case INSUFFICIENT_FUNDS -> 0.6;
            case LIMIT_EXCEEDED -> 0.55;
            case DO_NOT_HONOR -> 0.4;
            case CARD_EXPIRED -> 0.22;
            case INVALID_CVV -> 0.2;
            case SUSPECTED_FRAUD -> 0.05;
            case UNKNOWN -> 0.35;
        };
        int total = r.customerFailedCount();
        double history = total == 0 ? 0 : ((double) r.customerRecoveredCount() / total - 0.5) * 0.2;
        double riskPenalty = -r.customerRiskScore() / 400.0;
        double p = clamp(base + history + riskPenalty, 0.01, 0.99);

        double anomaly = clamp(0.1 + 0.08 * Math.max(0, r.velocity10m() - 1)
                + (r.newDevice() ? 0.2 : 0) + (r.ipCityMismatch() ? 0.2 : 0), 0, 1);
        int risk = (int) Math.round(clamp(r.customerRiskScore() / 100.0 * 0.6 + anomaly * 0.4, 0, 1) * 100);

        List<Factor> factors = new ArrayList<>(List.of(
                new Factor("Failure reason", round(base - 0.5)),
                new Factor("Customer history", round(history)),
                new Factor("Customer risk score", round(riskPenalty))));
        return new MlScoreResponse(round(p), risk, round(anomaly), factors, List.of(), "heuristic-fallback");
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}
