package com.prp.ingest;

import org.junit.jupiter.api.Test;

import static com.prp.domain.FailureReason.*;
import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {

    private final FailureClassifier classifier = new FailureClassifier();

    @Test
    void usesResponseCodeFirst() {
        assertThat(classifier.classify("51", "anything")).isEqualTo(INSUFFICIENT_FUNDS);
        assertThat(classifier.classify("54", null)).isEqualTo(CARD_EXPIRED);
        assertThat(classifier.classify("timeout", null)).isEqualTo(NETWORK_TIMEOUT);
    }

    @Test
    void fallsBackToMessage() {
        assertThat(classifier.classify("XX", "Card has expired")).isEqualTo(CARD_EXPIRED);
        assertThat(classifier.classify(null, "Wrong CVV")).isEqualTo(INVALID_CVV);
        assertThat(classifier.classify(null, "Transaction declined")).isEqualTo(DO_NOT_HONOR);
    }

    @Test
    void unknownWhenNothingMatches() {
        assertThat(classifier.classify("ZZ", "???")).isEqualTo(UNKNOWN);
    }
}
