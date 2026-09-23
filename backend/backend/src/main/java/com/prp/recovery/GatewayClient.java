package com.prp.recovery;

import com.prp.domain.Payment;

import java.math.BigDecimal;

/** Abstraction over the payment gateways (Razorpay, PayU, ...). */
public interface GatewayClient {

    record ChargeResult(boolean success, String responseCode) {}

    ChargeResult charge(Payment payment, String gateway);

    void refund(String transactionId, BigDecimal amount);

    /** Backup route used by the SWITCH_GATEWAY action. */
    String backupFor(String gateway);
}
