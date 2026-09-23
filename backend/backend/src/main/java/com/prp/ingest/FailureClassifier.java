package com.prp.ingest;

import com.prp.domain.FailureReason;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

import static com.prp.domain.FailureReason.*;

/**
 * Turns gateway response codes (ISO 8583 style) and messages into a normalised
 * failure reason. Codes win; the message is used when the code is unknown.
 */
@Component
public class FailureClassifier {

    private static final Map<String, FailureReason> CODES = Map.ofEntries(
            Map.entry("51", INSUFFICIENT_FUNDS),
            Map.entry("05", DO_NOT_HONOR),
            Map.entry("54", CARD_EXPIRED),
            Map.entry("82", INVALID_CVV),
            Map.entry("N7", INVALID_CVV),
            Map.entry("61", LIMIT_EXCEEDED),
            Map.entry("65", LIMIT_EXCEEDED),
            Map.entry("91", BANK_DOWNTIME),
            Map.entry("96", BANK_DOWNTIME),
            Map.entry("68", NETWORK_TIMEOUT),
            Map.entry("TIMEOUT", NETWORK_TIMEOUT),
            Map.entry("59", SUSPECTED_FRAUD),
            Map.entry("34", SUSPECTED_FRAUD));

    public FailureReason classify(String responseCode, String message) {
        if (responseCode != null) {
            FailureReason byCode = CODES.get(responseCode.trim().toUpperCase(Locale.ROOT));
            if (byCode != null) return byCode;
        }
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("insufficient") || m.contains("balance")) return INSUFFICIENT_FUNDS;
        if (m.contains("expired")) return CARD_EXPIRED;
        if (m.contains("cvv") || m.contains("security code")) return INVALID_CVV;
        if (m.contains("timeout") || m.contains("timed out")) return NETWORK_TIMEOUT;
        if (m.contains("unavailable") || m.contains("downtime")) return BANK_DOWNTIME;
        if (m.contains("limit")) return LIMIT_EXCEEDED;
        if (m.contains("fraud") || m.contains("risk")) return SUSPECTED_FRAUD;
        if (m.contains("do not honor") || m.contains("declined")) return DO_NOT_HONOR;
        return UNKNOWN;
    }
}
